(ns intronic.electrify-html.utils
  (:require [clojure.string :as str]
            [hyperfiddle.rcf :refer [tests]]))

;;;;;;;;;;

(defn remove-blank-lines
  "Removes any completely blank strings from a heterogenous collection."
  [coll]
  (remove #(and (string? %) (empty? (str/trim %))) coll))

(tests
 (remove-blank-lines []) := []
 (remove-blank-lines nil) := []
 (remove-blank-lines ["" "  \n  \t \n\n " " a\nb\t " {:a 1}]) := [" a\nb\t " {:a 1}]
 :rcf)

;;;;;;;;;;

(defn class-first [{:keys [class] :as attrs}]
  (if class
    (cons [:class class] (seq (dissoc attrs :class)))
    (seq attrs)))

(tests
 (class-first {:a 1 :b 2}) := '([:a 1] [:b 2])
 (class-first {:a 1 :b 2 :class "ok" :d 3}) := '([:class "ok"] [:a 1] [:b 2] [:d 3])
 :rcf)
