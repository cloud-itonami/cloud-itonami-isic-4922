(ns intercitycoachops.advisor-test
  "Unit tests of `intercitycoachops.advisor` proposal generation."
  (:require [clojure.test :refer [deftest is testing]]
            [intercitycoachops.advisor :as adv]
            [intercitycoachops.store :as store]))

(def db (store/seed-db))

(deftest propose-service-record-shape
  (testing "service-record proposal has correct shape and fields"
    (let [p (adv/infer db {:op :log-service-record
                           :vehicle-id "coach-1"
                           :patch {:trip-id "trip-501" :ridership 38 :incidents 0}})]
      (is (= :log-service-record (:op p)))
      (is (= "coach-1" (:vehicle-id p)))
      (is (= :propose (:effect p)))
      (is (<= 0 (:confidence p) 1))
      (is (map? (:value p)))
      (is (contains? (:value p) :vehicle-id)))))

(deftest propose-dispatch-operation-shape
  (testing "dispatch-operation proposal has correct shape"
    (let [p (adv/infer db {:op :schedule-dispatch-operation
                           :vehicle-id "coach-2"
                           :patch {:route "City A <-> City B" :date "2026-07-20"}})]
      (is (= :schedule-dispatch-operation (:op p)))
      (is (= "coach-2" (:vehicle-id p)))
      (is (= :propose (:effect p))))))

(deftest propose-maintenance-order-shape
  (testing "maintenance-order proposal has correct shape"
    (let [p (adv/infer db {:op :coordinate-maintenance-order
                           :vehicle-id "coach-1"
                           :patch {:item "scheduled brake inspection" :estimated-cost 380.0
                                   :provider-id "provider-1"}})]
      (is (= :coordinate-maintenance-order (:op p)))
      (is (= :propose (:effect p)))
      (is (string? (:summary p)))
      (is (= "provider-1" (get-in p [:value :provider-id]))))))

(deftest propose-safety-concern-shape
  (testing "safety-concern proposal always escalates"
    (let [p (adv/infer db {:op :flag-safety-concern
                           :vehicle-id "coach-1"
                           :patch {:concern "intermittent brake warning light"}})]
      (is (= :flag-safety-concern (:op p)))
      (is (= :propose (:effect p)))
      (is (string? (:summary p))))))

(deftest all-proposals-effect-is-always-propose
  (testing "every proposal type has :effect :propose, never direct actuation"
    (doseq [op [:log-service-record :schedule-dispatch-operation :coordinate-maintenance-order
                :flag-safety-concern]]
      (let [p (adv/infer db {:op op :vehicle-id "coach-1" :patch {}})]
        (is (= :propose (:effect p))
            (str "op " op " must have :effect :propose"))))))

(deftest rationale-string-is-present
  (testing "every proposal has a rationale explaining the advisor's thinking"
    (doseq [op [:log-service-record :schedule-dispatch-operation :coordinate-maintenance-order
                :flag-safety-concern]]
      (let [p (adv/infer db {:op op :vehicle-id "coach-1" :patch {}})]
        (is (string? (:rationale p))
            (str "op " op " must have a :rationale string"))))))
