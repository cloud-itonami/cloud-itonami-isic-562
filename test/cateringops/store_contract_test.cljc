(ns cateringops.store-contract-test
  "MemStore ≡ DatomicStore parity for the Store protocol. Mirrors
  `cerealops.store-contract-test` (cloud-itonami-isic-0111)."
  (:require [clojure.test :refer [deftest is]]
            [cateringops.store :as store]))

(defn- exercise [s]
  (store/register-event! s (store/new-event "E1" "Party" "John" "2026-08-01" "Venue" 50))
  ;; re-registering (update) exercises the identity-upsert path on
  ;; DatomicStore (:event/id is :db.unique/identity) the same way
  ;; MemStore's plain `assoc` re-registration does.
  (store/register-event! s (store/new-event "E1" "Party (renamed)" "John" "2026-08-01" "Venue" 50))
  (store/verify-event! s "E1")
  (store/register-delivery! s (store/new-delivery "D1" "E1" "Venue" "2026-08-01 15:00"))
  (store/verify-delivery! s "D1")
  (store/add-supply! s (store/new-supply-item "S1" "E1" "linens" 100 0.50))
  (store/add-shift! s (store/new-staff-shift "ST1" "E1" :server "16:00" "22:00"))
  (store/append-ledger! s {:t :committed :op :schedule-catering-event :subject "E1"})
  (store/append-ledger! s {:t :approval-requested :op :flag-safety-concern :subject "E1"})
  {:event   (store/lookup-event s "E1")
   :absent  (store/lookup-event s "no-such-event")
   :delivery (store/lookup-delivery s "D1")
   :supply  (store/lookup-supply s "S1")
   :shift   (store/lookup-shift s "ST1")
   :all-events-count (count (store/all-events s))
   :ledger  (store/ledger s)})

(deftest mem-and-datomic-parity
  (let [mem (store/mem-store)
        dat (store/datomic-store)
        m (exercise mem)
        d (exercise dat)]
    (is (= (:event m) (:event d)))
    (is (= "Party (renamed)" (:name (:event m))) "re-registration upserts, not forks history")
    (is (:registered? (:event m)))
    (is (:verified? (:event m)))
    (is (nil? (:absent m)))
    (is (nil? (:absent d)))
    (is (= (:delivery m) (:delivery d)))
    (is (:verified? (:delivery m)))
    (is (= (:supply m) (:supply d)))
    (is (= (:shift m) (:shift d)))
    (is (= 1 (:all-events-count m)))
    (is (= 1 (:all-events-count d)))
    (is (= 2 (count (:ledger m))))
    (is (= 2 (count (:ledger d))))
    (is (= (:ledger m) (:ledger d)))))
