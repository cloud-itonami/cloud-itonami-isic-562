(ns cateringops.store
  "SSoT: event/delivery registry, append-only ledgers")

;; --- Data Model ---

(defn new-event
  "Create an event record (not yet registered/verified)"
  [id name client-name date venue capacity]
  {:id id
   :name name
   :client-name client-name
   :date date
   :venue venue
   :capacity capacity
   :registered? false
   :verified? false
   :status :pending})

(defn new-delivery
  "Create a delivery record (not yet registered/verified)"
  [id event-id destination scheduled-time]
  {:id id
   :event-id event-id
   :destination destination
   :scheduled-time scheduled-time
   :registered? false
   :verified? false
   :status :pending})

(defn new-supply-item
  "Create a supply request (equipment, linens, cleaning supplies)"
  [id event-id item-type quantity unit-cost]
  {:id id
   :event-id event-id
   :item-type item-type
   :quantity quantity
   :unit-cost unit-cost
   :status :pending})

(defn new-staff-shift
  "Create a staff shift proposal"
  [id event-id role start-time end-time]
  {:id id
   :event-id event-id
   :role role
   :start-time start-time
   :end-time end-time
   :status :proposed})

;; --- In-Memory Store ---

(defprotocol IStore
  "Event catering store interface"
  (lookup-event [_ event-id] "Fetch event by ID")
  (all-events [_] "List all events")
  (lookup-delivery [_ delivery-id] "Fetch delivery by ID")
  (all-deliveries [_] "List all deliveries")
  (lookup-supply [_ supply-id] "Fetch supply request by ID")
  (register-event! [_ event] "Mark event as registered")
  (verify-event! [_ event-id] "Mark event as verified")
  (append-ledger! [_ ledger-key entry] "Append to audit ledger"))

(defrecord MemStore [events deliveries supplies shifts ledger]
  IStore
  (lookup-event [_ event-id] (get @events event-id))
  (all-events [_] (vals @events))
  (lookup-delivery [_ delivery-id] (get @deliveries delivery-id))
  (all-deliveries [_] (vals @deliveries))
  (lookup-supply [_ supply-id] (get @supplies supply-id))
  (register-event! [_ event]
    (swap! events assoc (:id event) (assoc event :registered? true)))
  (verify-event! [_ event-id]
    (swap! events update event-id assoc :verified? true))
  (append-ledger! [_ ledger-key entry]
    (swap! ledger update ledger-key (fnil conj []) entry)))

(defn new-store []
  (MemStore.
   (atom {})
   (atom {})
   (atom {})
   (atom {})
   (atom {})))

;; --- Demo Data ---

(defn load-demo-events! [store]
  "Populate store with demo event data"
  (let [event1 (new-event "E001" "Corporate Retreat" "TechCorp Inc" "2026-08-15" "Grand Hall Downtown" 150)
        event2 (new-event "E002" "Wedding Reception" "Smith-Johnson" "2026-09-20" "Sunset Manor" 200)]
    (register-event! store event1)
    (verify-event! store "E001")
    (register-event! store event2)
    store))

(defn load-demo-deliveries! [store]
  "Populate store with demo delivery data"
  (let [del1 (new-delivery "D001" "E001" "Grand Hall Downtown" "2026-08-15 15:00")
        del2 (new-delivery "D002" "E002" "Sunset Manor" "2026-09-20 17:30")]
    (swap! (:deliveries store) assoc "D001" del1 "D002" del2)
    store))

(defn load-demo-supplies! [store]
  "Populate store with demo supply requests"
  (let [supp1 (new-supply-item "S001" "E001" "linens" 100 0.50)
        supp2 (new-supply-item "S002" "E001" "glassware" 300 1.25)]
    (swap! (:supplies store) assoc "S001" supp1 "S002" supp2)
    store))

(defn load-demo-shifts! [store]
  "Populate store with demo staff shift proposals"
  (let [shift1 {:id "ST001" :event-id "E001" :role :server :start-time "2026-08-15 16:00" :end-time "2026-08-15 22:00" :status :proposed}
        shift2 {:id "ST002" :event-id "E002" :role :coordinator :start-time "2026-09-20 17:00" :end-time "2026-09-21 01:00" :status :proposed}]
    (swap! (:shifts store) assoc "ST001" shift1 "ST002" shift2)
    store))
