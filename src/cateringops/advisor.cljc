(ns cateringops.advisor
  "Proposal scoring (deterministic for demo)")

(defn score-schedule-event
  "Confidence score for event scheduling (0.0–1.0)"
  [proposal]
  (let [has-venue (boolean (:venue proposal))
        has-date (boolean (:date proposal))
        has-capacity (number? (:capacity proposal))]
    (if (and has-venue has-date has-capacity)
      0.95
      0.5)))

(defn score-delivery-update
  "Confidence score for delivery status update"
  [proposal]
  (let [has-destination (boolean (:destination proposal))
        has-time (boolean (:time proposal))]
    (if (and has-destination has-time)
      0.90
      0.5)))

(defn score-supply-request
  "Confidence score for supply coordination"
  [proposal]
  (let [has-item-type (boolean (:item-type proposal))
        has-quantity (number? (:quantity proposal))
        has-cost (number? (:unit-cost proposal))]
    (if (and has-item-type has-quantity has-cost)
      0.88
      0.5)))

(defn score-shift-proposal
  "Confidence score for staff shift proposal"
  [proposal]
  (let [has-role (boolean (:role proposal))
        has-times (and (:start-time proposal) (:end-time proposal))]
    (if (and has-role has-times)
      0.85
      0.5)))

(defn score-safety-concern
  "Confidence score for safety escalation"
  [proposal]
  (if (string? (:concern proposal))
    1.0
    0.5))

(defn advisor-score
  "Deterministic scoring for all operation types"
  [operation proposal]
  (case operation
    :schedule-catering-event (score-schedule-event proposal)
    :coordinate-delivery-status-update (score-delivery-update proposal)
    :coordinate-supply-request (score-supply-request proposal)
    :schedule-staff-shift-proposal (score-shift-proposal proposal)
    :flag-safety-concern (score-safety-concern proposal)
    0.0))
