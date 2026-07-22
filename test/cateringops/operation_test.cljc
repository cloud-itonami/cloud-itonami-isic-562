(ns cateringops.operation-test
  "End-to-end coverage of the REAL compiled `langgraph-clj` StateGraph
  (`cateringops.operation/build`) -- a commit path, a hard-hold path,
  and both branches of the escalate -> human-in-the-loop resume flow
  (approve -> commit, reject -> hold), asserting the graph's own
  `:status`/`:frontier` (proving a genuine `interrupt-before` pause,
  not a simulated one) AND that the `:commit`/`:hold` nodes genuinely
  appended to the Store's append-only ledger."
  (:require [clojure.test :refer [deftest testing is]]
            [cateringops.store :as store]
            [cateringops.operation :as operation]
            [langgraph.graph :as g]))

(defn- verified-store []
  (let [s (store/mem-store)]
    (store/register-event! s (store/new-event "E1" "Party" "John" "2026-08-01" "Venue" 50))
    (store/verify-event! s "E1")
    s))

(deftest operation-commit-path
  (let [s (verified-store)
        actor (operation/build s)
        result (g/run* actor
                        {:request {:operation :schedule-catering-event :target-id "E1"
                                   :data {:venue "Grand Hall" :date "2026-08-15" :capacity 150}}
                         :context {:actor-id "test-op" :phase 3}}
                        {:thread-id "test-commit"})]
    (is (= :done (:status result)))
    (is (= :commit (get-in result [:state :disposition])))
    (testing "a real ledger entry was appended by the :commit node"
      (is (= 1 (count (store/ledger s))))
      (is (= :committed (:t (first (store/ledger s))))))))

(deftest operation-hard-hold-path
  (let [s (verified-store)
        actor (operation/build s)
        result (g/run* actor
                        {:request {:operation :schedule-catering-event :target-id "no-such-event"
                                   :data {}}
                         :context {:actor-id "test-op" :phase 3}}
                        {:thread-id "test-hold"})]
    (is (= :done (:status result)))
    (is (= :hold (get-in result [:state :disposition])))
    (testing "the hold reason is a real Governor violation, not a phase gate"
      (is (some #(= :target-not-verified (:rule %))
                (get-in result [:state :verdict :violations]))))
    (testing "a real ledger entry was appended by the :hold node"
      (is (= 1 (count (store/ledger s))))
      (is (= :governor-hold (:t (first (store/ledger s))))))))

(deftest operation-escalate-approve-commit-path
  (let [s (verified-store)
        actor (operation/build s)
        tid "test-escalate-approve"
        r1 (g/run* actor
                    {:request {:operation :flag-safety-concern :target-id "E1"
                               :data {:concern "Facility hazard"}}
                     :context {:actor-id "test-op" :phase 3}}
                    {:thread-id tid})]
    (testing "the graph genuinely pauses at :request-approval (interrupt-before)"
      (is (= :interrupted (:status r1)))
      (is (= [:request-approval] (:frontier r1))))
    (let [r2 (g/run* actor {:approval {:status :approved :by "human-01"}}
                      {:thread-id tid :resume? true})]
      (testing "resume with an approval commits"
        (is (= :done (:status r2)))
        (is (= :commit (get-in r2 [:state :disposition]))))
      (testing "the ledger now has the escalated-then-committed fact"
        (is (= 1 (count (store/ledger s))))
        (is (= :committed (:t (first (store/ledger s)))))))))

(deftest operation-escalate-reject-hold-path
  (let [s (verified-store)
        actor (operation/build s)
        tid "test-escalate-reject"
        _ (g/run* actor
                   {:request {:operation :flag-safety-concern :target-id "E1"
                              :data {:concern "Facility hazard"}}
                    :context {:actor-id "test-op" :phase 3}}
                   {:thread-id tid})
        r2 (g/run* actor {:approval {:status :rejected :by "human-01"}}
                    {:thread-id tid :resume? true})]
    (testing "resume with a rejection holds"
      (is (= :done (:status r2)))
      (is (= :hold (get-in r2 [:state :disposition]))))
    (testing "the ledger records the approval-rejected fact"
      (is (= 1 (count (store/ledger s))))
      (is (= :approval-rejected (:t (first (store/ledger s))))))))
