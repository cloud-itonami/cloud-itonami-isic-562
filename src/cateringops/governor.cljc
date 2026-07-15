(ns cateringops.governor
  "Three HARD, un-overridable Governor checks")

;; --- Scope Exclusion Check ---

(defn scope-excluded?
  "Check if proposal mentions food-safety, health-inspection, recipe, or food-handling.
   Returns true if excluded (should be rejected)."
  [proposal]
  (let [proposal-str (str proposal)
        en-excluded-patterns
        ["food-safety" "health-inspection" "health-code" "recipe" "menu"
         "food-handling" "food preparation" "cooking technique" "preparation method"
         "safety-authority" "override" "certification"]
        ja-excluded-patterns
        ["食品安全" "調理法" "調理技法" "衛生管理" "保健所" "レシピ" "メニュー"
         "食中毒" "健康診断"]
        all-patterns (concat en-excluded-patterns ja-excluded-patterns)
        has-exclusion (some (fn [pattern]
                             (and pattern (clojure.string/includes? (clojure.string/lower-case proposal-str)
                                                                    (clojure.string/lower-case pattern))))
                          all-patterns)

        ;; Exception: :flag-safety-concern should not self-block even if it mentions safety
        operation (:operation proposal)
        is-safety-concern (= operation :flag-safety-concern)]

    (and has-exclusion (not is-safety-concern))))

;; --- Hard Check 1: Event/Delivery Unverified ---

(defn check-verified
  "Hard Check 1: target must be :registered? and :verified?"
  [proposal store]
  (let [operation (:operation proposal)
        target-id (:target-id proposal)]
    ;; Only event/delivery-specific ops need verification
    (case operation
      (:schedule-catering-event :coordinate-delivery-status-update)
      (if-let [event (or (get @(:events store) target-id)
                        (get @(:deliveries store) target-id))]
        (and (:registered? event) (:verified? event))
        false)  ;; No event found = fail
      ;; Other operations don't require target verification
      true)))

;; --- Hard Check 2: Effect Must Be :propose ---

(defn check-effect
  "Hard Check 2: effect must be :propose"
  [proposal]
  (= (:effect proposal) :propose))

;; --- Hard Check 3: Scope Exclusion ---

(defn check-scope
  "Hard Check 3: reject scope-excluded proposals"
  [proposal]
  (not (scope-excluded? proposal)))

;; --- Governor Decision ---

(defn governor-decision
  "Apply all three HARD checks. Return true if proposal PASSES (can proceed)."
  [proposal store]
  (let [check1 (check-verified proposal store)
        check2 (check-effect proposal)
        check3 (check-scope proposal)]
    (and check1 check2 check3)))

(defn propose-operation
  "Wrap a proposal with :effect :propose and run through Governor.
   Returns {:approved? bool :reason str}"
  [operation target-id data store]
  (let [proposal {:operation operation
                  :target-id target-id
                  :effect :propose
                  :data data}
        approved? (governor-decision proposal store)]
    {:approved? approved?
     :proposal proposal
     :reason (cond
              (not (check-effect proposal))
              "Effect must be :propose"
              (not (check-scope proposal))
              "Scope exclusion: food-safety, recipe, or food-handling"
              (not (check-verified proposal store))
              "Event/delivery not registered and verified"
              :else
              "Approved")}))
