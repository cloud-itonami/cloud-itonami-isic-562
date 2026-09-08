(ns cateringops.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2 for this repo: it previously had NO
  demo page and no generator at all. This namespace drives the REAL
  actor stack -- `cateringops.operation` (a compiled langgraph
  StateGraph) -> `cateringops.governor` -> `cateringops.phase` ->
  `cateringops.store` -- through a scenario extended from this repo's
  own `cateringops.sim` demo driver (`clojure -M:dev:run`), then renders
  the resulting store and append-only audit ledger.

  NOTHING on the page is hand-typed. Every event id, client name, venue,
  capacity, delivery destination, supply item-type, unit cost, staff
  role, shift time, confidence score, disposition, hold rule and hold
  detail string is read back out of the real store/ledger the run
  produced, or out of the live `cateringops.governor` / `.phase` vars.
  The scenario INPUTS (which op against which target id) are of course
  authored -- that is what a scenario is -- but no OUTPUT is.

  ## Measured, not assumed

  Three things on this page are DERIVED at render time by re-checking
  the real store, so they self-correct if the code is later fixed rather
  than going stale as prose:

  1. `approver-attribution` -- whether the human approver's id actually
     reaches the store's ledger. (Measured: it does NOT. `operation`'s
     `:request-approval` node emits an `:approval-granted` audit fact
     carrying `:by`, but only the `:commit` node's `:committed` fact and
     the `:hold` node's hold fact are appended to the ledger, and
     `commit-fact` takes `:actor` from the request CONTEXT -- the
     requesting coordinator -- never from the approval. The page says so
     plainly instead of printing an approver as though the store held
     one.)
  2. `commit-facts-distinguish-approval?` -- whether a human-approved
     commit is distinguishable from an auto-commit in the ledger.
     (Measured: it is not -- identical key sets.)
  3. `op-vocabulary-coverage` -- what the governor actually does with an
     op keyword outside this actor's five-op vocabulary. (Measured: the
     three HARD checks are target / effect / content checks; none of
     them gates the op keyword itself, so an out-of-vocabulary op with
     `:effect :propose` against a verified target reaches the phase gate
     and auto-commits at phase 3. The page reports the observed
     disposition rather than claiming a hold that does not happen.)

  ## Why this scenario

  It walks the two seeded events, two seeded deliveries, two seeded
  supply requests and two seeded staff shifts through every disposition
  the graph can reach -- phase-3 auto-commit, phase-2 auto-commit,
  phase-1 escalate -> approve, the permanently-escalating safety
  concern, a phase-0 escalate -> REJECT, and then all THREE of the
  CateringOps Governor's HARD checks, each of which holds without ever
  reaching a human:

    1. `:target-not-verified` -- E002 / D002 are registered but NOT
                                 verified; E999 does not exist at all
                                 (the same absent id `cateringops.sim`
                                 uses for this case)
    2. `:effect-not-propose`  -- a deliberately ROGUE advisor injected
                                 over the SAME store, returning
                                 `:effect :actuate`. Unreachable from
                                 the shipped mock advisor (which always
                                 says `:propose`), so injecting one
                                 through the seam `operation/build`
                                 already exposes is the only honest way
                                 to demonstrate the governor's
                                 defense-in-depth.
    3. `:scope-excluded`      -- operator content drifting into
                                 recipe/menu, food-safety or cooking-
                                 technique territory, in EN and in JA,
                                 across three different ops

  It also proves the scope scan's ONE exemption is live:
  `:flag-safety-concern` may legitimately mention an excluded term
  (保健所 / health inspection) without self-blocking.

  ## Determinism

  Every collaborator in the path is pure or deterministic: the mock
  advisor is a `case` over the request, the rogue advisor is a literal,
  and no code in `src/` reads a clock or a RNG. Every collection
  iterated for the page is explicitly sorted here rather than iterated
  in hash order. The page therefore contains NO timestamp and NO
  generated id, and two consecutive runs are byte-identical (verified
  with `cmp`).

  Usage: `clojure -M:dev:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [kotoba.lang.text :as str]
            [jp-go-dds.skin]
            [langgraph.graph :as g]
            [cateringops.advisor :as advisor]
            [cateringops.governor :as governor]
            [cateringops.operation :as operation]
            [cateringops.phase :as phase]
            [cateringops.store :as store]))

(def ^:private coordinator
  "The same operator context this repo's own `sim` driver uses."
  {:actor-id "catering-ops-01" :role :catering-coordinator :phase 3})

(def ^:private approver
  "The same human approver id this repo's own `sim` driver resumes with."
  "coordinator-01")

;; ----------------------------- driving the REAL actor -----------------------------

(defn- record!
  "Append one finished graph run to the ordered run log. `result` is the
  raw `langgraph.graph/run*` return value -- everything rendered from it
  is real actor output."
  [runs tid phase-n request result]
  (swap! runs conj {:tid tid
                    :phase phase-n
                    :request request
                    :proposal (get-in result [:state :proposal])
                    :verdict (get-in result [:state :verdict])
                    :audit (vec (get-in result [:state :audit]))
                    :disposition (get-in result [:state :disposition])})
  result)

(defn- exec!
  "One operation, no human in the loop (auto-commit or HARD hold)."
  ([runs actor tid request] (exec! runs actor tid request (:phase coordinator)))
  ([runs actor tid request phase-n]
   (record! runs tid phase-n request
            (g/run* actor {:request request :context (assoc coordinator :phase phase-n)}
                    {:thread-id tid}))))

(defn- resume!
  "One operation the phase gate escalates, then resumed by a human
  decision. `:audit`'s reducer is `into` and is restored from the
  checkpointer, so the resumed result carries the FULL accumulated audit
  and only it is recorded."
  [runs actor tid request phase-n status]
  (g/run* actor {:request request :context (assoc coordinator :phase phase-n)}
          {:thread-id tid})
  (record! runs tid phase-n request
           (g/run* actor {:approval {:status status :by approver}}
                   {:thread-id tid :resume? true})))

(def ^:private rogue-advisor
  "A deliberately MALFUNCTIONING advisor -- it claims `:effect :actuate`,
  which the shipped `cateringops.advisor/MockAdvisor` can never emit.
  Injected over the SAME store to prove HARD check 2
  (`:effect-not-propose`) fires: a compromised or broken advisor gains
  nothing by trying. See ns docstring."
  (reify advisor/Advisor
    (-advise [_advisor _store request]
      (let [{:keys [operation target-id data]} request]
        {:operation operation
         :target-id target-id
         :effect :actuate
         :data data
         :confidence 0.99
         :cites ["rogue-advisor-self-asserted"]
         :summary (str "ROGUE advisor claiming a direct actuation for "
                       (name operation) " targeting " target-id)}))))

(defn run-demo!
  "Runs a fresh seeded store through the scenario described in the ns
  docstring. Returns `{:db :runs}` -- `:runs` is the ordered log of real
  graph results, `:db` the real store the actor wrote."
  []
  (let [db     (-> (store/mem-store)
                   store/load-demo-events!
                   store/load-demo-deliveries!
                   store/load-demo-supplies!
                   store/load-demo-shifts!)
        actor  (operation/build db)
        ;; same store -- only the advisor is swapped
        broken (operation/build db {:advisor rogue-advisor})
        runs   (atom [])]

    ;; --- phase-3 auto-commit on every seeded, verified target ---
    (exec! runs actor "t01"
           {:operation :schedule-catering-event :target-id "E001"
            :data {:venue "Grand Hall Downtown" :date "2026-08-15" :capacity 150}})
    (exec! runs actor "t02"
           {:operation :coordinate-delivery-status-update :target-id "D001"
            :data {:destination "Grand Hall Downtown" :time "15:00" :status :in-transit}})
    (exec! runs actor "t03"
           {:operation :coordinate-supply-request :target-id "S001"
            :data {:item-type "linens" :quantity 100 :unit-cost 0.50}})
    (exec! runs actor "t04"
           {:operation :schedule-staff-shift-proposal :target-id "ST001"
            :data {:role :server :start-time "2026-08-15 16:00" :end-time "2026-08-15 22:00"}})

    ;; --- phase 2: supply coordination is inside the phase-2 auto set ---
    (exec! runs actor "t05"
           {:operation :coordinate-supply-request :target-id "S002"
            :data {:item-type "glassware" :quantity 300 :unit-cost 1.25}}
           phase/PHASE_2)

    ;; --- phase 1: a staff shift is NOT in the phase-1 auto set -> escalates ---
    (resume! runs actor "t06"
             {:operation :schedule-staff-shift-proposal :target-id "ST002"
              :data {:role :coordinator :start-time "2026-09-20 17:00"
                     :end-time "2026-09-21 01:00"}}
             phase/PHASE_1 :approved)

    ;; --- :flag-safety-concern ALWAYS escalates, even at phase 3 ---
    (resume! runs actor "t07"
             {:operation :flag-safety-concern :target-id "E001"
              :data {:concern "Facility ventilation issue detected"}}
             phase/PHASE_3 :approved)

    ;; --- the scope scan's ONE exemption: a safety concern may name an
    ;;     excluded term without self-blocking (and may be raised against
    ;;     an unverified event -- only schedule/delivery ops gate on that)
    (resume! runs actor "t08"
             {:operation :flag-safety-concern :target-id "E002"
              :data {:concern "保健所の立入検査に備え、搬入動線と休憩室の動線分離を確認したい"}}
             phase/PHASE_3 :approved)

    ;; --- phase 0 is read-only: everything escalates. Here the human REJECTS ---
    (resume! runs actor "t09"
             {:operation :coordinate-supply-request :target-id "S001"
              :data {:item-type "linens" :quantity 100 :unit-cost 0.50}}
             phase/PHASE_0 :rejected)

    ;; --- HARD check 1: target registered but NOT verified / absent entirely ---
    (exec! runs actor "t10"
           {:operation :schedule-catering-event :target-id "E002"
            :data {:venue "Sunset Manor" :date "2026-09-20" :capacity 200}})
    (exec! runs actor "t11"
           {:operation :coordinate-delivery-status-update :target-id "D002"
            :data {:destination "Sunset Manor" :time "17:30" :status :scheduled}})
    (exec! runs actor "t12"
           {:operation :schedule-catering-event :target-id "E999"
            :data {:venue "Grand Hall Downtown" :date "2026-08-15" :capacity 150}})

    ;; --- HARD check 2: a rogue advisor claiming a real-world actuation ---
    (exec! runs broken "t13"
           {:operation :schedule-catering-event :target-id "E001"
            :data {:venue "Grand Hall Downtown" :date "2026-08-15" :capacity 150}})
    ;; ...and one that trips TWO hard rules at once
    (exec! runs broken "t14"
           {:operation :coordinate-delivery-status-update :target-id "D002"
            :data {:destination "Sunset Manor" :time "17:30" :status :scheduled}})

    ;; --- HARD check 3: operator content drifting out of scope (EN, JA, 3 ops) ---
    (exec! runs actor "t15"
           {:operation :coordinate-supply-request :target-id "S001"
            :data {:item-type "linens" :quantity 100 :unit-cost 0.50
                   :operator-note "hold the linens until the chef signs off the new recipe for the tasting menu"}})
    (exec! runs actor "t16"
           {:operation :schedule-catering-event :target-id "E001"
            :data {:venue "Grand Hall Downtown" :date "2026-08-15" :capacity 150
                   :operator-note "食品安全の観点から当日の調理法を変更してよいか確認したい"}})
    (exec! runs actor "t17"
           {:operation :coordinate-delivery-status-update :target-id "D001"
            :data {:destination "Grand Hall Downtown" :time "15:00" :status :in-transit
                   :operator-note "menu tasting samples ride along with this delivery"}})

    ;; --- MEASUREMENT probe: an op keyword outside the five-op vocabulary.
    ;;     Rendered as an observation, never as a claimed hold. See
    ;;     `op-vocabulary-coverage`.
    (exec! runs actor "t18"
           {:operation :direct-kitchen-command :target-id "E001" :data {}})

    {:db db :runs @runs}))

;; ----------------------------- rendering helpers -----------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

(defn- kw-str [v] (if (keyword? v) (name v) (str v)))

(defn- code [v] (str "<code>" (esc v) "</code>"))

(defn- n-cell [v] (str "<span class=\"num\">" (esc v) "</span>"))

(defn- dash [] "<span class=\"muted\">&mdash;</span>")

(defn- yes-no [v]
  (if (true? v)
    "<span class=\"ok\">yes</span>"
    "<span class=\"critical\">no</span>"))

(defn- fact-of [audit t] (first (filter #(= t (:t %)) audit)))

(defn- row [& cells]
  (str "        <tr>" (str/join (map #(str "<td>" % "</td>") cells)) "</tr>"))

(defn- rows [xs] (str/join "\n" xs))

(defn- section [title lead headers body-rows]
  (str "  <section class=\"card\">\n"
       "    <h2>" title "</h2>\n"
       "    <p class=\"muted\">" lead "</p>\n"
       "    <table>\n"
       "      <thead><tr>" (str/join (map #(str "<th>" % "</th>") headers)) "</tr></thead>\n"
       "      <tbody>\n" (rows body-rows) "\n      </tbody>\n"
       "    </table>\n"
       "  </section>\n"))

;; ----------------------------- derived measurements -----------------------------

(defn- holds
  "The HARD `:governor-hold` facts the run actually wrote to the ledger.
  A human rejection lands as `:approval-rejected` and is deliberately
  NOT counted here -- it is a human decision, not a governor check."
  [db]
  (filterv #(= :governor-hold (:t %)) (store/ledger db)))

(defn- committed [db] (filterv #(= :committed (:t %)) (store/ledger db)))

(defn- outcome
  "Classify one real run from its own audit trail. Never from a literal."
  [{:keys [audit disposition]}]
  (let [hold (fact-of audit :governor-hold)
        rej  (fact-of audit :approval-rejected)]
    (cond
      hold {:kind :hard-hold :violations (:violations hold)}

      rej {:kind :rejected
           :reason (:reason (fact-of audit :approval-requested))
           :by approver}

      (fact-of audit :approval-granted)
      {:kind :approved
       :reason (:reason (fact-of audit :approval-requested))
       :by (:by (fact-of audit :approval-granted))}

      (fact-of audit :approval-requested)
      {:kind :awaiting :reason (:reason (fact-of audit :approval-requested))}

      (= :commit disposition) {:kind :auto-commit}
      :else {:kind :other})))

(defn- deep-key-names
  "Every key name appearing anywhere in a nested structure, as strings.
  Used to ask the ledger what it actually holds instead of assuming."
  [x]
  (cond
    (map? x) (into (into #{} (map kw-str) (keys x))
                   (mapcat deep-key-names (vals x)))
    (sequential? x) (into #{} (mapcat deep-key-names x))
    :else #{}))

(defn- approver-attribution
  "DERIVED honest disclosure about where the human approver's id actually
  lives after a `:request-approval` handoff.

  Re-checked against the real store at render time so the claim tracks
  the code: if `operation` is later changed to persist the approver,
  this page starts saying so on the next build without an edit."
  [db runs]
  (let [ledger    (store/ledger db)
        names     (deep-key-names ledger)
        approver? #(contains? #{"approved-by" "approved_by" "approver" "by" "approved-by-id"}
                              (str/lower %))]
    {:granted      (vec (sort (into #{} (keep #(:by (fact-of (:audit %) :approval-granted))) runs)))
     :on-ledger?   (boolean (some approver? names))
     :granted-fact-on-ledger? (boolean (some #(= :approval-granted (:t %)) ledger))
     ;; what the ledger DOES record on a commit: the requesting actor, from
     ;; the request context -- not the approver.
     :actors       (vec (sort (into #{} (keep :actor) ledger)))}))

(defn- commit-facts-distinguish-approval?
  "DERIVED: does the ledger let a reader tell a human-approved commit
  from an unattended auto-commit? Compares the key sets of the
  `:committed` facts produced by runs that really went through
  `:request-approval` against those that did not. Returns
  `{:approved-keys :auto-keys :distinguishable?}`."
  [db runs]
  (let [by-run  (into {} (for [r runs] [[(:operation (:request r)) (:target-id (:request r))]
                                        (:kind (outcome r))]))
        classed (group-by #(get by-run [(:op %) (:subject %)]) (committed db))
        keyset  (fn [fs] (into (sorted-set) (mapcat (comp (partial map kw-str) keys)) fs))
        ak      (keyset (get classed :approved))
        uk      (keyset (get classed :auto-commit))]
    {:approved-keys ak
     :auto-keys uk
     :distinguishable? (and (seq ak) (seq uk) (not= ak uk))}))

(defn- op-vocabulary-coverage
  "DERIVED: what the governor actually did with the out-of-vocabulary op
  keyword this scenario probed with. Reports the OBSERVED disposition
  rather than asserting a hold that may not happen. `known-ops` is the
  set of ops `cateringops.phase/phase-auto-commit?` names explicitly at
  some phase -- probed here by asking the live fn, so the vocabulary
  cannot drift away from the code."
  [runs]
  (let [all-ops   (into (sorted-set) (map (comp :operation :request)) runs)
        ;; an op is "in the vocabulary" iff some phase treats it specially:
        ;; phase 3 auto-commits everything EXCEPT :flag-safety-concern, so the
        ;; discriminating question is whether phases 1 or 2 name it.
        named?    (fn [op] (boolean (or (phase/phase-auto-commit? phase/PHASE_1 op)
                                        (phase/phase-auto-commit? phase/PHASE_2 op)
                                        (not (phase/phase-auto-commit? phase/PHASE_3 op)))))
        unknown   (into (sorted-set) (remove named?) all-ops)]
    {:all-ops all-ops
     :unknown unknown
     :observed (vec (for [r runs
                          :when (contains? unknown (:operation (:request r)))]
                      {:op (:operation (:request r))
                       :subject (:target-id (:request r))
                       :confidence (:confidence (:proposal r))
                       :violations (:violations (:verdict r))
                       :outcome (:kind (outcome r))}))}))

(defn- matched-scope-terms
  "Which of `governor/scope-excluded-terms` actually occur in a real
  proposal, recomputed exactly the way `governor/scope-excluded?` does
  (lower-cased `str` of the whole proposal map). Vector order follows the
  governor's own term vector, so it is deterministic."
  [proposal]
  (let [s (str/lower (str proposal))]
    (vec (filter #(str/includes? s (str/lower %)) governor/scope-excluded-terms))))

;; ----------------------------- sections (all derived) -----------------------------

(defn- outcome-cell [o]
  (case (:kind o)
    :hard-hold (str "<span class=\"critical\">HARD hold &middot; "
                    (esc (str/join ", " (map (comp kw-str :rule) (:violations o))))
                    "</span>")
    :approved (str "<span class=\"ok\">escalated (" (esc (kw-str (:reason o)))
                   ") &rarr; approved</span>")
    :rejected (str "<span class=\"critical\">escalated (" (esc (kw-str (:reason o)))
                   ") &rarr; REJECTED by human</span>")
    :awaiting (str "<span class=\"warn\">awaiting human approval &middot; "
                   (esc (kw-str (:reason o))) "</span>")
    :auto-commit "<span class=\"ok\">auto-commit (governor-clean)</span>"
    "<span class=\"muted\">in progress</span>"))

(defn- detail-cell [o]
  (case (:kind o)
    :hard-hold (esc (str/join " / " (keep :detail (:violations o))))
    :approved "<span class=\"muted\">human in the loop before commit</span>"
    :rejected "<span class=\"muted\">held; no commit fact written</span>"
    :awaiting "<span class=\"muted\">paused at :request-approval</span>"
    (dash)))

(defn- run-rows [runs]
  (for [{:keys [tid phase request proposal] :as r} runs
        :let [o (outcome r)]]
    (row (code tid)
         (n-cell phase)
         (code (kw-str (:operation request)))
         (code (:target-id request))
         (if proposal (n-cell (:confidence proposal)) (dash))
         (outcome-cell o)
         (detail-cell o))))

(defn- hold-rows [db]
  (for [{:keys [op subject violations confidence]} (holds db)
        v violations]
    (row (code (kw-str (:rule v)))
         (code (kw-str op))
         (code subject)
         (n-cell confidence)
         (if (:detail v) (esc (:detail v)) (dash)))))

(defn- event-rows [db]
  (for [{:keys [id name client-name date venue capacity registered? verified? status]}
        (sort-by :id (store/all-events db))]
    (row (code id)
         (esc name)
         (esc client-name)
         (esc date)
         (esc venue)
         (n-cell capacity)
         (yes-no registered?)
         (yes-no verified?)
         (code (kw-str status)))))

(defn- delivery-rows [db]
  (for [{:keys [id event-id destination scheduled-time registered? verified? status]}
        (sort-by :id (store/all-deliveries db))]
    (row (code id)
         (code event-id)
         (esc destination)
         (esc scheduled-time)
         (yes-no registered?)
         (yes-no verified?)
         (code (kw-str status)))))

(defn- referenced-ids
  "Every target id this run actually referenced, sorted. Used to look
  supplies and shifts back out of the SSoT -- `cateringops.store`'s
  protocol has no `all-supplies`/`all-shifts`, so the honest way to
  build those directories is to ask the store about the ids the run
  really touched rather than to hard-code a list here."
  [runs]
  (into (sorted-set) (map (comp :target-id :request)) runs))

(defn- supply-rows [db runs]
  (for [id (referenced-ids runs)
        :let [s (store/lookup-supply db id)]
        :when s]
    (row (code (:id s))
         (code (:event-id s))
         (esc (:item-type s))
         (n-cell (:quantity s))
         (n-cell (:unit-cost s))
         (code (kw-str (:status s))))))

(defn- shift-rows [db runs]
  (for [id (referenced-ids runs)
        :let [s (store/lookup-shift db id)]
        :when s]
    (row (code (:id s))
         (code (:event-id s))
         (code (kw-str (:role s)))
         (esc (:start-time s))
         (esc (:end-time s))
         (code (kw-str (:status s))))))

(defn- phase-rows
  "The auto-commit matrix, DERIVED by actually calling
  `phase/phase-auto-commit?` for every (phase, op) pair this run
  exercised -- not a prose description that could drift from the code."
  [runs]
  (for [op (:all-ops (op-vocabulary-coverage runs))]
    (apply row (code (kw-str op))
           (for [p [phase/PHASE_0 phase/PHASE_1 phase/PHASE_2 phase/PHASE_3]]
             (if (phase/phase-auto-commit? p op)
               "<span class=\"ok\">auto-commit</span>"
               "<span class=\"warn\">escalate</span>")))))

(defn- scope-term-hits
  "DERIVED: for each scope-exclusion term, whether this run's real
  proposals matched it, and whether that match actually blocked. A term
  matched inside a `:flag-safety-concern` proposal is EXEMPT -- the
  governor's one carve-out -- so the two cases are reported separately
  rather than collapsed into one column that would make a live exemption
  look like an absent term. Returns `{:blocked #{} :exempt #{}}`."
  [runs]
  (reduce (fn [acc {:keys [proposal verdict]}]
            (let [hit (matched-scope-terms proposal)]
              (cond
                (empty? hit) acc
                (some #(= :scope-excluded (:rule %)) (:violations verdict))
                (update acc :blocked into hit)
                :else (update acc :exempt into hit))))
          {:blocked (sorted-set) :exempt (sorted-set)}
          runs))

(defn- scope-term-rows
  "The scope-exclusion vocabulary read straight out of
  `governor/scope-excluded-terms-en` and `-ja`, with a DERIVED column
  saying what this run's real proposals actually did with each term."
  [runs]
  (let [{:keys [blocked exempt]} (scope-term-hits runs)
        cell (fn [t]
               (cond
                 (contains? blocked t) "<span class=\"critical\">matched &rarr; HARD hold</span>"
                 (contains? exempt t) "<span class=\"warn\">matched &rarr; exempt (:flag-safety-concern)</span>"
                 :else (dash)))]
    (concat
     (for [t governor/scope-excluded-terms-en] (row (code t) "en" (cell t)))
     (for [t governor/scope-excluded-terms-ja] (row (code t) "ja" (cell t))))))

(defn- ledger-rows [db]
  (for [{:keys [t op actor subject disposition basis violations summary]} (store/ledger db)]
    (row (case t
           :committed "<span class=\"ok\">committed</span>"
           :governor-hold "<span class=\"critical\">governor-hold</span>"
           :approval-rejected "<span class=\"critical\">approval-rejected</span>"
           (esc (kw-str t)))
         (code (kw-str op))
         (code subject)
         (code actor)
         (esc (kw-str disposition))
         (if (seq violations)
           (esc (str/join ", " (map (comp kw-str :rule) violations)))
           (esc (str/join " ; " (map kw-str basis))))
         (if summary (esc summary) (dash)))))

;; ----------------------------- derived prose sections -----------------------------

(defn- attribution-section
  "Renders the approver-attribution disclosure from the DERIVED facts, so
  the claim tracks the code."
  [{:keys [granted on-ledger? granted-fact-on-ledger? actors]} dist]
  (str "  <section class=\"card\">\n"
       "    <h2>Approver attribution &mdash; what the ledger does and does not hold</h2>\n"
       "    <p class=\"muted\">Re-measured against the real store at render time: every fact on the "
       "append-only ledger is scanned for an approver key at any depth, and for an "
       "<code>:approval-granted</code> fact. This disclosure is computed, not asserted, so it "
       "self-corrects if <code>cateringops.operation</code> is later changed to persist the "
       "approver.</p>\n"
       "    <table>\n"
       "      <thead><tr><th>Question</th><th>Measured answer</th></tr></thead>\n"
       "      <tbody>\n"
       (rows [(row "approver id(s) on this run&rsquo;s <code>:approval-granted</code> audit facts"
                   (if (seq granted) (str/join " " (map code granted)) (dash)))
              (row "an approver key survives anywhere on the store&rsquo;s ledger"
                   (yes-no on-ledger?))
              (row "an <code>:approval-granted</code> fact is itself appended to the ledger"
                   (yes-no granted-fact-on-ledger?))
              (row "what the ledger&rsquo;s <code>:actor</code> field actually contains"
                   (str/join " " (map code actors)))
              (row "a human-approved commit is distinguishable from an auto-commit in the ledger"
                   (yes-no (:distinguishable? dist)))])
       "\n      </tbody>\n    </table>\n"
       "    <p>"
       (cond
         (empty? granted)
         "This run produced no human approval, so there is no approver to attribute."

         on-ledger?
         (str "The approver survives on the ledger. The <em>Operation dispositions</em> table "
              "above can therefore be read directly against the stored record.")

         :else
         (str "<strong>Audit only &mdash; not retained in the record.</strong> "
              "<code>operation</code>&rsquo;s <code>:request-approval</code> node really does emit an "
              "<code>:approval-granted</code> fact carrying <code>:by "
              (esc (pr-str (first granted))) "</code>, but that fact stays on the run&rsquo;s "
              "in-memory <code>:audit</code> channel (recoverable only from the checkpointer): the "
              "<code>:commit</code> node appends nothing but its own <code>:committed</code> fact, "
              "and <code>commit-fact</code> takes <code>:actor</code> from the request "
              "<em>context</em> &mdash; the requesting coordinator "
              (str/join " " (map code actors))
              " &mdash; never from the approval. The same is true of a rejection: the "
              "<code>:approval-rejected</code> fact records the rule <code>:approver-rejected</code> "
              "but not who rejected. "
              (if (:distinguishable? dist)
                ""
                (str "Because the <code>:committed</code> facts written by an approved run and by an "
                     "unattended auto-commit carry <em>identical</em> key sets ("
                     (str/join " " (map code (:approved-keys dist)))
                     "), a reader of the ledger alone cannot tell the two apart. "))
              "So the &ldquo;approved&rdquo; outcomes above are joined back from each run&rsquo;s own "
              "audit trail, which really does carry the approver &mdash; this page states the gap "
              "plainly rather than printing an approver as though the ledger held one."))
       "</p>\n"
       "  </section>\n"))

(defn- coverage-section
  "Renders the op-vocabulary measurement. Reports what was OBSERVED."
  [{:keys [observed]}]
  (str "  <section class=\"card\">\n"
       "    <h2>Op-vocabulary coverage &mdash; measured, not assumed</h2>\n"
       "    <p class=\"muted\">This scenario deliberately submits one op keyword outside the "
       "five-op vocabulary <code>cateringops.phase</code> names, and reports what the real stack "
       "did with it. The op set below is derived by probing the live "
       "<code>phase/phase-auto-commit?</code>, so it cannot drift from the code.</p>\n"
       (if (empty? observed)
         "    <p>No out-of-vocabulary op was submitted by this run.</p>\n"
         (str "    <table>\n"
              "      <thead><tr><th>Op submitted</th><th>Target</th><th>Advisor confidence</th>"
              "<th>Governor violations</th><th>Observed disposition</th></tr></thead>\n"
              "      <tbody>\n"
              (rows (for [{:keys [op subject confidence violations outcome]} observed]
                      (row (code (kw-str op))
                           (code subject)
                           (n-cell confidence)
                           (if (seq violations)
                             (esc (str/join ", " (map (comp kw-str :rule) violations)))
                             "<span class=\"critical\">none</span>")
                           (if (= :auto-commit outcome)
                             "<span class=\"critical\">auto-committed</span>"
                             (str "<span class=\"ok\">" (esc (kw-str outcome)) "</span>")))))
              "\n      </tbody>\n    </table>\n"
              "    <p>"
              (if (some #(= :auto-commit (:outcome %)) observed)
                (str "<strong>Finding.</strong> The three HARD checks in "
                     "<code>cateringops.governor</code> are a <em>target</em> check, an "
                     "<em>effect</em> check and a <em>content</em> check &mdash; none of them gates "
                     "the op keyword itself against a closed allowlist. An out-of-vocabulary op that "
                     "carries <code>:effect :propose</code>, names a registered-and-verified target "
                     "and uses no excluded term therefore raises <em>no</em> violation, and "
                     "<code>phase-auto-commit?</code>&rsquo;s phase-3 branch returns <code>true</code> "
                     "for everything except <code>:flag-safety-concern</code>, so it auto-commits "
                     "&mdash; with an advisor confidence of "
                     (esc (str/join ", " (map (comp str :confidence) observed)))
                     ", because <code>advisor-score</code> has no branch for it and this governor has "
                     "no confidence floor. This page reports that rather than claiming a hold that "
                     "does not happen. Sibling actors in this fleet close the same gap with a "
                     "<code>closed-op-allowlist</code> HARD check.")
                (str "The out-of-vocabulary op did not auto-commit: "
                     (esc (str/join ", " (map (comp kw-str :outcome) observed))) "."))
              "</p>\n"))
       "  </section>\n"))

;; ----------------------------- the document -----------------------------

(defn render
  "Renders the whole operator console from a `run-demo!` result. Takes no
  clock and no seed: identical input -> identical bytes."
  [{:keys [db runs]}]
  (let [ledger   (vec (store/ledger db))
        outcomes (mapv outcome runs)
        hs       (holds db)
        cm       (committed db)
        cnt      (fn [k] (count (filterv #(= k (:kind %)) outcomes)))
        att      (approver-attribution db runs)
        dist     (commit-facts-distinguish-approval? db runs)
        cov      (op-vocabulary-coverage runs)]
    (str
     "<!DOCTYPE html>\n<html lang=\"en\"><head><meta charset=\"utf-8\">"
     "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1, viewport-fit=cover\">"
     "<meta name=\"color-scheme\" content=\"light\">"
     "<title>cloud-itonami-isic-562 &middot; event catering — operator console</title>"
     "<style>" (jp-go-dds.skin/dds+skin) "</style></head><body>\n"

     "<header class=\"bar\">\n"
     "  <h1>Event catering (ISIC 562) &mdash; Operator Console</h1>\n"
     "</header>\n"
     "<p><span class=\"badge\">read-only sample</span> "
     "<span class=\"badge\">governor-gated</span> "
     "<span class=\"badge\">coordination-only &middot; every effect is :propose</span> "
     "<span class=\"badge\">no food-safety authority</span></p>\n"
     "<p class=\"subtitle\">Generated at build time by <code>cateringops.render-html</code> "
     "(<code>clojure -M:dev:render-html</code>) by actually running the compiled "
     "<code>cateringops.operation</code> StateGraph over a freshly seeded store. Every value below "
     "was read back out of that run or out of the live <code>governor</code> / <code>phase</code> "
     "vars &mdash; there is no mock markup on this page, and no timestamp, so successive "
     "regenerations are byte-identical.</p>\n"

     "<main>\n"

     (section "Run summary"
              "Counted from the real audit ledger and the real graph results, not asserted."
              ["Measure" "Count"]
              [(row "events in the SSoT" (n-cell (count (store/all-events db))))
               (row "deliveries in the SSoT" (n-cell (count (store/all-deliveries db))))
               (row "graph runs in this scenario" (n-cell (count runs)))
               (row "<span class=\"ok\">auto-commits (governor-clean)</span>" (n-cell (cnt :auto-commit)))
               (row "<span class=\"ok\">escalated &rarr; human-approved commits</span>" (n-cell (cnt :approved)))
               (row "<span class=\"critical\">escalated &rarr; human-rejected holds</span>" (n-cell (cnt :rejected)))
               (row "<span class=\"critical\">HARD governor holds (never reach a human)</span>" (n-cell (count hs)))
               (row "distinct HARD rules exercised"
                    (n-cell (count (into #{} (mapcat (fn [h] (map :rule (:violations h)))) hs))))
               (row "committed facts in the audit ledger" (n-cell (count cm)))
               (row "audit-ledger facts total" (n-cell (count ledger)))
               (row "ops exercised" (n-cell (count (:all-ops cov))))
               (row "scope-exclusion terms in the governor&rsquo;s vocabulary"
                    (n-cell (str (count governor/scope-excluded-terms-en) " en + "
                                 (count governor/scope-excluded-terms-ja) " ja")))])

     (section "Operation dispositions (this run)"
              "One row per graph run. The outcome and the hold reason are classified from each run's
               own audit trail; the detail text is the governor's own message, verbatim. The phase
               column is the rollout phase the request was submitted at &mdash; the same request can
               auto-commit at one phase and escalate at another."
              ["Thread" "Phase" "Op" "Target" "Advisor confidence" "Outcome" "Governor detail"]
              (run-rows runs))

     (section "HARD governor holds"
              "One row per violated rule on every <code>:governor-hold</code> fact the run really
               wrote to the ledger. These are un-overridable: they never reach the
               <code>:request-approval</code> interrupt, so no human can wave them through. Note that
               a single proposal can trip more than one rule at once."
              ["Rule" "Op" "Target" "Advisor confidence" "Governor detail (verbatim)"]
              (hold-rows db))

     (attribution-section att dist)

     (coverage-section cov)

     (section "Phase gate &mdash; auto-commit matrix"
              "Derived by actually calling <code>cateringops.phase/phase-auto-commit?</code> for every
               (phase, op) pair this run exercised. Phase 0 is read-only: everything escalates.
               <code>:flag-safety-concern</code> never auto-commits at any phase. A cell reading
               &ldquo;escalate&rdquo; means the request pauses at the <code>:request-approval</code>
               interrupt for a human &mdash; it does not mean the governor held it."
              ["Op" "Phase 0" "Phase 1" "Phase 2" "Phase 3"]
              (phase-rows runs))

     (section "Scope-exclusion vocabulary"
              "Read straight out of <code>governor/scope-excluded-terms-en</code> and
               <code>-ja</code>. Any proposal whose printed form contains one of these is a HARD,
               permanent block &mdash; this actor coordinates catering logistics and has no
               food-safety, health-inspection, recipe/menu or cooking-technique authority. The one
               exemption is <code>:flag-safety-concern</code>, which must be able to name a hazard
               without self-blocking. The last column is recomputed here from this run's real
               proposals using the governor's own scan, and separates a term that BLOCKED from one
               that was matched but exempted &mdash; run <code>t08</code> raises a real concern
               naming 保健所 and commits, which is the exemption working, not the term being absent."
              ["Term" "Lang" "What this run did with it"]
              (scope-term-rows runs))

     (section "Event register"
              "The SSoT after the run. <code>registered?</code> AND <code>verified?</code> are the
               ground truth the CateringOps Governor re-derives independently &mdash; never the
               advisor's own confidence. A registered-but-unverified event is exactly the state that
               HARD-holds a scheduling proposal."
              ["Id" "Event" "Client" "Date" "Venue" "Capacity" "Registered?" "Verified?" "Status"]
              (event-rows db))

     (section "Delivery register"
              "Same two-flag verification gate as events. <code>:coordinate-delivery-status-update</code>
               is the other op that requires it."
              ["Id" "Event" "Destination" "Scheduled" "Registered?" "Verified?" "Status"]
              (delivery-rows db))

     (section "Supply requests referenced by this run"
              "Non-food supply coordination (equipment, linens, cleaning supplies). Looked back out of
               the SSoT by id &mdash; <code>cateringops.store</code>'s protocol exposes
               <code>lookup-supply</code> but no <code>all-supplies</code>, so this directory is built
               from the target ids the run actually referenced rather than from a list typed here."
              ["Id" "Event" "Item type" "Quantity" "Unit cost" "Status"]
              (supply-rows db runs))

     (section "Staff shifts referenced by this run"
              "Shift PROPOSALS &mdash; this actor proposes a roster, it never assigns or dispatches
               anyone. Looked back out of the SSoT by id, as above."
              ["Id" "Event" "Role" "Start" "End" "Status"]
              (shift-rows db runs))

     (section "Audit ledger"
              "The append-only decision facts the run actually wrote to
               <code>cateringops.store</code>, in append order. This log IS the durable record: this
               actor has no separate commit-record entity, so an event's whole operating history is a
               query over these facts."
              ["Fact" "Op" "Target" "Actor" "Disposition" "Basis / violated rule" "Summary"]
              (ledger-rows db))

     "</main>\n"
     "<footer>\n"
     "  <p>This actor NEVER makes a food-safety, health-inspection, recipe/menu or food-handling\n"
     "  decision &mdash; that authority belongs exclusively to the certified food-safety operator and\n"
     "  the public health authority. Every proposal carries <code>:effect :propose</code>; committing\n"
     "  one means a coordination fact was logged, never that food was prepared, served or cleared as\n"
     "  safe.</p>\n"
     "  <p>Regenerate: <code>clojure -M:dev:render-html</code></p>\n"
     "</footer>\n"
     "</body></html>\n")))

;; ----------------------------- entry point -----------------------------

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        {:keys [db runs] :as result} (run-demo!)
        hs (holds db)
        cm (committed db)]
    ;; A console that shows no real HARD hold is not evidence of a governor.
    (when (empty? hs)
      (throw (ex-info (str "no :governor-hold fact on the ledger — refusing to write a console "
                           "that shows no real hold")
                      {:ledger-facts (count (store/ledger db))})))
    ;; ...and one that shows no commit at all is not evidence of an actor.
    (when (empty? cm)
      (throw (ex-info (str "no :committed fact on the ledger — refusing to write a console "
                           "that shows no clean path")
                      {:ledger-facts (count (store/ledger db))})))
    (let [f (java.io.File. ^String out)]
      (when-let [p (.getParentFile f)] (.mkdirs p))
      (spit f (render result)))
    (println "wrote" out
             (str "(" (count (store/ledger db)) " ledger facts, "
                  (count hs) " HARD holds, "
                  (count (into #{} (mapcat (fn [h] (map :rule (:violations h)))) hs)) " distinct HARD rules, "
                  (count cm) " commits, "
                  (count runs) " graph runs)"))))
