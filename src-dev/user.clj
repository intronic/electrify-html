(ns user ; user ns is loaded by REPL startup
  (:require [hyperfiddle.rcf :refer [tests]]
            [hickory.core :as h]
            [cljfmt.core :as fmt]
            [clj-reload.core :as reload]))

(comment
  (reload/reload) ; reload deps
  )

;; run tests
(hyperfiddle.rcf/enable!)

(tests
 "hickory assumptions"

 (->> (h/parse-fragment "text node") (map h/as-hickory)) :=
 '("text node")

 (->> (h/parse-fragment "<!---->") (map h/as-hickory)) :=
 '({:type :comment :content [""]})
 (->> (h/parse-fragment "<!-- comm\nent -->") (map h/as-hickory)) :=
 '({:type :comment :content [" comm\nent "]})
 (->> (h/parse-fragment "<!-- <div>Stuff\n</div> -->") (map h/as-hickory)) :=
 '({:type :comment :content [" <div>Stuff\n</div> "]})

 (->> (h/parse-fragment "\n\n \n<div>Stuff</div>\n")
      (map h/as-hickory)) :=
 '("\n\n \n" {:type :element, :attrs nil, :tag :div, :content ["Stuff"]} "\n")

 (->> (h/parse-fragment "<div class=\"a\" data=\"b\" >\n<span>s1</span>\n<span>s2</span>\n</div>")
      (map h/as-hickory)) :=
 '({:type    :element
    :attrs   {:class "a" :data "b"}
    :tag     :div
    :content ["\n"
              {:type :element :attrs nil :tag :span :content ["s1"]}
              "\n"
              {:type :element :attrs nil :tag :span :content ["s2"]}
              "\n"]})
 :rcf)

(tests
 "fmt/reformat-string assumptions"

 ;; all sexp on one line
 (let [one-liner "(dom/div  (dom/props  {   }  )   (dom/span  (dom/props { } ) (dom/text \"  s1  \" ) ) )"
       trimmed   "(dom/div  (dom/props  {})   (dom/span  (dom/props {}) (dom/text \"  s1  \")))"]
   (fmt/reformat-string one-liner) := trimmed)

 ;; all sexp on new lines
 (fmt/reformat-string
  (str "(dom/div"
       "\n(dom/props   {  } )"
       "\n(dom/span"
       "\n(dom/props {})"
       "\n(dom/text \"s1\")))")) :=
 (str "(dom/div\n"
      " (dom/props   {})\n"
      " (dom/span\n"
      "  (dom/props {})\n"
      "  (dom/text \"s1\")))")

 ;; first sexp on same line; second on new
 (fmt/reformat-string
  (str "(dom/div (dom/props {})"
       "\n(dom/span (dom/props {})"
       "\n(dom/text \"s1\")))")) :=
 (str "(dom/div (dom/props {})\n"
      "         (dom/span (dom/props {})\n"
      "                   (dom/text \"s1\")))")

 ;; blank lines preserved
 (fmt/reformat-string
  (str "(dom/div (dom/props {})\n"
       "\n"
       "(dom/span (dom/props {})\n"
       "\n"
       "(dom/text \"s1\")))")) :=
 (str "(dom/div (dom/props {})\n"
      "\n"
      "         (dom/span (dom/props {})\n"
      "\n"
      "                   (dom/text \"s1\")))")

 :rcf)

(require '[intronic.electrify-html])
