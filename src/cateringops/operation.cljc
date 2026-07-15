(ns cateringops.operation
  "langgraph-clj StateGraph orchestration (deterministic sim variant)"
  (:require [cateringops.governor :as gov]))

;; --- Operation State Graph ---

(defn start-operation
  "StateGraph: start node"
  [state]
  (assoc state :stage :started))

(defn evaluate-proposal
  "StateGraph: Governor evaluation"
  [state]
  (let [proposal (:proposal state)
        store (:store state)
        decision (gov/propose-operation
                  (:operation proposal)
                  (:target-id proposal)
                  (:data proposal)
                  store)]
    (assoc state
           :proposal proposal
           :governor-decision decision
           :stage (if (:approved? decision) :approved :rejected))))

(defn commit-or-escalate
  "StateGraph: Commit if approved, escalate if safety concern"
  [state]
  (let [decision (:governor-decision state)
        operation (get-in state [:proposal :operation])]
    (cond
      (not (:approved? decision))
      (assoc state :stage :rejected :result "Proposal rejected by Governor")

      (= operation :flag-safety-concern)
      (assoc state :stage :escalated :result "Safety concern escalated to human review")

      :else
      (assoc state :stage :committed :result "Proposal committed"))))

(defn end-operation
  "StateGraph: end node"
  [state]
  (assoc state :complete? true))

;; --- Simplified State Machine (deterministic) ---

(defn execute-operation
  "Execute a full operation through state machine"
  [operation target-id data store]
  (let [state {:operation operation
               :target-id target-id
               :data data
               :store store
               :proposal {:operation operation :target-id target-id :data data :effect :propose}
               :stage :init}]
    (-> state
        start-operation
        evaluate-proposal
        commit-or-escalate
        end-operation)))

(defn graph-execute
  "StateGraph-compatible executor (returns final state)"
  [operation target-id data store]
  (execute-operation operation target-id data store))
