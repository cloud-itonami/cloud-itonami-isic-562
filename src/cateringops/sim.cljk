(ns cateringops.sim
  "Demo driver -- `clojure -M:dev:run`. Walks a registered/verified
  event and delivery through a clean phase-3 auto-commit, an
  always-escalate safety concern (human approves), an auto-commit
  supply coordination, and a hard-hold (unregistered event), then
  prints the resulting audit ledger. Mirrors `cerealops.sim`
  (cloud-itonami-isic-0111)."
  (:require [langgraph.graph :as g]
            [cateringops.operation :as operation]
            [cateringops.store :as store]))

(def coordinator {:actor-id "catering-ops-01" :role :catering-coordinator :phase 3})

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "coordinator-01"}}
          {:thread-id tid :resume? true}))

(defn- reject! [actor tid]
  (g/run* actor {:approval {:status :rejected :by "coordinator-01"}}
          {:thread-id tid :resume? true}))

(defn demo
  "Run the compiled StateGraph through a commit path, an
  escalate->approve->commit path, an auto-commit path, an
  escalate->reject->hold path, and a hard-hold path; print each result
  and the final audit ledger."
  []
  (let [st (-> (store/mem-store)
               store/load-demo-events!
               store/load-demo-deliveries!
               store/load-demo-supplies!
               store/load-demo-shifts!)
        actor (operation/build st)]

    (println "=== Event Catering Operations Coordinator Demo ===")

    (println "\n== schedule-catering-event E001 (verified, phase-3, governor-clean -> commit) ==")
    (println (exec-op actor "t1"
                      {:operation :schedule-catering-event :target-id "E001"
                       :data {:venue "Grand Hall Downtown" :date "2026-08-15" :capacity 150}}
                      coordinator))

    (println "\n== coordinate-delivery-status-update D001 (verified, phase-3 -> commit) ==")
    (println (exec-op actor "t2"
                      {:operation :coordinate-delivery-status-update :target-id "D001"
                       :data {:destination "Grand Hall Downtown" :time "15:00" :status :in-transit}}
                      coordinator))

    (println "\n== flag-safety-concern E001 (ALWAYS escalates -- coordinator approves) ==")
    (let [r (exec-op actor "t3"
                     {:operation :flag-safety-concern :target-id "E001"
                      :data {:concern "Facility ventilation issue detected"}}
                     coordinator)]
      (println r)
      (println "-- coordinator approves --")
      (println (approve! actor "t3")))

    (println "\n== coordinate-supply-request S001 (phase-3 -> auto-commit) ==")
    (println (exec-op actor "t4"
                      {:operation :coordinate-supply-request :target-id "S001"
                       :data {:item-type "linens" :quantity 100 :unit-cost 0.50}}
                      coordinator))

    (println "\n== coordinate-supply-request S001 (phase-0 -> escalate -- coordinator rejects) ==")
    (let [r (exec-op actor "t5"
                     {:operation :coordinate-supply-request :target-id "S001"
                      :data {:item-type "linens" :quantity 100 :unit-cost 0.50}}
                     (assoc coordinator :phase 0))]
      (println r)
      (println "-- coordinator rejects --")
      (println (reject! actor "t5")))

    (println "\n== schedule-catering-event E999 (unregistered -> HARD hold, no interrupt) ==")
    (println (exec-op actor "t6"
                      {:operation :schedule-catering-event :target-id "E999"
                       :data {:venue "Nowhere" :date "2026-08-01" :capacity 10}}
                      coordinator))

    (println "\n== audit ledger ==")
    (doseq [f (store/ledger st)] (println f))

    {:ledger (store/ledger st)}))

(defn -main
  "clojure -M:dev:run entrypoint."
  [& _args]
  (demo))

(comment
  ;; In a real REPL:
  (demo)
  )
