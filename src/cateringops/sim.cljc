(ns cateringops.sim
  "Deterministic demo runner (5 scenarios)"
  (:require [cateringops.store :as store]
            [cateringops.operation :as op]
            [cateringops.phase :as phase]
            [cateringops.governor :as gov]))

;; --- Demo Scenarios ---

(defn scenario-1-event-scheduling
  "Scenario 1: Schedule catering event (happy path)"
  [s]
  (println "\n[Scenario 1] Event Scheduling (Happy Path)")
  (let [store (store/load-demo-events! s)]
    (store/register-event! store (store/new-event "E003" "Birthday Party" "Jane Doe" "2026-08-10" "Community Center" 80))
    (store/verify-event! store "E003")
    (let [result (op/execute-operation
                  :schedule-catering-event
                  "E003"
                  {:capacity 80 :date "2026-08-10"}
                  store)]
      (println (str "  Result: " (:stage result)))
      result)))

(defn scenario-2-delivery-status
  "Scenario 2: Coordinate delivery status update"
  [s]
  (println "\n[Scenario 2] Delivery Status Update")
  (let [store (store/load-demo-deliveries! s)]
    (let [result (op/execute-operation
                  :coordinate-delivery-status-update
                  "D001"
                  {:status :in-transit}
                  store)]
      (println (str "  Result: " (:stage result)))
      result)))

(defn scenario-3-supply-request
  "Scenario 3: Coordinate supply request (non-food)"
  [s]
  (println "\n[Scenario 3] Supply Request (Non-Food)")
  (let [store (store/load-demo-supplies! s)]
    (let [result (op/execute-operation
                  :coordinate-supply-request
                  "S001"
                  {:quantity 100 :item-type "linens"}
                  store)]
      (println (str "  Result: " (:stage result)))
      result)))

(defn scenario-4-staff-shift
  "Scenario 4: Staff shift proposal"
  [s]
  (println "\n[Scenario 4] Staff Shift Proposal")
  (let [store (store/load-demo-shifts! s)]
    (let [result (op/execute-operation
                  :schedule-staff-shift-proposal
                  "ST001"
                  {:role :server :duration-hours 6}
                  store)]
      (println (str "  Result: " (:stage result)))
      result)))

(defn scenario-5-safety-escalation
  "Scenario 5: Safety concern escalation (always escalates, never auto-commits)"
  [s]
  (println "\n[Scenario 5] Safety Concern Escalation")
  (let [store (store/load-demo-events! s)]
    (let [result (op/execute-operation
                  :flag-safety-concern
                  "E001"
                  {:concern "Facility ventilation issue detected"}
                  store)]
      (println (str "  Result: " (:stage result)))
      result)))

;; --- Demo Runner ---

(defn run-all-scenarios
  "Run all 5 demo scenarios"
  []
  (println "╔════════════════════════════════════════════════════════════╗")
  (println "║ ISIC-562 Event Catering Coordination Actor Demo            ║")
  (println "╚════════════════════════════════════════════════════════════╝")
  (let [store (store/new-store)]
    (let [results
          [(scenario-1-event-scheduling store)
           (scenario-2-delivery-status (store/new-store))
           (scenario-3-supply-request (store/new-store))
           (scenario-4-staff-shift (store/new-store))
           (scenario-5-safety-escalation (store/new-store))]]
      (println "\n" "─── Summary ───")
      (println (str "5/5 scenarios completed"))
      results)))
