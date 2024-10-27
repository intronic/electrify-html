(ns intronic.electrify-html.string
  (:require [clojure.string :as str]
            [hyperfiddle.rcf :refer [tests]]))

;; element options
(def ^:dynamic *element-props-on-new-line* true)
(def ^:dynamic *element-content-on-new-line* true)
(def ^:dynamic *siblings-on-new-line* true)

;;;;;;;;;;

(defn text-str [t]
  (if-not (empty? (str/trim t)) (str "(dom/text " (pr-str t) ")") ""))

(defn props-str [attrs]
  (letfn [(attr-str [{:keys [class] :as attrs}]
            (let [class-line (if class
                               (str (pr-str :class class) (if (> (count attrs) 1) \newline ""))
                               "")]
              (str class-line (str/join " " (map (partial apply pr-str) (dissoc attrs :class))))))]
    (str "(dom/props {" (attr-str attrs) "})")))

(defn comment-str [content]
  (str "(comment " content ")"))

(defn electric-component-str
  "Wrap body in an electric component."
  [body]
  (str "(e/defn Component" "\n" "[]" "\n" body ")"))

(defn element-str [tag attrs content]
  (str "(dom/" (name tag)
       (if (empty? content)
         (str " " (props-str attrs))
         (str
          (if *element-props-on-new-line* "\n" " ")
          (props-str attrs)
          (if *element-content-on-new-line* "\n" " ")
          content)) ")"))

(defn siblings-str [coll]
  (->> coll (str/join (if *siblings-on-new-line* "\n" " "))))

(tests
 ;; Helpers

 (text-str "hello") := "(dom/text \"hello\")"
 (text-str " \t  \n ") := ""

 (props-str {:a 1 :b 2}) := "(dom/props {:a 1 :b 2})"
 (props-str {:a 1 :b 2 :class "ok" :d 3}) :=
 "(dom/props {:class \"ok\"\n:a 1 :b 2 :d 3})"

 (comment-str "Hello") :=
 "(comment Hello)"

 (electric-component-str "(dom/div\n(dom/props {})\n(dom/text \"ok\"))") :=
 "(e/defn Component\n[]\n(dom/div\n(dom/props {})\n(dom/text \"ok\")))"

 (doseq [param [true false]]
   (binding [*siblings-on-new-line* param]
     (siblings-str []) := ""
     (siblings-str ["a" "b" 1 2 3]) :=
     (if param "a\nb\n1\n2\n3" "a b 1 2 3"))

   (binding [*element-props-on-new-line* param
             *element-content-on-new-line* param]
     (element-str :div {:class "k"} nil) := "(dom/div (dom/props {:class \"k\"}))"
     (element-str :div {:class "k"} "") := "(dom/div (dom/props {:class \"k\"}))"
     (element-str :div {:class "k"} "(dom/text \"Hello\")") :=
     (str "(dom/div"
          (if param "\n" " ")
          "(dom/props {:class \"k\"})"
          (if param "\n" " ")
          "(dom/text \"Hello\"))")))

 :rcf)
