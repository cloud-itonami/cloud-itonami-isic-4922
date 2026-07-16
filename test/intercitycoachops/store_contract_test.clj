(ns intercitycoachops.store-contract-test
  "Contract tests for `intercitycoachops.store/Store` protocol."
  (:require [clojure.test :refer [deftest is testing]]
            [intercitycoachops.store :as store]))

(deftest mem-store-vehicle-lookup
  (testing "MemStore can store and retrieve vehicles by ID (string keys)"
    (let [vehicles {"c1" {:vehicle-id "c1" :name "Alice's Express Coach" :registered? true :verified? true}}
          s (store/mem-store vehicles)]
      (is (some? (store/vehicle-record s "c1")))
      (is (nil? (store/vehicle-record s "c99"))))))

(deftest mem-store-all-vehicle-records
  (testing "MemStore returns all vehicles in sorted order"
    (let [vehicles {"c2" {:vehicle-id "c2" :name "Bob's Charter Coach"}
                    "c1" {:vehicle-id "c1" :name "Alice's Express Coach"}
                    "c3" {:vehicle-id "c3" :name "Carol's Overnight Coach"}}
          s (store/mem-store vehicles)
          all-v (store/all-vehicle-records s)]
      (is (= 3 (count all-v)))
      (is (= "c1" (:vehicle-id (first all-v))))
      (is (= "c3" (:vehicle-id (last all-v)))))))

(deftest mem-store-provider-lookup
  (testing "MemStore can store and retrieve maintenance providers by ID (string keys)"
    (let [providers {"p1" {:provider-id "p1" :name "Acme Coach Depot" :registered? true :verified? true}}
          s (store/mem-store {} providers)]
      (is (some? (store/provider-record s "p1")))
      (is (nil? (store/provider-record s "p99"))))))

(deftest mem-store-all-provider-records
  (testing "MemStore returns all providers in sorted order"
    (let [providers {"p2" {:provider-id "p2" :name "Beta Depot"}
                     "p1" {:provider-id "p1" :name "Acme Depot"}}
          s (store/mem-store {} providers)
          all-p (store/all-provider-records s)]
      (is (= 2 (count all-p)))
      (is (= "p1" (:provider-id (first all-p)))))))

(deftest mem-store-ledger-append
  (testing "MemStore append-ledger! adds facts to immutable log"
    (let [s (store/mem-store {})
          fact1 {:t :test :data "fact1"}
          fact2 {:t :test :data "fact2"}]
      (is (= 0 (count (store/ledger s))))
      (store/append-ledger! s fact1)
      (is (= 1 (count (store/ledger s))))
      (store/append-ledger! s fact2)
      (is (= 2 (count (store/ledger s)))))))

(deftest mem-store-coordination-log
  (testing "MemStore commit-record! appends to coordination-log"
    (let [s (store/mem-store {})
          record {:op :log-service-record :vehicle-id "c1" :value {:trip-id "trip-1"}}]
      (is (= 0 (count (store/coordination-log s))))
      (store/commit-record! s record)
      (is (= 1 (count (store/coordination-log s))))
      (is (= record (first (store/coordination-log s)))))))

(deftest mem-store-with-vehicle-records
  (testing "MemStore with-vehicle-records replaces the vehicle directory"
    (let [s (store/mem-store {})
          new-vehicles {"c1" {:vehicle-id "c1" :name "Alice's Express Coach"}}]
      (is (= 0 (count (store/all-vehicle-records s))))
      (store/with-vehicle-records s new-vehicles)
      (is (= 1 (count (store/all-vehicle-records s)))))))

(deftest mem-store-with-provider-records
  (testing "MemStore with-provider-records replaces the provider directory"
    (let [s (store/mem-store {})
          new-providers {"p1" {:provider-id "p1" :name "Acme Depot"}}]
      (is (= 0 (count (store/all-provider-records s))))
      (store/with-provider-records s new-providers)
      (is (= 1 (count (store/all-provider-records s)))))))

(deftest seed-db-has-demo-data
  (testing "seed-db creates a populated MemStore with demo vehicles and providers"
    (let [s (store/seed-db)]
      (is (> (count (store/all-vehicle-records s)) 0))
      (is (some? (store/vehicle-record s "coach-1")))
      (is (some? (store/vehicle-record s "coach-2")))
      (is (some? (store/vehicle-record s "coach-3")))
      (is (> (count (store/all-provider-records s)) 0))
      (is (some? (store/provider-record s "provider-1")))
      (is (some? (store/provider-record s "provider-2"))))))

(deftest demo-data-string-key-consistency
  (testing "demo-data uses string keys, not keywords, for vehicle-id/provider-id"
    (let [demo (store/demo-data)
          vehicles (:vehicles demo)
          providers (:providers demo)]
      (doseq [[k v] vehicles]
        (is (string? k) "vehicle keys must be strings")
        (is (string? (:vehicle-id v)) "vehicle-id must be string")
        (is (= k (:vehicle-id v)) "key must match vehicle-id"))
      (doseq [[k v] providers]
        (is (string? k) "provider keys must be strings")
        (is (string? (:provider-id v)) "provider-id must be string")
        (is (= k (:provider-id v)) "key must match provider-id")))))

(deftest store-is-append-only
  (testing "appended facts are immutable and never removed"
    (let [s (store/seed-db)
          fact1 {:t :event1 :data "a"}
          fact2 {:t :event2 :data "b"}]
      (store/append-ledger! s fact1)
      (let [ledger-after-1 (store/ledger s)]
        (store/append-ledger! s fact2)
        (let [ledger-after-2 (store/ledger s)]
          (is (= (count ledger-after-1) (dec (count ledger-after-2))))
          (is (every? #(some (fn [x] (= x %)) ledger-after-2) ledger-after-1)
              "all prior facts must still be present"))))))
