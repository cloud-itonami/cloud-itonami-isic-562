(ns cateringops.advisor
  "CateringOpsAdvisor -- the contained decision node. This actor's
  intelligence layer proposes back-office coordination actions (event
  booking/scheduling, delivery status tracking, non-food supply
  coordination, staff shift proposals, facility safety-concern flags)
  based on event/delivery state and operator input. The advisor is
  SEALED into the `:advise` step of the operation flow; every proposal
  is routed through the independent Governor before committing.

  The advisor makes proposals but has NO direct authority. Proposals
  are always censored by:
    1. Governor (target verification, effect-must-be-:propose, scope
       exclusion -- `cateringops.governor`)
    2. Phase gate (rollout stage -- `cateringops.phase`)
    3. Human operator (for escalated actions)

  Current implementation is a deterministic mock advisor for testing
  (the confidence-scoring functions below are unchanged from this
  actor's pre-StateGraph implementation -- only how they're wired in
  changed: they are now genuinely invoked by `cateringops.operation`'s
  `:advise` node, not dead code). Production should swap in a real
  LLM/langchain backend behind the same `Advisor` protocol (same seam
  point as every sibling cloud-itonami actor's advisor)."
  )

;; ----------------------------- confidence scoring -----------------------------
;; Deterministic scoring, preserved verbatim from the pre-StateGraph
;; implementation. Each fn scores the REQUEST's :data payload (the
;; fields an operator actually submits), not the full proposal
;; envelope.

(defn score-schedule-event
  "Confidence score for event scheduling (0.0-1.0)."
  [data]
  (let [has-venue (boolean (:venue data))
        has-date (boolean (:date data))
        has-capacity (number? (:capacity data))]
    (if (and has-venue has-date has-capacity)
      0.95
      0.5)))

(defn score-delivery-update
  "Confidence score for delivery status update."
  [data]
  (let [has-destination (boolean (:destination data))
        has-time (boolean (:time data))]
    (if (and has-destination has-time)
      0.90
      0.5)))

(defn score-supply-request
  "Confidence score for supply coordination."
  [data]
  (let [has-item-type (boolean (:item-type data))
        has-quantity (number? (:quantity data))
        has-cost (number? (:unit-cost data))]
    (if (and has-item-type has-quantity has-cost)
      0.88
      0.5)))

(defn score-shift-proposal
  "Confidence score for staff shift proposal."
  [data]
  (let [has-role (boolean (:role data))
        has-times (and (:start-time data) (:end-time data))]
    (if (and has-role has-times)
      0.85
      0.5)))

(defn score-safety-concern
  "Confidence score for safety escalation."
  [data]
  (if (string? (:concern data))
    1.0
    0.5))

(defn advisor-score
  "Deterministic scoring for all operation types."
  [operation data]
  (case operation
    :schedule-catering-event (score-schedule-event data)
    :coordinate-delivery-status-update (score-delivery-update data)
    :coordinate-supply-request (score-supply-request data)
    :schedule-staff-shift-proposal (score-shift-proposal data)
    :flag-safety-concern (score-safety-concern data)
    0.0))

;; ----------------------------- Advisor protocol -----------------------------

(defprotocol Advisor
  (-advise [advisor store request]
    "Given store and request ({:operation kw :target-id str :data map}),
    return a proposal map with :operation, :target-id, :effect :propose,
    :data, :confidence, plus :cites/:summary for the audit trail."))

(defrecord MockAdvisor []
  Advisor
  (-advise [_advisor _store request]
    (let [{:keys [operation target-id data]} request
          confidence (advisor-score operation data)]
      {:operation operation
       :target-id target-id
       :effect :propose
       :data data
       :confidence confidence
       :cites ["operator-submitted-request"]
       :summary (str "Proposal for " (name operation) " targeting " target-id)})))

(defn mock-advisor [] (MockAdvisor.))

(defn trace
  "Audit trail entry for an advisor proposal. Recorded whenever a
  proposal is generated, regardless of whether it's approved."
  [request proposal]
  {:t :advisor-proposal
   :op (:operation request)
   :target-id (:target-id request)
   :proposal-summary (:summary proposal)
   :confidence (:confidence proposal)})
