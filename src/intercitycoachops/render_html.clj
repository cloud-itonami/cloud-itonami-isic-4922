(ns intercitycoachops.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2 (com-junkawasaki/root ADR-2607189300,
  Wave5 rollout, generator template ledger seq 6): this repo previously
  had NO demo page and no generator at all. This namespace drives the
  REAL actor stack (`intercitycoachops.operation` ->
  `intercitycoachops.governor` -> `intercitycoachops.store`) through a
  scenario adapted from this repo's own `intercitycoachops.sim` demo
  driver (`clojure -M:run`, confirmed to run correctly against the real
  seeded vehicle/provider directory before this file was written),
  trimmed to a representative subset at phase 3 (supervised-auto) and
  rendered deterministically -- no invented numbers, no timestamps in
  the page content, byte-identical across reruns against the same seed
  (verified by diffing two consecutive runs).

  Usage: `clojure -M:dev:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [clojure.string :as str]
            [intercitycoachops.store :as store]
            [intercitycoachops.operation :as op]
            [langgraph.graph :as g]))

(def ^:private coordinator
  {:actor-id "coord-1" :actor-role :dispatch-coordinator :phase 3})

(defn- exec! [actor tid request]
  (g/run* actor {:request request :context coordinator} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "coord-1"}}
          {:thread-id tid :resume? true}))

(defn run-demo!
  "Runs a fresh seeded store through a scenario mixing every disposition
  this actor can reach: coach-1 clears a service-record log (auto-
  commit, clean), a dispatch-scheduling (auto-commit, clean), and a
  low-cost maintenance-order naming a verified provider (auto-commit,
  clean); coach-1's high-cost maintenance order and its safety-concern
  flag both ALWAYS escalate to a human -- approved in both cases, so
  coach-1's own last recorded status is a clean approval, not a hold.
  coach-99 (does not exist) HARD-holds on `:vehicle-unverified`;
  coach-3 (registered but its route certification is not yet verified)
  HARD-holds on the same rule, showing the distinct 'unregistered' vs
  'registered-but-unverified' ground states; a maintenance order
  against coach-2 naming provider-2 (registered but unverified as a
  maintenance counterparty) HARD-holds on `:provider-unverified`. Every
  HARD hold never reaches a human. Returns the resulting store -- every
  field read by `render` below is real governor/store output, not a
  hand-typed copy."
  []
  (let [db (store/seed-db)
        actor (op/build db)]
    (exec! actor "t1" {:op :log-service-record :vehicle-id "coach-1"
                       :patch {:trip-id "trip-502" :ridership 41 :incidents 0}})

    (exec! actor "t2" {:op :schedule-dispatch-operation :vehicle-id "coach-1"
                       :patch {:route "City A <-> City B" :date "2026-07-20"
                               :timetable "08:00 dep / 12:30 arr"}})

    (exec! actor "t3" {:op :coordinate-maintenance-order :vehicle-id "coach-1"
                       :patch {:item "scheduled brake inspection" :estimated-cost 380.0
                               :provider-id "provider-1"}})

    (exec! actor "t4" {:op :coordinate-maintenance-order :vehicle-id "coach-1"
                       :patch {:item "engine overhaul" :estimated-cost 4200.0
                               :provider-id "provider-1"}})
    (approve! actor "t4")

    (exec! actor "t5" {:op :flag-safety-concern :vehicle-id "coach-1"
                       :patch {:concern "intermittent brake warning light observed on pre-trip inspection"
                               :confidence 0.92}})
    (approve! actor "t5")

    (exec! actor "t6" {:op :log-service-record :vehicle-id "coach-99"
                       :patch {:trip-id "trip-999"}})

    (exec! actor "t7" {:op :log-service-record :vehicle-id "coach-3"
                       :patch {:trip-id "trip-998"}})

    (exec! actor "t8" {:op :coordinate-maintenance-order :vehicle-id "coach-2"
                       :patch {:item "tire replacement" :estimated-cost 300.0
                               :provider-id "provider-2"}})
    db))

;; ----------------------------- rendering -----------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

(defn- last-fact-for [ledger vehicle-id]
  (last (filter #(= (:vehicle-id %) vehicle-id) ledger)))

(defn- status-cell [ledger vehicle-id]
  (let [f (last-fact-for ledger vehicle-id)]
    (cond
      (nil? f) "<span class=\"muted\">no activity</span>"
      (= :committed (:t f)) "<span class=\"ok\">committed</span>"
      (= :approval-granted (:t f)) "<span class=\"ok\">approved &amp; committed</span>"
      (= :governor-hold (:t f))
      (let [rule (-> f :violations first :rule)]
        (str "<span class=\"critical\">HARD hold &middot; " (esc (name (or rule :unknown))) "</span>"))
      (= :approval-requested (:t f)) "<span class=\"warn\">awaiting approval</span>"
      :else "<span class=\"muted\">in progress</span>")))

(defn- bool-cell [v] (if v "<span class=\"ok\">yes</span>" "<span class=\"err\">no</span>"))

(defn- vehicle-row [ledger {:keys [vehicle-id name route operator-license registered? verified?]}]
  (format "        <tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>"
          (esc vehicle-id) (esc name) (esc route) (esc operator-license)
          (bool-cell registered?) (bool-cell verified?) (status-cell ledger vehicle-id)))

(defn- provider-row [{:keys [provider-id name registered? verified?]}]
  (format "        <tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>"
          (esc provider-id) (esc name) (bool-cell registered?) (bool-cell verified?)))

(defn- ledger-row [{:keys [t op vehicle-id basis]}]
  (format "        <tr><td>%s</td><td><code>%s</code></td><td>%s</td><td>%s</td></tr>"
          (esc (name t)) (esc (name (or op :n-a))) (esc (or vehicle-id ""))
          (esc (or (some->> basis (map name) (str/join ", ")) ""))))

(def ^:private action-gate-rows
  ;; Static description of this actor's own op contract (README `Ops`
  ;; table, `intercitycoachops.governor`/`intercitycoachops.phase`) --
  ;; documentation of fixed behavior, not runtime telemetry, so it is
  ;; legitimately hand-described rather than derived from a live run.
  ["        <tr><td><code>:log-service-record</code></td><td><span class=\"ok\">auto-commit when clean, phase 3</span></td></tr>"
   "        <tr><td><code>:schedule-dispatch-operation</code></td><td><span class=\"ok\">auto-commit when clean &middot; vehicle/route/operator-license independently verified</span></td></tr>"
   "        <tr><td><code>:coordinate-maintenance-order</code></td><td><span class=\"warn\">auto-commit below cost threshold &middot; provider independently verified &middot; ALWAYS human approval above cost threshold</span></td></tr>"
   "        <tr><td><code>:flag-safety-concern</code></td><td><span class=\"warn\">ALWAYS human approval, any phase</span></td></tr>"])

(defn render
  "Renders the full operator-console.html document from a store `db`
  that has already run `run-demo!` (or any other real scenario)."
  [db]
  (let [ledger (vec (store/ledger db))
        vehicles (store/all-vehicle-records db)
        providers (store/all-provider-records db)
        vehicle-rows (str/join "\n" (map (partial vehicle-row ledger) vehicles))
        provider-rows (str/join "\n" (map provider-row providers))
        ledger-rows (str/join "\n" (map ledger-row ledger))]
    (str
     "<html><head><meta charset=\"utf-8\"><title>cloud-itonami-isic-4922 &middot; intercity coach dispatch</title><style>\n"
     "table { width: 100%; border-collapse: collapse; font-size: 14px; }\n"
     ".ok { color: #137a3f; }\n"
     "body { font-family: system-ui,-apple-system,sans-serif; margin: 0; color: #1a1a1a; background: #fafafa; }\n"
     "header.bar { display: flex; align-items: center; gap: 12px; padding: 12px 20px; background: #fff; border-bottom: 1px solid #e5e5e5; }\n"
     "th, td { text-align: left; padding: 8px 10px; border-bottom: 1px solid #f0f0f0; }\n"
     "h2 { margin-top: 0; font-size: 15px; }\n"
     ".warn { color: #b25c00; background: #fff8e1; padding: 2px 6px; border-radius: 4px; }\n"
     "main { max-width: 1040px; margin: 24px auto; padding: 0 20px; }\n"
     "header.bar h1 { font-size: 18px; margin: 0; font-weight: 600; }\n"
     ".muted { color: #888; font-size: 13px; }\n"
     ".critical { color: #fff; background: #b3261e; padding: 2px 6px; border-radius: 4px; font-weight: 600; }\n"
     ".card { background: #fff; border: 1px solid #e5e5e5; border-radius: 8px; padding: 16px; margin-bottom: 16px; }\n"
     ".err { color: #b3261e; background: #fbe9e7; padding: 2px 6px; border-radius: 4px; }\n"
     "th { font-weight: 600; color: #555; font-size: 12px; text-transform: uppercase; letter-spacing: 0.04em; }\n"
     "header.bar .badge { margin-left: auto; font-size: 12px; color: #666; }\n"
     "code { font-size: 12px; background: #f4f4f4; padding: 1px 4px; border-radius: 3px; }\n"
     "</style></head><body>\n"
     "<header class=\"bar\">\n"
     "  <h1>Intercity coach dispatch (ISIC 4922) — Operator Console</h1>\n"
     "  <span class=\"badge\">read-only sample · governor-gated · dispatch-safety-clearance / driver-fitness finalization always excluded</span>\n"
     "</header>\n"
     "<main>\n"
     "  <section class=\"card\">\n"
     "    <h2>Vehicles (coaches)</h2>\n"
     "    <p class=\"muted\">Demo snapshot — build-time-generated from <code>intercitycoachops.store</code> via <code>intercitycoachops.render-html</code> (<code>clojure -M:dev:render-html</code>), regenerated nightly.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Vehicle</th><th>Name</th><th>Route</th><th>Operator license</th><th>Registered</th><th>Verified</th><th>Last op status</th></tr></thead>\n"
     "      <tbody>\n"
     vehicle-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Maintenance providers</h2>\n"
     "    <table>\n"
     "      <thead><tr><th>Provider</th><th>Name</th><th>Registered</th><th>Verified</th></tr></thead>\n"
     "      <tbody>\n"
     provider-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Action gate (Intercity Coach Governor)</h2>\n"
     "    <p class=\"muted\">HARD holds cannot be overridden. Vehicle/route/operator-license and maintenance-provider status is independently re-derived from the store, never trusted from a proposal. Directly finalizing a dispatch-safety-clearance or driver-fitness-to-drive determination is permanently out of scope.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Op</th><th>Gate</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" action-gate-rows) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Audit ledger (this run)</h2>\n"
     "    <p class=\"muted\">Append-only decision-fact log — every proposal, hold and commit this scenario produced.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Fact</th><th>Op</th><th>Vehicle</th><th>Basis</th></tr></thead>\n"
     "      <tbody>\n"
     ledger-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "</main>\n"
     "</body></html>\n")))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        db (run-demo!)
        html (render db)]
    (spit out html)
    (println "wrote" out "(" (count (store/ledger db)) "ledger facts )")))
