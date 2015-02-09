(ns embang.pcascade
  (:refer-clojure :exclude [rand rand-int rand-nth])
  (:use [embang.state :exclude [initial-state]]
        embang.inference
        [embang.runtime :only [observe sample]]))

;;;; Parallel Cascade

(derive ::algorithm :embang.inference/algorithm)

;;; Initial state

(defn make-initial-state
  "initial state constructor for Parallel Cascade, parameterized
  by the maximum number of running threads"
  [max-count]
  (into embang.state/initial-state
        {;; maximum number of running threads
         ::max-count max-count

         ;;; Shared state
         ;; Number of running threads
         ::particle-count (atom 0)
         ;; Queue of running threads (futures)
         ::particle-queue (atom clojure.lang.PersistentQueue/EMPTY)
         ;; Table of average weights and hit counts
         ::average-weights (atom {})

         ;; Number of collapsed particles
         ::multiplier 1
         
         ;;; Maintaining observe addresses
         ;; counts of occurences of each observe
         ::observe-counts {}
         ;; last observe id
         ::observe-last-id nil}))

(defn average-weight!
  "updates and returns updated average weight for given id"
  [state id weight]
  (when-not (contains? @(state ::average-weights) id)
    ;; First particle arriving at this id ---
    ;; initialize the average weight.
    (swap! (state ::average-weights) #(assoc % id (atom [0. 0]))))

  ;; The id is in the table, update the average weight.
  (let [[average-weight _]
        (swap! (@(state ::average-weights) id)
               (fn [[average-weight count]]
                 [(/ (+ (* count average-weight)
                        (* weight (double (state ::multiplier))))
                     (double (+ count (state ::multiplier))))
                  (double (+ count (state ::multiplier)))]))]
    average-weight))

(defn observe-id
  "returns unique idenditifer for observe"
  [obs state]
  (checkpoint-id obs state ::observe-counts))

(defn record-observe
  "records observe in the state"
  [state observe-id]
  (record-checkpoint state observe-id
                     ::observe-counts ::observe-last-id))

(defmethod checkpoint [::algorithm embang.trap.observe] [_ obs]
  (let [;; Incorporate new observation
        state (add-log-weight (:state obs)
                              (observe (:dist obs) (:value obs)))
        ;; Compute unique observe-id of this observe,
        ;; required for non-global observes.
        observe-id (observe-id obs state)
        state (record-observe state observe-id)

        ;; Update average weight for this barrier.
        weight (Math/exp (get-log-weight state))
        average-weight (average-weight! state observe-id weight)
        weight-ratio (if (pos? average-weight)
                       (/ weight average-weight)
                       1.)

        ;; Compute multiplier and new weight.
        ceil-ratio (Math/ceil weight-ratio)
        floor-ratio (- ceil-ratio 1.)
        [multiplier new-weight]
        (if (< (- weight-ratio floor-ratio) (rand))
          [(int floor-ratio) (/ weight floor-ratio)]
          [(int ceil-ratio) (/ weight ceil-ratio)])
        new-log-weight (Math/log new-weight)]

    ;; If the multiplier is zero, die and return nil.
    (if (zero? multiplier)
      (do (swap! (state ::particle-count) dec) nil)
      ;; Otherwise, continue the thread as well as add
      ;; more threads if the multiplier is greater than 1.
      (let [state (set-log-weight state new-log-weight)]
        (loop [multiplier multiplier]
          (cond
            (= multiplier 1)
            ;; Last particle to add, continue in the current thread.
            #((:cont obs) nil state)

            (>= @(state ::particle-count) (state ::max-count))
            ;; No place to add more particles, collapse remaining
            ;; particles into the current particle.
            #((:cont obs) nil (update-in state [::multiplier] * multiplier))

            :else
            ;; Launch new thread.
            (let [new-thread (future (exec ::algorithm
                                            (:cont obs) nil state))]
              (swap! (state ::particle-count) inc)
              (swap! (state ::particle-queue) #(conj % new-thread))
              (recur (dec multiplier)))))))))

(defmethod checkpoint [::algorithm embang.trap.result] [_ res]
  (swap! ((:state res) ::particle-count) dec)
  res)

(defmethod infer :pcascade [_ prog & {:keys [number-of-threads]
                                      :or {number-of-threads 2}}]
  (let [initial-state (make-initial-state number-of-threads)]
    (letfn
      [(sample-seq []
         (lazy-seq
           (if (empty? @(initial-state ::particle-queue))
             ;; All existing particles died, launch new particles.
             (let [new-threads (repeatedly
                                 number-of-threads
                                 #(future
                                    (exec ::algorithm
                                          prog nil initial-state)))]
                 (swap! (initial-state ::particle-count)
                        #(+ % number-of-threads))
                 (swap! (initial-state ::particle-queue)
                        #(into % new-threads))
                 (sample-seq))

             ;; Retrieve first particle in the queue.
             (let [res @(peek @(initial-state ::particle-queue))]
               (swap! (initial-state ::particle-queue) pop)
               (if (some? res)
                 ;; The particle has lived through to the result.
                 ;; Multiply the weight by the multiplier.
                 (let [state (add-log-weight
                               (:state res)
                               (Math/log
                                 (double
                                   ((:state res) ::multiplier))))]
                   ;; Add the state to the output sequence.
                   (cons state (sample-seq)))
                 ;; The particle died midway, retrieve the next one.
                 (sample-seq))))))]
      (sample-seq))))
