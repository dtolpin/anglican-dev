(ns branching
  (:use [anglican emit runtime]))

;;; Simple branching

(defanglican branching
  "simple branching"
  [assume fib (lambda (n)
                      (cond ((= n 0) 0)
                            ((= n 1) 1)
                            (else (+ (fib (- n 1))
                                     (fib (- n 2))))))]
  [assume r (sample (poisson 4))]
  [assume l (if (< 4 r)
              6
              (+ (fib (* 3 r)) (sample (poisson 4))))]
  [observe (poisson l) 6]
  [predict r])

(defanglican optimized
  "simple branching with some basic manual optimizations"
  [assume poisson-source (poisson 4)]
  [assume fib (lambda (n)
                      (cond ((= n 0) 0)
                            ((= n 1) 1)
                            (else (+ (fib (- n 1))
                                     (fib (- n 2))))))]
  [assume r (sample poisson-source)]
  [assume l (if (< 4 r)
              6
              (+ (fib (* 3 r)) (sample poisson-source)))]
  [observe (poisson l) 6]
  [predict r])
