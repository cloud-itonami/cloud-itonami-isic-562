#!/usr/bin/env nbb

(require '[clojure.string :as str])

;; Load demo module
(require '[cateringops.sim :as sim])

;; Run demo
(sim/run-all-scenarios)
