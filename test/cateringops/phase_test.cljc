(ns cateringops.phase-test
  (:require [clojure.test :refer [deftest testing is]]
            [cateringops.phase :as phase]))

(deftest phase-auto-commit-matrix
  (testing "phase 0 auto-commits nothing"
    (doseq [op [:schedule-catering-event :coordinate-delivery-status-update
                :coordinate-supply-request :schedule-staff-shift-proposal]]
      (is (not (phase/phase-auto-commit? 0 op)))))
  (testing "phase 1 auto-commits event scheduling and delivery status only"
    (is (phase/phase-auto-commit? 1 :schedule-catering-event))
    (is (phase/phase-auto-commit? 1 :coordinate-delivery-status-update))
    (is (not (phase/phase-auto-commit? 1 :coordinate-supply-request))))
  (testing "phase 2 adds supply + shift"
    (is (phase/phase-auto-commit? 2 :coordinate-supply-request))
    (is (phase/phase-auto-commit? 2 :schedule-staff-shift-proposal)))
  (testing "phase 3 auto-commits everything except flag-safety-concern"
    (is (phase/phase-auto-commit? 3 :schedule-catering-event))
    (is (phase/phase-auto-commit? 3 :coordinate-supply-request))
    (is (not (phase/phase-auto-commit? 3 :flag-safety-concern)))))

(deftest phase-gate-translates-disposition
  (testing "hold passes through unchanged"
    (is (= {:disposition :hold :reason nil} (phase/gate 3 {:operation :schedule-catering-event} :hold))))
  (testing "phase 3 commit for an auto-commit op stays commit"
    (is (= :commit (:disposition (phase/gate 3 {:operation :schedule-catering-event} :commit)))))
  (testing "phase 0 downgrades commit to escalate"
    (is (= :escalate (:disposition (phase/gate 0 {:operation :schedule-catering-event} :commit)))))
  (testing "phase 3 downgrades flag-safety-concern commit to escalate"
    (is (= :escalate (:disposition (phase/gate 3 {:operation :flag-safety-concern} :commit))))))
