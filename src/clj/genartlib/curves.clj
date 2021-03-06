(ns genartlib.curves
  (:require [genartlib.algebra :refer [interpolate point-dist]]))

(defn- single-chaikin-step [points tightness]
  (mapcat (fn [[[start-x start-y] [end-x end-y]]]
            (let [q-x (interpolate start-x end-x (+ 0.0 tightness))
                  q-y (interpolate start-y end-y (+ 0.0 tightness))
                  r-x (interpolate start-x end-x (- 1.0 tightness))
                  r-y (interpolate start-y end-y (- 1.0 tightness))]
              [[q-x q-y] [r-x r-y]]))
          (partition 2 1 points)))

(defn chaikin-curve
  "Forms a Chaikin curve from a seq of points, returning a new
   seq of points.

   The tightness parameter controls how sharp the corners will be,
   and should be a value between 0.0 and 0.5.  A value of 0.0 retains
   full sharpness, and 0.25 creates maximum smoothness.

   The depth parameter controls how many recursive steps will occur.
   The more steps, the smoother the curve is (assuming tightness is
   greater than zero). Suggested values are between 1 and 8, with a
   good default being 4.

   When points form a closed polygon, it's recommended that the start
   point be repeated at the end of points to avoid a gap."

  ([points] (chaikin-curve points 4))
  ([points depth] (chaikin-curve points depth 0.25))

  ([points depth tightness]
   (nth (iterate #(single-chaikin-step % tightness) points) depth)))

(defn chaikin-curve-retain-ends
  "Like chaikin-curve, but retains the first and last point in the
   original `points` seq."
  ([points] (chaikin-curve-retain-ends points 4))
  ([points depth] (chaikin-curve-retain-ends points depth 0.25))
  ([points depth tightness]
   (if (<= (count points) 2)
     points
     (let [first-point (first points)
           last-point (last points)
           processed-points (chaikin-curve points depth tightness)]
       (concat [first-point]
               processed-points
               [last-point])))))

(defn curve-length
  "Returns the total length of a curve"
  [curve]
  (->> curve
       (partition 2 1)
       (map #(apply point-dist %))
       (reduce +)))

(defn split-curve-with-step
  [curve step-size]
  (if (<= (count curve) 1)
    curve
    (loop [curve (rest curve)
           segments (transient [])
           current-segment (transient [(first curve)])
           current-length 0
           prev-point (first curve)]

      (if (empty? curve)
        (if (<= (count current-segment) 1)
          (persistent! segments)
          (persistent! (conj! segments (persistent! current-segment))))

        (let [new-point (first curve)
              new-dist (point-dist prev-point new-point)
              new-length (+ current-length new-dist)]
          (if (< new-length step-size)
            (recur (rest curve) segments (conj! current-segment new-point) new-length new-point)
            (let [dist-needed (- step-size current-length)
                  t (/ dist-needed new-dist)
                  x (interpolate (first prev-point) (first new-point) t)
                  y (interpolate (second prev-point) (second new-point) t)]
              (if (= 1 (count curve))
                ; we're done, cleanup and return
                (persistent! (conj! segments (persistent! (conj! current-segment [x y]))))

                ; we need to split
                (let [finalized-segment (persistent! (conj! current-segment [x y]))]
                  (recur curve
                         (conj! segments finalized-segment)
                         (transient [[x y]])
                         0
                         [x y]))))))))))

(defn split-curve-into-parts
  [curve num-parts]
  (if (<= num-parts 1)
    curve
    (let [total-length (curve-length curve)
          segment-length (/ total-length num-parts)]
      (split-curve-with-step curve segment-length))))
