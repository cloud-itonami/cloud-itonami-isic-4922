(ns intercitycoachops.store
  "SSoT for the ISIC-4922 'Other passenger land transport' (long-distance/
  intercity coach, chartered bus, and similar passenger services --
  distinct from ISIC 4921's urban and suburban passenger land transport)
  operations-COORDINATION actor, behind a `Store` protocol so the backend
  is a swap, not a rewrite -- the same seam every `cloud-itonami-isic-*`
  actor in this fleet uses.

  This actor coordinates the SCHEDULING/DISPATCH LOGISTICS of an
  intercity/chartered coach operator: trip/ridership/incident-report data
  logging, vehicle/route/timetable dispatch scheduling, fleet maintenance
  procurement coordination, and vehicle-defect/driver-fitness/route-hazard
  safety-concern flagging. It never directly operates a vehicle, never
  overrides safety judgment, and never finalizes a dispatch-safety-
  clearance or a driver-fitness-to-drive determination -- see
  `intercitycoachops.governor`'s `scope-exclusion-violations`, a HARD,
  permanent, un-overridable block.

  `MemStore` -- atom of EDN. The deterministic default for dev/tests/demo
  (no deps). A `vehicles` directory keyed by `:vehicle-id` STRING
  (bundling the vehicle, its assigned route and its operator-license
  record together -- 'vehicle/route/operator-license' must be
  independently verified/registered before any action, per this
  vertical's own hard invariant) and a `providers` directory keyed by
  `:provider-id` STRING for fleet-maintenance vendors (never keywords --
  consistent keying from the start, avoiding the silent-miss bug that has
  plagued earlier sibling actors).

  A registered/verified vehicle record (vehicle + assigned route +
  operator-license, all bundled into one record for this vertical) must
  exist before ANY proposal targeting that vehicle may ever commit or
  escalate -- `intercitycoachops.governor`'s `vehicle-unverified-
  violations` re-derives this from the vehicle's own `:registered?`/
  `:verified?` fields, never from proposal self-report. A
  `:coordinate-maintenance-order` proposal additionally names a
  registered maintenance provider via its own `:provider-id`; the SAME
  'ground truth, not self-report' discipline applies via
  `provider-unverified-violations`.

  The ledger stays append-only: which vehicle a proposal targeted, which
  operation, on what basis, committed/held/escalated and approved by whom
  is always a query over an immutable log.")

(defprotocol Store
  (vehicle-record [s vehicle-id] "Registered vehicle/route/operator-license
    record, or nil. Vehicle map: {:vehicle-id .. :name .. :route ..
    :operator-license .. :registered? bool :verified? bool}.")
  (all-vehicle-records [s])
  (provider-record [s provider-id] "Registered maintenance-provider record,
    or nil. Provider map: {:provider-id .. :name .. :registered? bool
    :verified? bool}.")
  (all-provider-records [s])
  (ledger [s] "the append-only immutable decision-fact log")
  (coordination-log [s] "the append-only committed coordination-proposal history")
  (commit-record! [s record] "apply a committed proposal's record to the SSoT")
  (append-ledger! [s fact] "append one immutable decision fact")
  (with-vehicle-records [s vehicles] "replace/seed the vehicle directory (map vehicle-id->vehicle)")
  (with-provider-records [s providers] "replace/seed the provider directory (map provider-id->provider)"))

;; ----------------------------- demo data -----------------------------

(defn demo-data
  "A small, self-contained vehicle/provider directory covering both the
  happy path and the governor's own hard checks, so the actor + tests run
  offline."
  []
  {:vehicles
   {"coach-1" {:vehicle-id "coach-1" :name "Highline Express Coach 12"
               :route "City A <-> City B intercity express"
               :operator-license "OL-2201"
               :registered? true :verified? true}
    "coach-2" {:vehicle-id "coach-2" :name "Summit Charter Coach 7"
               :route "Charter / on-demand"
               :operator-license "OL-2340"
               :registered? true :verified? true}
    "coach-3" {:vehicle-id "coach-3" :name "Northline Coach 3 (pending route certification)"
               :route "City C <-> City D"
               :operator-license "OL-2399"
               :registered? true :verified? false}}
   :providers
   {"provider-1" {:provider-id "provider-1" :name "Trailhead Coach Maintenance Depot"
                  :registered? true :verified? true}
    "provider-2" {:provider-id "provider-2" :name "Unverified Roadside Garage Co."
                  :registered? true :verified? false}}})

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [a]
  Store
  (vehicle-record [_ vehicle-id] (get-in @a [:vehicles vehicle-id]))
  (all-vehicle-records [_] (sort-by :vehicle-id (vals (:vehicles @a))))
  (provider-record [_ provider-id] (get-in @a [:providers provider-id]))
  (all-provider-records [_] (sort-by :provider-id (vals (:providers @a))))
  (ledger [_] (:ledger @a))
  (coordination-log [_] (:coordination-log @a))
  (commit-record! [_ record]
    (swap! a update :coordination-log conj record)
    record)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-vehicle-records [s vehicles] (when (seq vehicles) (swap! a assoc :vehicles vehicles)) s)
  (with-provider-records [s providers] (when (seq providers) (swap! a assoc :providers providers)) s))

(defn seed-db
  "A MemStore seeded with the demo vehicle/provider directory. The
  deterministic default."
  []
  (->MemStore (atom (assoc (demo-data) :ledger [] :coordination-log []))))

(defn mem-store
  "A MemStore seeded with explicit `vehicles`/`providers` maps
  (vehicle-id/provider-id string -> record map) -- the primary test/dev
  entry point. Either may be empty (an unregistered-everywhere fleet)."
  ([vehicles] (mem-store vehicles {}))
  ([vehicles providers]
   (->MemStore (atom {:vehicles (or vehicles {}) :providers (or providers {})
                       :ledger [] :coordination-log []}))))
