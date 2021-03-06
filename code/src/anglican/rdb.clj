(ns anglican.rdb
  "Random DB, same as Lightweight Metropolis-Hastings"
  (:refer-clojure :exclude [rand rand-int rand-nth])
  (:require anglican.lmh)
  (:use anglican.inference))

;;;; Alias for Single-site Metropolis-Hastings

(defmethod infer :rdb [algorithm & args] (apply infer :lmh args))
