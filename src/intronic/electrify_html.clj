(ns intronic.electrify-html
  (:require
   [cljfmt.core :as fmt]
   [hickory.core :as h]
   [hyperfiddle.rcf :refer [tests]]
   [intronic.electrify-html.string :as s]
   [intronic.electrify-html.utils :as u])
  (:gen-class))

(defn spy [msg elt] (print msg) (prn elt) elt)

(def ^:dynamic *default-ns* "dom")
(def ^:dynamic *element-ns* *default-ns*)
(def ^:dynamic *content-remove-blanks* true)

(defn elt-str [elt] (str *default-ns* "/" elt))

(defn remove-blank-content [coll]
  (if *content-remove-blanks* (u/remove-blank-lines coll) coll))

;;; convert html fragments to electric dom code; returns string
(defmulti electrify (fn [arg] (get arg :type (class arg))))
;; hickory node types: :element, :comment, :document, :document-type
;; text nodes are converted to java.lang.String
;; assume string nodes are all "dom" (eg dom/text and svg/text are the same)

(defmethod electrify nil ; node without content
  [_] "")

(defmethod electrify String ; hickory text node; type String
  electrify-str
  [elt]
  (s/text-str *default-ns* elt))

(defmethod electrify :element ; hickory node :type :element
  electrify-element
  [{:keys [tag attrs content]}]
  (binding [*element-ns* (if (= tag :svg) "svg" *element-ns*)] ; either use :svg or the current value (svg or default)
    (s/element-str *element-ns* *default-ns* tag
                   (if (= "svg" *element-ns*) (s/rename-svg-attributes attrs) attrs)
                   (electrify content))))

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
 (doseq [ns ["dom" "svg"]
         elt ["p" "text"]]
   (binding [*default-ns* ns]
     (elt-str elt) := (str ns "/" elt))))

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
     (->> (h/parse-fragment "<!-- Comment1 -->") first h/as-hickory electrify) :=
     (str "(comment \" Comment1 \")")

     ;; Vector of parse-fragment (mapv)
     (->> (h/parse-fragment "<!-- Comment2 --><div a=\"1\" class=\"c\">helo</div>") (mapv h/as-hickory) electrify) :=
     (str "(comment \" Comment2 \")"
          (if s/*siblings-on-new-line* "\n" " ")
          "(dom/div" "\n"
          "(dom/props {:class \"c\"\n:a \"1\"})" "\n"
          "(dom/text \"helo\"))")

     ;; Lazy-seq of parse-fragment (map)
     (->> (h/parse-fragment "<!-- Comment3 --><div a=\"1\" class=\"c\">helo</div>") (map h/as-hickory) electrify) :=
     (str "(comment \" Comment3 \")"
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
 "empty formatted electric-component"
 (electric-component "") :=
 (str "(e/defn Component\n"
      "  []\n"
      "  (e/client))"))

(tests
 "formatted electric-component"
 (doseq [remove-blanks [true false]]
   (binding [*content-remove-blanks* remove-blanks]
     (->> "\n<!-- Comment1 --><div>\n\n<span>\ns1\n</span>\n<!-- Comm2 -->\n<img src=\"abc\" /><span aria-label=\"lab\" class=\"k\">s2</span>\n\n</div>"
          electric-component) :=
     (str "(e/defn Component\n"
          "  []\n"
          "  (e/client\n"
          (if *content-remove-blanks* "" "\n")
          "   (comment \" Comment1 \")\n"
          "   (dom/div\n"
          "    (dom/props {})\n"
          (if *content-remove-blanks* "" "\n")
          "    (dom/span\n"
          "     (dom/props {})\n"
          "     (dom/text \"\\ns1\\n\"))\n"
          (if *content-remove-blanks* "" "\n")
          "    (comment \" Comm2 \")\n"
          (if *content-remove-blanks* "" "\n")
          "    (dom/img (dom/props {:src \"abc\"}))\n"
          "    (dom/span\n"
          "     (dom/props {:class \"k\"\n"
          "                 :aria-label \"lab\"})\n"
          "     (dom/text \"s2\"))))"
          ")"))))

(tests
 "embedded svg content, :xmlns is renamed :data-xmlns"
 (name :viewBox) := "viewBox"
 (let [svg "<div>
              <span>Helo</span>
              <svg viewBox=\"0 0 300 200\" version=\"1.1\" width=\"300\" height=\"200\" xmlns=\"http://www.w3.org/2000/svg\">
                <rect width=\"100%\" height=\"100%\" fill=\"red\" />
                <text fill=\"white\">SVG thing</text>
                <circle cx=\"150\" cy=\"100\" r=\"80\" />
              </svg>
              <span>Bye</span>
            </div>"]
   (->> svg electric-component) :=
   (str "(e/defn Component\n"
        "  []\n"
        "  (e/client\n"
        "   (dom/div\n"
        "    (dom/props {})\n"
        "    (dom/span\n"
        "     (dom/props {})\n"
        "     (dom/text \"Helo\"))\n"
        "    (svg/svg\n"
        "     (dom/props {:version \"1.1\" :width \"300\" :height \"200\" :viewBox \"0 0 300 200\" :data-xmlns \"http://www.w3.org/2000/svg\"})\n"
        "     (svg/rect (dom/props {:width \"100%\" :height \"100%\" :fill \"red\"}))\n"
        "     (svg/text\n"
        "      (dom/props {:fill \"white\"})\n"
        "      (dom/text \"SVG thing\"))\n"
        "     (svg/circle (dom/props {:cx \"150\" :cy \"100\" :r \"80\"})))\n"
        "    (dom/span\n"
        "     (dom/props {})\n"
        "     (dom/text \"Bye\"))))"
        ")")))
