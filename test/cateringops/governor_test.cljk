(ns cateringops.governor-test
  (:require [clojure.test :refer [deftest testing is]]
            [cateringops.store :as store]
            [cateringops.governor :as governor]))

(defn- verified-store []
  (let [s (store/mem-store)]
    (store/register-event! s (store/new-event "E1" "Party" "John" "2026-08-01" "Venue" 50))
    (store/verify-event! s "E1")
    s))

(deftest governor-verification-check
  (let [s (verified-store)]
    (testing "unverified target -- HARD violation"
      (let [proposal {:operation :schedule-catering-event :target-id "E999" :effect :propose :data {}}
            v (governor/check proposal s)]
        (is (not (:ok? v)))
        (is (some #(= :target-not-verified (:rule %)) (:violations v)))))
    (testing "verified target -- passes"
      (let [proposal {:operation :schedule-catering-event :target-id "E1" :effect :propose :data {}}
            v (governor/check proposal s)]
        (is (:ok? v))
        (is (empty? (:violations v)))))
    (testing "ops without a verifiable target skip the check"
      (let [proposal {:operation :coordinate-supply-request :target-id "no-such-supply" :effect :propose :data {}}
            v (governor/check proposal s)]
        (is (:ok? v))))))

(deftest governor-effect-check
  (let [s (verified-store)
        proposal {:operation :schedule-catering-event :target-id "E1" :effect :execute :data {}}
        v (governor/check proposal s)]
    (is (not (:ok? v)))
    (is (some #(= :effect-not-propose (:rule %)) (:violations v)))))

(deftest governor-scope-exclusion
  (let [s (verified-store)]
    (testing "food-safety content is excluded"
      (let [proposal {:operation :schedule-catering-event :target-id "E1" :effect :propose
                       :data {:notes "Check food-safety regulations"}}
            v (governor/check proposal s)]
        (is (not (:ok? v)))
        (is (some #(= :scope-excluded (:rule %)) (:violations v)))))
    (testing "recipe content is excluded"
      (let [proposal {:operation :schedule-catering-event :target-id "E1" :effect :propose
                       :data {:notes "Update recipe for main course"}}
            v (governor/check proposal s)]
        (is (not (:ok? v)))
        (is (some #(= :scope-excluded (:rule %)) (:violations v)))))
    (testing "Japanese scope terms are excluded too"
      (let [proposal {:operation :schedule-catering-event :target-id "E1" :effect :propose
                       :data {:notes "調理法を確認する"}}
            v (governor/check proposal s)]
        (is (not (:ok? v)))))
    (testing "flag-safety-concern is exempt from its own 'safety' mention"
      (let [proposal {:operation :flag-safety-concern :target-id "E1" :effect :propose
                       :data {:concern "Facility hazard detected"}}
            v (governor/check proposal s)]
        (is (:ok? v))))))

(deftest governor-full-decision-passes
  (let [s (verified-store)
        proposal {:operation :schedule-catering-event :target-id "E1" :effect :propose :data {}}]
    (is (:approved? (governor/check proposal s)))))
