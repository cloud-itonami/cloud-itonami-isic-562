(ns cateringops.governor
  "Three HARD, un-overridable Governor checks for the event-catering
  operations coordinator. The advisor has no notion of:
    - whether the event/delivery a proposal targets is actually
      registered AND verified in the Store
    - whether a proposal is a real actuation (`:effect :propose` only)
    - whether a proposal's content has drifted into food-safety,
      health-inspection, recipe/menu-content, or food-handling-technique
      territory -- permanently out of this actor's coordination-only scope

  This MUST be a separate system able to *reject* a proposal and fall
  back to HOLD. `check` re-derives every fact independently from the
  Store (via `cateringops.store`'s protocol, never by reaching into a
  backend's private representation), so a `MemStore` and a
  `DatomicStore` are governed identically.

  Hard violations (always HOLD, no override, permanent):
    1. Target (event/delivery) not registered AND verified -- only
       `:schedule-catering-event` / `:coordinate-delivery-status-update`
       require this; other ops don't reference a verifiable target.
    2. Proposal `:effect` is not `:propose` (no direct execution, ever)
    3. Proposal content touches food-safety, health-inspection, recipe/
       menu-content, or food-handling-technique territory (EN+JA
       substring scan) -- `:flag-safety-concern` is exempted from this
       scan (a legitimate facility/sanitation safety concern is allowed
       to mention \"safety\" without self-blocking)."
  (:require [clojure.string :as str]
            [cateringops.store :as store]))

;; ----------------------------- scope exclusion -----------------------------

(def scope-excluded-terms-en
  ["food-safety" "health-inspection" "health-code" "recipe" "menu"
   "food-handling" "food preparation" "cooking technique" "preparation method"
   "safety-authority" "override" "certification"])

(def scope-excluded-terms-ja
  ["食品安全" "調理法" "調理技法" "衛生管理" "保健所" "レシピ" "メニュー"
   "食中毒" "健康診断"])

(def scope-excluded-terms (into scope-excluded-terms-en scope-excluded-terms-ja))

(defn scope-excluded?
  "Check if proposal mentions food-safety, health-inspection, recipe, or
  food-handling. Returns true if excluded (should be rejected).
  `:flag-safety-concern` is exempted -- it should not self-block even
  when it legitimately mentions \"safety\"."
  [proposal]
  (let [proposal-str (str/lower-case (str proposal))
        has-exclusion (some #(str/includes? proposal-str (str/lower-case %))
                             scope-excluded-terms)
        is-safety-concern (= :flag-safety-concern (:operation proposal))]
    (boolean (and has-exclusion (not is-safety-concern)))))

;; ----------------------------- checks -----------------------------

(defn- verification-violations
  "Hard Check 1: `:schedule-catering-event` and
  `:coordinate-delivery-status-update` target an event or delivery that
  MUST be independently `:registered?` AND `:verified?` in the Store.
  Other ops don't reference a verifiable target."
  [{:keys [operation target-id]} st]
  (case operation
    (:schedule-catering-event :coordinate-delivery-status-update)
    (let [target (or (store/lookup-event st target-id)
                      (store/lookup-delivery st target-id))]
      (when-not (and target (:registered? target) (:verified? target))
        [{:rule :target-not-verified
          :detail (str "target-id " (pr-str target-id) " は未登録または未検証のイベント/配送 -- 提案を進められない")}]))
    nil))

(defn- effect-violations
  "Hard Check 2: `:effect` must always be `:propose` -- this actor
  never directly executes anything."
  [proposal]
  (when-not (= :propose (:effect proposal))
    [{:rule :effect-not-propose
      :detail (str ":effect は :propose のみ許可されるが " (pr-str (:effect proposal)) " が提案された")}]))

(defn- scope-violations
  "Hard Check 3: content touching food-safety/health-inspection/recipe/
  food-handling territory is a HARD, permanent block."
  [proposal]
  (when (scope-excluded? proposal)
    [{:rule :scope-excluded
      :detail "提案内容が食品安全・保健所検査・レシピ/メニュー・調理技法の対象範囲外領域に抵触"}]))

(defn check
  "Censors a CateringOps proposal against the three HARD Governor
  checks. Returns {:ok? bool :approved? bool :violations [..] :confidence n}."
  [proposal st]
  (let [violations (into [] (concat (verification-violations proposal st)
                                     (effect-violations proposal)
                                     (scope-violations proposal)))]
    {:ok? (empty? violations)
     :approved? (empty? violations)
     :violations violations
     :confidence (:confidence proposal 0.0)}))

;; Individual named checks kept for direct testing / backward-compatible
;; readability (mirrors this actor's own README's "Three HARD Checks").

(defn check-verified [proposal st] (empty? (verification-violations proposal st)))
(defn check-effect [proposal] (empty? (effect-violations proposal)))
(defn check-scope [proposal] (not (scope-excluded? proposal)))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t :governor-hold
   :op (:operation request)
   :actor (:actor-id context)
   :subject (:target-id request)
   :disposition :hold
   :basis (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
