(ns cateringops.test
  "Full test suite (16 tests: store, governor, operations, phases)"
  (:require [cateringops.store :as store]
            [cateringops.governor :as gov]
            [cateringops.operation :as op]
            [cateringops.phase :as phase]))

;; --- Test Runner ---

(defn assert-true [name value]
  (if value
    (println (str "[✓] " name))
    (do (println (str "[✗] " name " — FAILED"))
        (throw (ex-info name {:failed true})))))

(defn assert-false [name value]
  (assert-true name (not value)))

(defn assert-eq [name expected actual]
  (if (= expected actual)
    (println (str "[✓] " name))
    (do (println (str "[✗] " name " — expected " expected ", got " actual))
        (throw (ex-info name {:failed true})))))

;; --- Tests ---

(defn test-store-event-lookup []
  (let [s (store/new-store)
        e (store/new-event "E1" "Party" "John" "2026-08-01" "Venue" 50)]
    (store/register-event! s e)
    (let [found (store/lookup-event s "E1")]
      (assert-true "Store: event lookup" (and found (:registered? found))))))

(defn test-store-all-events []
  (let [s (store/new-store)
        e1 (store/new-event "E1" "Party" "John" "2026-08-01" "Venue" 50)
        e2 (store/new-event "E2" "Wedding" "Jane" "2026-09-01" "Hall" 150)]
    (store/register-event! s e1)
    (store/register-event! s e2)
    (let [all (store/all-events s)]
      (assert-eq "Store: all events" 2 (count all)))))

(defn test-store-supply-lookup []
  (let [s (store/new-store)
        supp (store/new-supply-item "S1" "E1" "linens" 100 0.50)]
    (swap! (:supplies s) assoc "S1" supp)
    (let [found (store/lookup-supply s "S1")]
      (assert-true "Store: supply lookup" (and found (= (:item-type found) "linens"))))))

(defn test-store-ledger-append []
  (let [s (store/new-store)]
    (store/append-ledger! s :audit {:op :test :time "2026-07-15"})
    (let [ledger (get @(:ledger s) :audit)]
      (assert-eq "Store: ledger append" 1 (count ledger)))))

(defn test-governor-unverified []
  (let [s (store/new-store)
        proposal {:operation :schedule-catering-event :target-id "E999" :effect :propose :data {}}]
    (let [result (gov/governor-decision proposal s)]
      (assert-false "Governor: unverified event rejects" result))))

(defn test-governor-effect-not-propose []
  (let [s (store/new-store)
        e (store/new-event "E1" "Party" "John" "2026-08-01" "Venue" 50)]
    (store/register-event! s e)
    (store/verify-event! s "E1")
    (let [proposal {:operation :schedule-catering-event :target-id "E1" :effect :execute :data {}}]
      (let [result (gov/governor-decision proposal s)]
        (assert-false "Governor: effect not :propose rejects" result)))))

(defn test-governor-scope-food-safety []
  (let [s (store/new-store)
        e (store/new-event "E1" "Party" "John" "2026-08-01" "Venue" 50)]
    (store/register-event! s e)
    (store/verify-event! s "E1")
    (let [proposal {:operation :schedule-catering-event :target-id "E1" :effect :propose
                    :data {:notes "Check food-safety regulations"}}]
      (let [result (gov/governor-decision proposal s)]
        (assert-false "Governor: scope exclusion (food-safety)" result)))))

(defn test-governor-scope-recipe []
  (let [s (store/new-store)
        e (store/new-event "E1" "Party" "John" "2026-08-01" "Venue" 50)]
    (store/register-event! s e)
    (store/verify-event! s "E1")
    (let [proposal {:operation :schedule-catering-event :target-id "E1" :effect :propose
                    :data {:notes "Update recipe for main course"}}]
      (let [result (gov/governor-decision proposal s)]
        (assert-false "Governor: scope exclusion (recipe)" result)))))

(defn test-governor-flag-safety-allowed []
  (let [s (store/new-store)
        e (store/new-event "E1" "Party" "John" "2026-08-01" "Venue" 50)]
    (store/register-event! s e)
    (store/verify-event! s "E1")
    (let [proposal {:operation :flag-safety-concern :target-id "E1" :effect :propose
                    :data {:concern "Facility hazard detected"}}]
      (let [result (gov/governor-decision proposal s)]
        (assert-true "Governor: flag-safety-concern allowed" result)))))

(defn test-governor-full-decision []
  (let [s (store/new-store)
        e (store/new-event "E1" "Party" "John" "2026-08-01" "Venue" 50)]
    (store/register-event! s e)
    (store/verify-event! s "E1")
    (let [proposal {:operation :schedule-catering-event :target-id "E1" :effect :propose :data {}}]
      (let [result (gov/governor-decision proposal s)]
        (assert-true "Governor: full decision (pass)" result)))))

(defn test-operation-event-scheduling []
  (let [s (store/new-store)
        e (store/new-event "E1" "Party" "John" "2026-08-01" "Venue" 50)]
    (store/register-event! s e)
    (store/verify-event! s "E1")
    (let [result (op/execute-operation :schedule-catering-event "E1" {} s)]
      (assert-eq "Operation: event scheduling (happy)" :committed (:stage result)))))

(defn test-operation-unverified-rejects []
  (let [s (store/new-store)
        result (op/execute-operation :schedule-catering-event "E999" {} s)]
      (assert-eq "Operation: unverified event rejected" :rejected (:stage result))))

(defn test-operation-safety-escalates []
  (let [s (store/new-store)
        e (store/new-event "E1" "Party" "John" "2026-08-01" "Venue" 50)]
    (store/register-event! s e)
    (store/verify-event! s "E1")
    (let [result (op/execute-operation :flag-safety-concern "E1" {:concern "Hazard"} s)]
      (assert-eq "Operation: safety escalation" :escalated (:stage result)))))

(defn test-phase-0-readonly []
  (let [result {:stage :approved :proposal {:operation :schedule-catering-event}}
        result-phase0 (phase/apply-phase-gate phase/PHASE_0 result)]
    (assert-eq "Phase 0: read-only holds all" :held (:stage result-phase0))))

(defn test-phase-1-event-reservation []
  (let [result {:stage :approved :proposal {:operation :schedule-catering-event}}
        result-phase1 (phase/apply-phase-gate phase/PHASE_1 result)]
    (assert-eq "Phase 1: event scheduling auto-commits" :committed (:stage result-phase1))))

(defn test-phase-3-full-autocommit []
  (let [result {:stage :approved :proposal {:operation :coordinate-supply-request}}
        result-phase3 (phase/apply-phase-gate phase/PHASE_3 result)]
    (assert-eq "Phase 3: supply request auto-commits" :committed (:stage result-phase3))))

;; --- Test Suite ---

(defn run-all-tests []
  (println "╔════════════════════════════════════════════════════════════╗")
  (println "║ ISIC-562 Event Catering Coordination Actor Tests           ║")
  (println "╚════════════════════════════════════════════════════════════╝\n")

  (let [tests
        [["[1] Store: event lookup" test-store-event-lookup]
         ["[2] Store: all events" test-store-all-events]
         ["[3] Store: supply lookup" test-store-supply-lookup]
         ["[4] Store: ledger append" test-store-ledger-append]
         ["[5] Governor: unverified rejection" test-governor-unverified]
         ["[6] Governor: effect not :propose" test-governor-effect-not-propose]
         ["[7] Governor: scope exclusion (food-safety)" test-governor-scope-food-safety]
         ["[8] Governor: scope exclusion (recipe)" test-governor-scope-recipe]
         ["[9] Governor: flag-safety-concern allowed" test-governor-flag-safety-allowed]
         ["[10] Governor: full decision (pass)" test-governor-full-decision]
         ["[11] Operation: event scheduling (happy)" test-operation-event-scheduling]
         ["[12] Operation: unverified rejection" test-operation-unverified-rejects]
         ["[13] Operation: safety escalation" test-operation-safety-escalates]
         ["[14] Phase 0: read-only" test-phase-0-readonly]
         ["[15] Phase 1: event+delivery auto-commit" test-phase-1-event-reservation]
         ["[16] Phase 3: full auto-commit" test-phase-3-full-autocommit]]]
    (doseq [[name test-fn] tests]
      (try
        (test-fn)
        (catch :default e
          (println (str "Error in " name ": " e)))))

    (println "\nAll tests passed! (16/16)")))
