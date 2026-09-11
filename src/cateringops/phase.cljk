(ns cateringops.phase
  "Phase gate for the event-catering operations coordinator rollout
  (0->3 maturity). `phase-auto-commit?` is preserved verbatim from this
  actor's pre-StateGraph implementation (the exact auto-commit matrix
  per phase/operation is unchanged); `gate` is new plumbing that folds
  it into the `:commit|:escalate|:hold` disposition vocabulary
  `cateringops.operation`'s compiled StateGraph routes on.

  Phase 0: read-only -- every proposal that would otherwise commit is
    forced to escalate for human review (mirrors the pre-StateGraph
    behavior of holding everything, but now as a real interrupt-before
    checkpoint a human can act on, not a dead-end).
  Phase 1: event scheduling + delivery status auto-commit; other ops
    escalate.
  Phase 2: + supply coordination + staff shift auto-commit.
  Phase 3: all non-safety ops auto-commit; `:flag-safety-concern`
    NEVER auto-commits at any phase -- always escalates to a human."
  )

;; --- Phase Definitions ---

(def PHASE_0 0)
(def PHASE_1 1)
(def PHASE_2 2)
(def PHASE_3 3)

(def default-phase PHASE_0)

(defn phase-auto-commit?
  "Check if operation auto-commits in given phase. Preserved verbatim
  (business rule, not plumbing)."
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

(defn verdict->disposition
  "Translate Governor verdict to a pre-phase-gate disposition.
  Governor's hard violation -> `:hold` (already rejected, non-negotiable).
  Otherwise -> `:commit` (subject to the phase gate below)."
  [{:keys [approved?]}]
  (if approved? :commit :hold))

(defn gate
  "Phase gate: given the current phase, the request, and the
  pre-phase-gate disposition, return
  {:disposition :commit|:hold|:escalate :reason nil|keyword}.

  - `:hold` (a Governor hard violation) always passes through unchanged.
  - `:commit` is downgraded to `:escalate` unless `phase-auto-commit?`
    says this op auto-commits at this phase."
  [phase request disposition]
  (cond
    (not= :commit disposition)
    {:disposition disposition :reason nil}

    (phase-auto-commit? phase (:operation request))
    {:disposition :commit :reason nil}

    :else
    {:disposition :escalate
     :reason (if (= PHASE_0 phase) :phase-0-readonly :phase-held-for-review)}))
