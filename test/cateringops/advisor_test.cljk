(ns cateringops.advisor-test
  (:require [clojure.test :refer [deftest testing is]]
            [cateringops.store :as store]
            [cateringops.advisor :as advisor]))

(defn- verified-store []
  (let [s (store/mem-store)]
    (store/register-event! s (store/new-event "E1" "Party" "John" "2026-08-01" "Venue" 50))
    (store/verify-event! s "E1")
    s))

(deftest advisor-proposal-shape
  (let [adv (advisor/mock-advisor)
        s (verified-store)
        request {:operation :schedule-catering-event :target-id "E1"
                  :data {:venue "Grand Hall" :date "2026-08-15" :capacity 150}}
        p (advisor/-advise adv s request)]
    (is (= :schedule-catering-event (:operation p)))
    (is (= :propose (:effect p)))
    (is (= 0.95 (:confidence p)))
    (is (seq (:cites p)))
    (is (string? (:summary p)))))

(deftest advisor-scoring-matrix
  (testing "schedule-catering-event: complete data scores high"
    (is (= 0.95 (advisor/advisor-score :schedule-catering-event
                                        {:venue "v" :date "d" :capacity 10}))))
  (testing "schedule-catering-event: incomplete data scores low"
    (is (= 0.5 (advisor/advisor-score :schedule-catering-event {}))))
  (testing "coordinate-delivery-status-update: complete data scores high"
    (is (= 0.90 (advisor/advisor-score :coordinate-delivery-status-update
                                        {:destination "d" :time "t"}))))
  (testing "coordinate-supply-request: complete data scores high"
    (is (= 0.88 (advisor/advisor-score :coordinate-supply-request
                                        {:item-type "linens" :quantity 10 :unit-cost 1.0}))))
  (testing "schedule-staff-shift-proposal: complete data scores high"
    (is (= 0.85 (advisor/advisor-score :schedule-staff-shift-proposal
                                        {:role :server :start-time "16:00" :end-time "22:00"}))))
  (testing "flag-safety-concern: string concern scores 1.0"
    (is (= 1.0 (advisor/advisor-score :flag-safety-concern {:concern "hazard"}))))
  (testing "unknown op scores 0.0"
    (is (= 0.0 (advisor/advisor-score :unknown-op {})))))
