(ns hmm-x
  (:use [anglican emit runtime]))

;;; HMM

;; observing states 1-16
;; predicting initial and 17th state

(defanglican hmm-x
  "HMM with predicts for first state"
  [assume initial-state-distribution (list 1.0 1.0 1.0)]
  [assume get-state-transition-vector
   (lambda (s)
           (cond ((= s 0) (list 0.1 0.5 0.4))
                 ((= s 1) (list 0.2 0.2 0.6))
                 ((= s 2) (list 0.15 0.15 0.7))))]
  [assume transition
   (lambda (prev-state)
     (sample (discrete (get-state-transition-vector prev-state))))]
  [assume get-state
   (mem (lambda (index)
                (if (<= index 0)
                  (sample (discrete initial-state-distribution))
                  (transition (get-state (- index 1))))))]
  [assume get-state-observation-mean
   (lambda (s)
           (cond ((= s 0) -1)
                 ((= s 1) 1)
                 ((= s 2) 0)))]
  [observe (normal (get-state-observation-mean (get-state 1)) 1) 0.9]
  [observe (normal (get-state-observation-mean (get-state 2)) 1) 0.8]
  [observe (normal (get-state-observation-mean (get-state 3)) 1) 0.7]
  [observe (normal (get-state-observation-mean (get-state 4)) 1) 0]
  [observe (normal (get-state-observation-mean (get-state 5)) 1) -0.025]
  [observe (normal (get-state-observation-mean (get-state 6)) 1) -5]
  [observe (normal (get-state-observation-mean (get-state 7)) 1) -2]
  [observe (normal (get-state-observation-mean (get-state 8)) 1) -0.1]
  [observe (normal (get-state-observation-mean (get-state 9)) 1) 0]
  [observe (normal (get-state-observation-mean (get-state 10)) 1) 0.13]
  [observe (normal (get-state-observation-mean (get-state 11)) 1) 0.45]
  [observe (normal (get-state-observation-mean (get-state 12)) 1) 6]
  [observe (normal (get-state-observation-mean (get-state 13)) 1) 0.2]
  [observe (normal (get-state-observation-mean (get-state 14)) 1) 0.3]
  [observe (normal (get-state-observation-mean (get-state 15)) 1) -1]
  [observe (normal (get-state-observation-mean (get-state 16)) 1) -1]
  [predict (get-state 0)]
  [predict (get-state 17)])
