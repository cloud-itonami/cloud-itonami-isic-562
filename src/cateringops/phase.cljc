(ns cateringops.phase
  "Rollout phases 0–3 (auto-commit gate control)")

;; --- Phase Definitions ---

(def PHASE_0 0)
(def PHASE_1 1)
(def PHASE_2 2)
(def PHASE_3 3)

(defn phase-auto-commit?
  "Check if operation auto-commits in given phase"
  [phase operation]
  (case phase
    0 false  ;; Phase 0: read-only, all held
    1 (case operation
        :schedule-catering-event true
        :coordinate-delivery-status-update true
        false)
    2 (case operation
        :schedule-catering-event true
        :coordinate-delivery-status-update true
        :coordinate-supply-request true
        :schedule-staff-shift-proposal true
        false)
    3 (case operation
        :flag-safety-concern false  ;; Always escalate, never auto-commit
        true)  ;; All others auto-commit
    false))  ;; Unknown phase

(defn phase-allows-operation?
  "Check if operation is allowed in given phase"
  [phase operation]
  ;; All operations allowed in all phases (Governor already filtered scope)
  ;; But auto-commit depends on phase
  (if (= operation :flag-safety-concern)
    true  ;; Always allowed, always escalates
    true))

(defn apply-phase-gate
  "Apply phase-based commitment logic to operation result"
  [phase result]
  (let [operation (get-in result [:proposal :operation])]
    (if (= (:stage result) :approved)
      (if (phase-auto-commit? phase operation)
        (assoc result :stage :committed :reason "Auto-committed by phase gate")
        (assoc result :stage :held :reason "Held for human review in this phase"))
      result)))

;; --- Demo: Phase Progression ---

(defn demo-phase-progression
  "Show how same proposal is handled across phases"
  []
  (let [result {:stage :approved :proposal {:operation :schedule-catering-event :target-id "E001"}}]
    {0 (apply-phase-gate PHASE_0 result)
     1 (apply-phase-gate PHASE_1 result)
     2 (apply-phase-gate PHASE_2 result)
     3 (apply-phase-gate PHASE_3 result)}))
