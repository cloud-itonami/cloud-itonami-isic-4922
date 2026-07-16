(ns intercitycoachops.governor-test
  "Pure unit tests of `intercitycoachops.governor/check` against hand-built
  proposals -- the fast, focused complement to `governor-contract-test`'s
  full-graph integration coverage."
  (:require [clojure.test :refer [deftest is testing]]
            [intercitycoachops.advisor :as adv]
            [intercitycoachops.governor :as gov]
            [intercitycoachops.store :as store]))

(def coach-1 {:vehicle-id "coach-1" :name "Highline Express Coach 12" :registered? true :verified? true})
(def coach-3 {:vehicle-id "coach-3" :name "Northline Coach 3" :registered? true :verified? false})
(def provider-1 {:provider-id "provider-1" :name "Trailhead Coach Maintenance Depot" :registered? true :verified? true})
(def provider-2 {:provider-id "provider-2" :name "Unverified Roadside Garage Co." :registered? true :verified? false})

(defn- clean-proposal [op vehicle-id]
  {:op op :vehicle-id vehicle-id :summary "s" :rationale "routine intercity coach dispatch coordination"
   :cites [vehicle-id] :effect :propose :value {} :confidence 0.85})

(defn- clean-maintenance-order [vehicle-id provider-id cost]
  (assoc (clean-proposal :coordinate-maintenance-order vehicle-id)
         :value {:vehicle-id vehicle-id :provider-id provider-id :estimated-cost cost}))

(deftest vehicle-unregistered-is-hard
  (testing "no vehicle record at all -> HARD hold"
    (let [s (store/mem-store {"coach-1" coach-1})
          verdict (gov/check {} nil (clean-proposal :log-service-record "unknown-coach") s)]
      (is (true? (:hard? verdict)))
      (is (some #{:vehicle-unverified} (map :rule (:violations verdict)))))))

(deftest vehicle-unverified-is-hard
  (testing "vehicle registered but not yet verified -> HARD hold"
    (let [s (store/mem-store {"coach-3" coach-3})
          verdict (gov/check {} nil (clean-proposal :log-service-record "coach-3") s)]
      (is (true? (:hard? verdict)))
      (is (some #{:vehicle-unverified} (map :rule (:violations verdict)))))))

(deftest provider-missing-on-maintenance-order-is-hard
  (testing "maintenance-order proposal with no :provider-id at all -> HARD hold"
    (let [s (store/mem-store {"coach-1" coach-1} {"provider-1" provider-1})
          verdict (gov/check {} nil (clean-maintenance-order "coach-1" nil 100.0) s)]
      (is (true? (:hard? verdict)))
      (is (some #{:provider-unverified} (map :rule (:violations verdict)))))))

(deftest provider-unregistered-on-maintenance-order-is-hard
  (testing "maintenance-order proposal naming an unknown provider -> HARD hold"
    (let [s (store/mem-store {"coach-1" coach-1} {"provider-1" provider-1})
          verdict (gov/check {} nil (clean-maintenance-order "coach-1" "unknown-provider" 100.0) s)]
      (is (true? (:hard? verdict)))
      (is (some #{:provider-unverified} (map :rule (:violations verdict)))))))

(deftest provider-unverified-on-maintenance-order-is-hard
  (testing "maintenance-order proposal naming a registered-but-unverified provider -> HARD hold"
    (let [s (store/mem-store {"coach-1" coach-1} {"provider-1" provider-1 "provider-2" provider-2})
          verdict (gov/check {} nil (clean-maintenance-order "coach-1" "provider-2" 100.0) s)]
      (is (true? (:hard? verdict)))
      (is (some #{:provider-unverified} (map :rule (:violations verdict)))))))

(deftest provider-verified-on-maintenance-order-is-not-hard-on-provider-check
  (testing "maintenance-order proposal naming a verified provider never trips :provider-unverified"
    (let [s (store/mem-store {"coach-1" coach-1} {"provider-1" provider-1})
          verdict (gov/check {} nil (clean-maintenance-order "coach-1" "provider-1" 100.0) s)]
      (is (empty? (filter #(= :provider-unverified (:rule %)) (:violations verdict)))))))

(deftest provider-check-is-scoped-to-maintenance-order-only
  (testing "non-maintenance-order ops never trip :provider-unverified, even with no providers registered at all"
    (let [s (store/mem-store {"coach-1" coach-1})]
      (doseq [op [:log-service-record :schedule-dispatch-operation :flag-safety-concern]]
        (let [verdict (gov/check {} nil (clean-proposal op "coach-1") s)]
          (is (empty? (filter #(= :provider-unverified (:rule %)) (:violations verdict)))
              (str "op " op " must never trip :provider-unverified")))))))

(deftest effect-not-propose-is-hard
  (testing "any :effect other than :propose is a HARD, un-overridable block"
    (let [s (store/mem-store {"coach-1" coach-1})
          verdict (gov/check {} nil (assoc (clean-proposal :schedule-dispatch-operation "coach-1") :effect :commit) s)]
      (is (true? (:hard? verdict)))
      (is (some #{:effect-not-propose} (map :rule (:violations verdict)))))))

(deftest op-outside-allowlist-is-hard
  (testing "an op outside the closed four-op allowlist is a scope violation"
    (let [s (store/mem-store {"coach-1" coach-1})
          verdict (gov/check {} nil (clean-proposal :finalize-dispatch-safety-clearance "coach-1") s)]
      (is (true? (:hard? verdict)))
      (is (some #{:op-not-allowed} (map :rule (:violations verdict)))))))

(deftest dispatch-clearance-finalization-content-is-hard-and-permanent
  (testing "a proposal whose rationale touches directly clearing a vehicle to dispatch despite a defect is HARD-blocked regardless of op/confidence"
    (let [s (store/mem-store {"coach-1" coach-1})
          poisoned (assoc (clean-proposal :log-service-record "coach-1")
                          :rationale "cleared the vehicle to dispatch despite the defect noted on pre-trip inspection"
                          :confidence 0.99)
          verdict (gov/check {} nil poisoned s)]
      (is (true? (:hard? verdict)))
      (is (some #{:scope-excluded} (map :rule (:violations verdict)))))))

(deftest driver-fitness-determination-content-is-hard
  (testing "a proposal touching certifying the driver as fit to drive is HARD-blocked, same as dispatch clearance"
    (let [s (store/mem-store {"coach-1" coach-1})
          poisoned (assoc (clean-proposal :log-service-record "coach-1")
                          :rationale "certified the driver as fit to drive despite the reported fatigue concern"
                          :confidence 0.90)
          verdict (gov/check {} nil poisoned s)]
      (is (true? (:hard? verdict)))
      (is (some #{:scope-excluded} (map :rule (:violations verdict)))))))

(deftest safety-hold-override-content-is-hard
  (testing "a proposal touching overriding the safety hold is HARD-blocked"
    (let [s (store/mem-store {"coach-1" coach-1})
          poisoned (assoc (clean-proposal :schedule-dispatch-operation "coach-1")
                          :summary "dispatch requested to override the safety hold and depart on schedule")
          verdict (gov/check {} nil poisoned s)]
      (is (true? (:hard? verdict)))
      (is (some #{:scope-excluded} (map :rule (:violations verdict)))))))

(deftest pretrip-inspection-waiver-content-is-hard
  (testing "a proposal touching waiving the pre-trip inspection is HARD-blocked"
    (let [s (store/mem-store {"coach-1" coach-1} {"provider-1" provider-1})
          poisoned (assoc (clean-maintenance-order "coach-1" "provider-1" 100.0)
                          :summary "waived the pre-trip inspection to keep the coach on schedule")
          verdict (gov/check {} nil poisoned s)]
      (is (true? (:hard? verdict)))
      (is (some #{:scope-excluded} (map :rule (:violations verdict)))))))

(deftest legitimate-safety-concern-is-not-scope-excluded
  (testing "flagging observed vehicle-defect/driver-fitness/route-hazard concerns as a SAFETY CONCERN (not a finalization/determination) never trips scope-exclusion -- this actor's core valid use case must not be self-blocked"
    (let [s (store/mem-store {"coach-1" coach-1})
          concern (assoc (clean-proposal :flag-safety-concern "coach-1")
                         :value {:concern "intermittent brake warning light and reported driver fatigue on the overnight route"})
          verdict (gov/check {} nil concern s)]
      (is (empty? (filter #(= :scope-excluded (:rule %)) (:violations verdict)))
          "raw observation content (vehicle-defect/driver-fitness/route-hazard) is exactly what this op exists to surface"))))

(deftest safety-concern-always-escalates-clean
  (testing ":flag-safety-concern is always high-stakes/escalate, even when otherwise clean and high confidence"
    (let [s (store/mem-store {"coach-1" coach-1})
          verdict (gov/check {} nil (assoc (clean-proposal :flag-safety-concern "coach-1") :confidence 0.99) s)]
      (is (false? (:hard? verdict)))
      (is (true? (:high-stakes? verdict)))
      (is (true? (:escalate? verdict))))))

(deftest high-cost-maintenance-order-always-escalates
  (testing "a :coordinate-maintenance-order above the cost threshold is high-stakes/escalate, even when otherwise clean and high confidence"
    (let [s (store/mem-store {"coach-1" coach-1} {"provider-1" provider-1})
          expensive (assoc (clean-maintenance-order "coach-1" "provider-1" 5000.0) :confidence 0.97)
          verdict (gov/check {} nil expensive s)]
      (is (false? (:hard? verdict)))
      (is (true? (:high-stakes? verdict)))
      (is (true? (:escalate? verdict))))))

(deftest low-cost-maintenance-order-does-not-force-escalate
  (testing "a :coordinate-maintenance-order at or below the cost threshold does not trip the high-cost escalate gate"
    (let [s (store/mem-store {"coach-1" coach-1} {"provider-1" provider-1})
          cheap (assoc (clean-maintenance-order "coach-1" "provider-1" 380.0) :confidence 0.9)
          verdict (gov/check {} nil cheap s)]
      (is (false? (:hard? verdict)))
      (is (false? (:high-stakes? verdict)))
      (is (false? (:escalate? verdict))))))

;; ----------------------------- self-trip regression -----------------------------
;;
;; A known bug class in this actor fleet: the governor's own
;; scope-exclusion term list is sometimes phrased as a bare noun (e.g.
;; "safety" or "dispatch"), which then accidentally matches inside the
;; mock advisor's own DEFAULT rationale/disclaimer text for a legitimate,
;; allowed proposal -- causing the actor to self-block its own happy
;; path. This is a dedicated regression test: every op the default mock
;; advisor can generate, with default (non-`out-of-scope?`) request
;; patches, must NEVER trip `:scope-excluded` or `:op-not-allowed`.
(deftest default-mock-advisor-proposals-never-self-trip-scope-exclusion
  (testing "the default mock advisor's own proposals for every allowed op never trip the governor's scope-exclusion check"
    (let [s (store/mem-store {"coach-1" coach-1} {"provider-1" provider-1})]
      (doseq [op [:log-service-record :schedule-dispatch-operation :coordinate-maintenance-order
                  :flag-safety-concern]]
        (let [patch (if (= op :coordinate-maintenance-order)
                      {:item "scheduled brake inspection" :estimated-cost 380.0 :provider-id "provider-1"}
                      {})
              proposal (adv/infer nil {:op op :vehicle-id "coach-1" :patch patch})
              verdict (gov/check {:vehicle-id "coach-1"} nil proposal s)]
          (is (empty? (filter #(= :scope-excluded (:rule %)) (:violations verdict)))
              (str "default advisor proposal for " op " must never self-trip :scope-excluded -- rationale/summary: "
                   (pr-str (select-keys proposal [:summary :rationale]))))
          (is (empty? (filter #(= :op-not-allowed (:rule %)) (:violations verdict)))
              (str "default advisor proposal for " op " must always be inside the closed op allowlist")))))))

(deftest out-of-scope-test-hook-does-trip-scope-exclusion
  (testing "sanity check: the advisor's own `out-of-scope?` test hook (used by governor-contract-test) really does trip :scope-excluded, so the patterns are not vacuously non-matching"
    (let [s (store/mem-store {"coach-1" coach-1})
          proposal (adv/infer nil {:op :log-service-record :vehicle-id "coach-1" :out-of-scope? true :patch {}})
          verdict (gov/check {:vehicle-id "coach-1"} nil proposal s)]
      (is (true? (:hard? verdict)))
      (is (some #{:scope-excluded} (map :rule (:violations verdict)))))))

(deftest never-a-hard-block-that-finalizes-safety-clearance-in-allowlist
  (testing "structural invariant: no op in the closed allowlist is itself a dispatch-safety-clearance or driver-fitness-to-drive finalization op -- this actor coordinates SCHEDULING/DISPATCH LOGISTICS ONLY"
    (doseq [op gov/allowed-ops]
      (is (not (contains? #{:finalize-dispatch-safety-clearance :finalize-driver-fitness-determination
                             :clear-vehicle-to-dispatch :certify-driver-fit-to-drive} op))))))
