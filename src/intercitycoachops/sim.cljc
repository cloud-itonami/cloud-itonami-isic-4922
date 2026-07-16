(ns intercitycoachops.sim
  "Demo driver -- `clojure -M:run`. Walks a clean trip/ridership log
  request through intake -> advise -> govern -> decide -> approval ->
  commit at phase 1 (assisted-logging, always approval), then re-runs the
  same op at phase 3 (supervised-auto, clean + high confidence ->
  auto-commit), then a dispatch-scheduling request and a low-cost
  maintenance-order coordination naming a verified provider (both
  auto-commit clean at phase 3), then a high-cost maintenance order
  (ALWAYS escalates regardless of phase), then a safety-concern flag
  (ALWAYS escalates, at any phase -- approve, then commit), then
  HARD-hold scenarios: an unregistered vehicle, a vehicle registered but
  not yet verified, a maintenance order naming an unverified provider, a
  proposal whose own `:effect` is not `:propose`, and a proposal that has
  drifted into the permanently-excluded dispatch-safety-clearance/
  driver-fitness-to-drive-determination scope."
  (:require [langgraph.graph :as g]
            [intercitycoachops.advisor :as advisor]
            [intercitycoachops.store :as store]
            [intercitycoachops.operation :as op]))

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "dispatch-coordinator-1"}} {:thread-id tid :resume? true}))

(defn -main [& _]
  (let [db (store/seed-db)
        coordinator-phase-1 {:actor-id "coord-1" :actor-role :dispatch-coordinator :phase 1}
        coordinator-phase-3 {:actor-id "coord-1" :actor-role :dispatch-coordinator :phase 3}
        actor (op/build db)]

    (println "== log-service-record coach-1 (phase 1, escalates -- human approves) ==")
    (let [r (exec-op actor "t1" {:op :log-service-record :vehicle-id "coach-1"
                                  :patch {:trip-id "trip-501" :ridership 38 :incidents 0}} coordinator-phase-1)]
      (println r)
      (println "-- human dispatch coordinator approves --")
      (println (approve! actor "t1")))

    (println "\n== log-service-record coach-1 (phase 3, clean -- auto-commits) ==")
    (println (exec-op actor "t2" {:op :log-service-record :vehicle-id "coach-1"
                                  :patch {:trip-id "trip-502" :ridership 41 :incidents 0}} coordinator-phase-3))

    (println "\n== schedule-dispatch-operation coach-1 (phase 3, clean -- auto-commits) ==")
    (println (exec-op actor "t3" {:op :schedule-dispatch-operation :vehicle-id "coach-1"
                                  :patch {:route "City A <-> City B" :date "2026-07-20" :timetable "08:00 dep / 12:30 arr"}} coordinator-phase-3))

    (println "\n== coordinate-maintenance-order coach-1, low cost, verified provider (phase 3, clean -- auto-commits) ==")
    (println (exec-op actor "t4" {:op :coordinate-maintenance-order :vehicle-id "coach-1"
                                  :patch {:item "scheduled brake inspection" :estimated-cost 380.0
                                          :provider-id "provider-1"}} coordinator-phase-3))

    (println "\n== coordinate-maintenance-order coach-1, HIGH cost (ALWAYS escalates, even at phase 3) ==")
    (let [r (exec-op actor "t5" {:op :coordinate-maintenance-order :vehicle-id "coach-1"
                                 :patch {:item "engine overhaul" :estimated-cost 4200.0
                                         :provider-id "provider-1"}} coordinator-phase-3)]
      (println r)
      (println "-- human dispatch coordinator reviews & approves --")
      (println (approve! actor "t5")))

    (println "\n== flag-safety-concern coach-1 (ALWAYS escalates, even at phase 3) ==")
    (let [r (exec-op actor "t6" {:op :flag-safety-concern :vehicle-id "coach-1"
                                 :patch {:concern "intermittent brake warning light observed on pre-trip inspection" :confidence 0.92}} coordinator-phase-3)]
      (println r)
      (println "-- human dispatch coordinator reviews & escalates to safety authority --")
      (println (approve! actor "t6")))

    (println "\n== log-service-record coach-99 (unregistered vehicle -> HARD hold) ==")
    (println (exec-op actor "t7" {:op :log-service-record :vehicle-id "coach-99"
                                  :patch {:trip-id "trip-999"}} coordinator-phase-3))

    (println "\n== log-service-record coach-3 (registered but unverified -> HARD hold) ==")
    (println (exec-op actor "t8" {:op :log-service-record :vehicle-id "coach-3"
                                  :patch {:trip-id "trip-998"}} coordinator-phase-3))

    (println "\n== coordinate-maintenance-order coach-1, provider-2 unverified (-> HARD hold) ==")
    (println (exec-op actor "t9" {:op :coordinate-maintenance-order :vehicle-id "coach-1"
                                  :patch {:item "tire replacement" :estimated-cost 300.0
                                          :provider-id "provider-2"}} coordinator-phase-3))

    (println "\n== schedule-dispatch-operation coach-1, advisor attempts direct actuation (:effect :commit) -> HARD hold ==")
    (let [actor-direct (op/build db {:advisor (reify advisor/Advisor
                                                (-advise [_ _ req]
                                                  (assoc (advisor/infer nil req) :effect :commit)))})]
      (println (exec-op actor-direct "t10" {:op :schedule-dispatch-operation :vehicle-id "coach-1"
                                           :patch {:route "City C <-> City D" :date "2026-07-22"}} coordinator-phase-3)))

    (println "\n== log-service-record coach-1, advisor drifts into dispatch-safety-clearance/driver-fitness scope -> HARD hold, permanent ==")
    (println (exec-op actor "t11" {:op :log-service-record :vehicle-id "coach-1"
                                   :out-of-scope? true
                                   :patch {}} coordinator-phase-3))

    (println "\n== audit ledger ==")
    (doseq [f (store/ledger db)] (println f))

    (println "\n== committed coordination log ==")
    (doseq [r (store/coordination-log db)] (println r))))
