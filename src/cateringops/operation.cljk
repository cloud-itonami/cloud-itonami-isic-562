(ns cateringops.operation
  "OperationActor -- one event-catering coordination request = one
  supervised actor run, expressed as a langgraph-clj StateGraph. The
  advisor (CateringOpsAdvisor) is sealed into a single node (:advise);
  its proposal is ALWAYS routed through the independent Governor
  (:govern) and the rollout phase gate (:decide) before anything
  commits to the SSoT.

  Everything the actor depends on is injected, so each is a swap, not a
  rewrite:
    - the Store    (MemStore | DatomicStore, see `cateringops.store`)
    - the Advisor  (mock today; real LLM is the next seam --
                     `cateringops.advisor/Advisor` is already the
                     injection point)
    - the Phase    (0->3 rollout)

  One graph run = one coordination request. No unbounded inner loop --
  each operation is auditable and checkpointed. Every commit/hold/
  approval-rejected decision fact lands in `cateringops.store`'s
  append-only ledger (`store/append-ledger!`), so an event's full
  operating history is always a query over an immutable log.

  Human-in-the-loop = real approval workflow:
  `interrupt-before #{:request-approval}` pauses the actor at the
  `:request-approval` node until a human operator resumes it with a
  decision. Mirrors `cerealops.operation` (cloud-itonami-isic-0111)
  node/edge structure exactly, wired to this repo's own
  advisor/governor/phase/store."
  (:require [langgraph.graph :as g]
            [langgraph.checkpoint :as cp]
            [cateringops.advisor :as advisor]
            [cateringops.governor :as governor]
            [cateringops.phase :as phase]
            [cateringops.store :as store]))

(defn- commit-fact
  "The audit fact written when a proposal commits. `:record` carries
  the operational payload the advisor proposed -- cateringops has no
  separate stateful commit-record! entity beyond event/delivery
  registration, so the ledger fact itself is the durable record of
  what happened."
  [request context proposal]
  {:t          :committed
   :op         (:operation request)
   :actor      (:actor-id context)
   :subject    (:target-id request)
   :disposition :commit
   :basis      (:cites proposal)
   :summary    (:summary proposal)
   :record     (:data proposal)})

(defn build
  "Compiles an OperationActor graph bound to `store`. opts:
    :advisor      -- a `cateringops.advisor/Advisor` (default: mock-advisor)
    :checkpointer -- a `langgraph.checkpoint/Checkpointer`
                     (default: in-memory `cp/mem-checkpointer`)"
  [store & [{:keys [advisor checkpointer]
             :or   {advisor      (advisor/mock-advisor)
                    checkpointer (cp/mem-checkpointer)}}]]
  (-> (g/state-graph
       {:channels
        {:request     {:default nil}
         :context     {:default nil}
         :proposal    {:default nil}
         :verdict     {:default nil}
         :disposition {:default nil}
         :approval    {:default nil}
         :audit       {:reducer into :default []}}})

      (g/add-node :intake (fn [s] s))

      (g/add-node :advise
        (fn [{:keys [request]}]
          (let [p (advisor/-advise advisor store request)]
            {:proposal p :audit [(advisor/trace request p)]})))

      (g/add-node :govern
        (fn [{:keys [proposal]}]
          {:verdict (governor/check proposal store)}))

      (g/add-node :decide
        (fn [{:keys [request context verdict]}]
          (let [base (phase/verdict->disposition verdict)
                ph   (:phase context phase/default-phase)
                {:keys [disposition reason]} (phase/gate ph request base)]
            (case disposition
              :hold
              {:disposition :hold
               :audit [(cond-> (governor/hold-fact request context verdict)
                         reason (assoc :phase-reason reason :phase ph))]}

              :escalate
              {:disposition :escalate
               :audit [{:t :approval-requested
                        :op (:operation request) :subject (:target-id request)
                        :reason (or reason :governance-review)
                        :phase ph
                        :confidence (:confidence verdict)}]}

              :commit
              {:disposition :commit}))))

      (g/add-node :request-approval
        (fn [{:keys [request context approval verdict]}]
          (if (= :approved (:status approval))
            {:disposition :commit
             :audit [{:t :approval-granted :op (:operation request)
                      :subject (:target-id request) :by (:by approval)}]}
            {:disposition :hold
             :audit [(merge (governor/hold-fact request context
                                                (update verdict :violations
                                                        (fnil conj []) {:rule :approver-rejected}))
                            {:t :approval-rejected})]})))

      (g/add-node :commit
        (fn [{:keys [request context proposal]}]
          (let [f (commit-fact request context proposal)]
            (store/append-ledger! store f)
            {:audit [f]})))

      (g/add-node :hold
        (fn [{:keys [audit]}]
          (when-let [hf (last (filter #(#{:governor-hold :approval-rejected} (:t %)) audit))]
            (store/append-ledger! store (assoc hf :disposition :hold)))
          {}))

      (g/set-entry-point :intake)
      (g/add-edge :intake :advise)
      (g/add-edge :advise :govern)
      (g/add-edge :govern :decide)

      (g/add-conditional-edges :decide
        (fn [{:keys [disposition]}]
          (case disposition
            :commit   :commit
            :escalate :request-approval
            :hold)))

      (g/add-conditional-edges :request-approval
        (fn [{:keys [disposition]}]
          (if (= :commit disposition) :commit :hold)))

      (g/set-finish-point :commit)
      (g/set-finish-point :hold)

      (g/compile-graph
       {:checkpointer     checkpointer
        :interrupt-before #{:request-approval}})))
