(ns intronic.electrify-html
  (:require
   [cljfmt.core :as fmt]
   [hickory.core :as h]
   [hyperfiddle.rcf :refer [tests]]
   [intronic.electrify-html.string :as s]
   [intronic.electrify-html.utils :as u])
  (:gen-class))

(defn spy [msg elt] (print msg) (prn elt) elt)

(def ^:dynamic *content-remove-blanks* true)

(defn remove-blank-content [coll]
  (if *content-remove-blanks* (u/remove-blank-lines coll) coll))

;;; convert html fragments to electric dom code
;;; returns string

;; hickory node types: :element, :comment, :document, :document-type
;; text nodes are converted to java.lang.String
(defmulti electrify (fn [arg] (get arg :type (class arg))))

(defmethod electrify nil ; node without content
  [_] "")

(defmethod electrify String ; hickory text node; type String
  electrify-str
  [elt]
  (s/text-str elt))

(defmethod electrify :element ; hickory node :type :element
  electrify-element
  [{:keys [tag attrs content]}]
  (s/element-str tag attrs (electrify content)))

(defmethod electrify :comment ; hickory node :type :comment
  electrify-comment
  [{:keys [content]}]
  (s/comment-str (s/siblings-str content)))

(defmethod electrify clojure.lang.Sequential ; hickory fragments or children
  electrify-vec
  [elts]
  ;; hickory text content is already strings so remove blanks before electrify
  (->> elts
       remove-blank-content
       (mapv electrify)
       s/siblings-str))

(defmethod electrify :default
  electrify-default
  [elt] (throw (ex-info (str "Unknown element " (pr-str elt)) {:data elt})))

(defn electric-component [s]
  (->> s
       h/parse-fragment
       (map h/as-hickory)
       electrify
       s/electric-component-str
       fmt/reformat-string))

(defn -main
  "Convert file of html fragments to electric dom nodes"
  [& args]
  (let [file (-> args first :file str)]
    (if (seq file)
      (->> file slurp electric-component println)
      (println "Usage: :file _file_name_ [:keep-comments true]"))))

(tests

 (doseq [newline [true false]]
   (binding [s/*siblings-on-new-line* newline]
     ;; nil
     (electrify nil) := ""

     ;; String
     (electrify "text node") := "(dom/text \"text node\")"

     (->> (h/parse-fragment "text node") first h/as-hickory electrify)
     := "(dom/text \"text node\")"

     ;; :element
     (->> (h/parse-fragment "<div a=\"1\" class=\"c\">helo</div>") first h/as-hickory electrify) :=
     (str "(dom/div" "\n"
          "(dom/props {:class \"c\"\n:a \"1\"})" "\n"
          "(dom/text \"helo\"))")

     ;; comment
     (->> (h/parse-fragment "<!-- Comment -->") first h/as-hickory electrify) :=
     (str "(comment  Comment )")

     ;; Vector of parse-fragment (mapv)
     (->> (h/parse-fragment "<!-- Comment --><div a=\"1\" class=\"c\">helo</div>") (mapv h/as-hickory) electrify) :=
     (str "(comment  Comment )"
          (if s/*siblings-on-new-line* "\n" " ")
          "(dom/div" "\n"
          "(dom/props {:class \"c\"\n:a \"1\"})" "\n"
          "(dom/text \"helo\"))")

     ;; Lazy-seq of parse-fragment (map)
     (->> (h/parse-fragment "<!-- Comment --><div a=\"1\" class=\"c\">helo</div>") (map h/as-hickory) electrify) :=
     (str "(comment  Comment )"
          (if s/*siblings-on-new-line* "\n" " ")
          "(dom/div" "\n"
          "(dom/props {:class \"c\"\n:a \"1\"})" "\n"
          "(dom/text \"helo\"))")

     ; :content of <div> is a vector
     (->> (h/parse-fragment "<div><span>s1</span><span>s2</span></div>") (map h/as-hickory) electrify) :=
     (str "(dom/div" "\n"
          "(dom/props {})" "\n"
          "(dom/span" "\n"
          "(dom/props {})" "\n"
          "(dom/text \"s1\"))"
          (if s/*siblings-on-new-line* "\n" " ")
          "(dom/span" "\n" "(dom/props {})" "\n"
          "(dom/text \"s2\"))" ")"))))

(tests
 "formatted electric-component"
 (doseq [remove-blanks [#_true false]]
   (binding [*content-remove-blanks* remove-blanks]
     (->> "\n<!-- Comment --><div>\n\n<span>\ns1\n</span>\n<!-- Comm2 -->\n<img src=\"abc\" /><span aria-label=\"lab\" class=\"k\">s2</span>\n\n</div>"
          electric-component) :=
     (str "(e/defn Component\n"
          "  []\n"
          (if *content-remove-blanks* "" "\n")
          "  (comment  Comment)\n"
          "  (dom/div\n"
          "   (dom/props {})\n"
          (if *content-remove-blanks* "" "\n")
          "   (dom/span\n"
          "    (dom/props {})\n"
          "    (dom/text \"\\ns1\\n\"))\n"
          (if *content-remove-blanks* "" "\n")
          "   (comment  Comm2)\n"
          (if *content-remove-blanks* "" "\n")
          "   (dom/img (dom/props {:src \"abc\"}))\n"
          "   (dom/span\n"
          "    (dom/props {:class \"k\"\n"
          "                :aria-label \"lab\"})\n"
          "    (dom/text \"s2\"))))"))))
