(ns cateringops.store-test
  "Real `clojure.test` coverage for `cateringops.store`'s `Store`
  protocol (MemStore). This REPLACES the prior hand-rolled harness in
  `test/cateringops/test.cljc`, which printed '[Y] <name>' lines but
  caught every failure inside a `try/catch` that just logged
  'Error in ...' and unconditionally printed 'All tests passed!
  (16/16)' regardless of what actually happened -- i.e. it could not
  detect a real failure. Every assertion below is a real
  `clojure.test/is` that fails the run (and CI, via
  `cognitect.test-runner`) on a genuine regression."
  (:require [clojure.test :refer [deftest testing is]]
            [cateringops.store :as store]))

(deftest store-event-and-delivery-lifecycle
  (let [s (store/mem-store)
        e (store/new-event "E1" "Party" "John" "2026-08-01" "Venue" 50)]
    (store/register-event! s e)
    (testing "event lookup returns a registered event"
      (let [found (store/lookup-event s "E1")]
        (is (some? found))
        (is (:registered? found))
        (is (not (:verified? found)))))
    (testing "verify-event! marks the event verified"
      (store/verify-event! s "E1")
      (is (:verified? (store/lookup-event s "E1"))))
    (testing "unregistered lookup is nil"
      (is (nil? (store/lookup-event s "no-such-event"))))
    (testing "all-events lists every registered event"
      (store/register-event! s (store/new-event "E2" "Wedding" "Jane" "2026-09-01" "Hall" 150))
      (is (= 2 (count (store/all-events s)))))
    (testing "delivery register+verify lifecycle mirrors event"
      (let [d (store/new-delivery "D1" "E1" "Venue" "2026-08-01 15:00")]
        (store/register-delivery! s d)
        (is (:registered? (store/lookup-delivery s "D1")))
        (is (not (:verified? (store/lookup-delivery s "D1"))))
        (store/verify-delivery! s "D1")
        (is (:verified? (store/lookup-delivery s "D1")))))))

(deftest store-supply-and-shift
  (let [s (store/mem-store)
        supp (store/new-supply-item "S1" "E1" "linens" 100 0.50)
        shift (store/new-staff-shift "ST1" "E1" :server "16:00" "22:00")]
    (store/add-supply! s supp)
    (store/add-shift! s shift)
    (testing "supply lookup"
      (is (= "linens" (:item-type (store/lookup-supply s "S1")))))
    (testing "shift lookup"
      (is (= :server (:role (store/lookup-shift s "ST1")))))))

(deftest store-ledger-append
  (let [s (store/mem-store)]
    (is (empty? (store/ledger s)))
    (store/append-ledger! s {:t :committed :op :test :time "2026-07-15"})
    (is (= 1 (count (store/ledger s))))
    (store/append-ledger! s {:t :governor-hold :op :test2})
    (is (= 2 (count (store/ledger s))))))
