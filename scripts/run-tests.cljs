#!/usr/bin/env nbb

(require '[clojure.string :as str])

;; Load test modules
(require '[cateringops.test :as test])

;; Run all tests
(test/run-all-tests)
