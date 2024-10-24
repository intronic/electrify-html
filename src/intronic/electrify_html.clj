(ns intronic.electrify-html
  (:require [hickory.core :as h]
            [clojure.string :as str]
            [clojure.edn :as edn]
            [clojure.pprint :as p]
            [hyperfiddle.rcf :refer [tests]])
  (:gen-class))

(defn spy [msg elt] (print msg) (prn elt) elt)

;;; empty text on electric fragment types
(defmulti empty-text? class)

(defmethod empty-text? java.lang.String
  [elt] (= "" (str/trim elt)))

(defmethod empty-text? org.jsoup.nodes.TextNode ; https://jsoup.org/apidocs/org/jsoup/nodes/TextNode.html
  [elt] (empty-text? (h/as-hickory elt)))

(defmethod empty-text? clojure.lang.ISeq
  [elts] (every? empty-text? elts))

(defmethod empty-text? clojure.lang.IPersistentVector
  [elts] (every? empty-text? elts))

(defmethod empty-text? :default
  [_] false)

(def not-empty-text? (comp not empty-text?))

(defn class-first [{:keys [class] :as attrs}]
  (if class
    (cons [:class class] (seq (dissoc attrs :class)))
    (seq attrs)))

(defn text-str [t]
  (if-not (empty? (str/trim t)) (str "(dom/text " (pr-str t) ")") ""))

(defn props-str [attrs]
  (letfn [(attr-str [{:keys [class] :as attrs}]
            (let [class-line (if class
                               (str (pr-str :class class) (if (> (count attrs) 1) "\n " ""))
                               "")]
              (str class-line (str/join " " (map (partial apply pr-str) (dissoc attrs :class))))))]
    (str "(dom/props {" (attr-str attrs) "})")))

(defn element-str [tag attrs content]
  (str "(dom/" (name tag) " " (props-str attrs) "\n " content ")"))

(defn comment-str [comment]
  (str "\n ; " (str/join "\n ; " comment) "\n"))

(defn print-code [s]
  (p/with-pprint-dispatch
    p/code-dispatch
    (p/pprint
     (clojure.edn/read-string s))))


;;; convert html fragments to electric dom code

;; hickory node types: :element, :comment, :document, :document-type
;; text nodes are converted to java.lang.String
(defmulti electrify (fn [arg] (get arg :type (class arg))))


(defmethod electrify nil ; node without content
  [_] "")

(defmethod electrify java.lang.String
  [elt]
  (text-str elt))

(defmethod electrify org.jsoup.nodes.Node
  [elt]
  (electrify (h/as-hickory elt)))

(defmethod electrify :element
  [{:keys [tag attrs content]}]
  (element-str tag attrs (electrify content)))

(defmethod electrify :comment
  [{:keys [content]}]
  (comment-str content))

(defmethod electrify clojure.lang.LazySeq
  [elts]
  (->> (doall elts) (map electrify) (str/join "\n ")))

(defmethod electrify clojure.lang.PersistentVector
  [elts]
  (->> elts (map electrify) (str/join "\n ")))

(defmethod electrify :default
  [elt] (throw (ex-info (str "Unknown element " (pr-str elt)) {:data elt})))

(tests
 ;; Helpers
 (empty-text? "\n") := true
 (map str/trim (map h/as-hickory (h/parse-fragment "\n"))) := '("")
 (empty-text? [""]) := true
 (empty-text? (h/parse-fragment "\n")) := true
 (empty-text? (h/parse-fragment "<hr/>")) := false

 (not-empty-text? "\n") := (not (empty-text? "\n"))
 (not-empty-text? (h/parse-fragment "<hr/>")) := (not (empty-text? (h/parse-fragment "<hr/>")))

 (class-first {:a 1 :b 2}) := '([:a 1] [:b 2])
 (class-first {:a 1 :b 2 :class "ok" :d 3}) := '([:class "ok"] [:a 1] [:b 2] [:d 3])

 (text-str "hello") := "(dom/text \"hello\")"
 (text-str " \t  \n ") := ""

 (props-str {:a 1 :b 2}) := "(dom/props {:a 1 :b 2})"
 (props-str {:a 1 :b 2 :class "ok" :d 3}) := "(dom/props {:class \"ok\"\n :a 1 :b 2 :d 3})"

 (element-str :div {:class "k"} "(dom/text \"Hello\")") := "(dom/div (dom/props {:class \"k\"})\n (dom/text \"Hello\"))"
 :rcf)

(tests
 ;; text and comments
 (electrify "text node") := "(dom/text \"text node\")"
 (electrify (h/as-hickory (first (h/parse-fragment "text node")))) := "(dom/text \"text node\")"
 (map electrify (h/parse-fragment "text node")) := '("(dom/text \"text node\")")

 (electrify (h/parse-fragment "<!-- Comment --><div>text node</div>")) :=
 "\n ;  Comment \n\n (dom/div (dom/props {})\n (dom/text \"text node\"))"
 :rcf)

(tests
 (let [frag "\n<span aria-label=\"lab\" class=\"c\"> Badge </span>\n"
       frag-result "(dom/span (dom/props {:class \"c\"\n :aria-label \"lab\"})\n (dom/text \" Badge \"))"]
   (->> frag h/parse-fragment (map h/as-hickory) (filter not-empty-text?) electrify) := frag-result
   (->> frag h/parse-fragment (filter not-empty-text?) electrify) := frag-result
   (->> frag h/parse-fragment (filterv not-empty-text?) electrify) := frag-result)
 ; element with no content
 (electrify (map h/as-hickory (h/parse-fragment "<img src=\"id\" />"))) := "(dom/img (dom/props {:src \"id\"})\n )"
 :rcf)

(defn -main
  "Convert file of html fragments to electric dom nodes"
  [& args]
  (let [comments (-> args first :keep-comments boolean)
        file (-> args first :file str)]
    (if (seq file)
     (->> file
          slurp
          h/parse-fragment
          (map h/as-hickory)
          (filter not-empty-text?)
          electrify
          (#(if comments
              (println %)
              (print-code %))))
      (println "Usage: :file _file_name_ [:keep-comments true]"))))
