(ns intronic.electrify-html.string
  (:require [clojure.string :as str]
            [clojure.set :as set]
            [hyperfiddle.rcf :refer [tests]]))

;; element options
(def ^:dynamic *element-props-on-new-line* true)
(def ^:dynamic *element-content-on-new-line* true)
(def ^:dynamic *siblings-on-new-line* true)

;;;;;;;;;;
;;; SVG attribute names taken from https://developer.mozilla.org/en-US/docs/Web/SVG/Attribute
;;; - list generated with the following JS script in the browser console:
;;;   Array.from(new Set(Array.from(document.querySelectorAll('li code a,dd code a')).map(i => i.textContent).filter(i => i.toLowerCase() !== i))).map(i => `:${i.toLowerCase()} :${i}\n`).join('')
(def SVG-ATTRIBUTES {:xmlns :data-xmlns ; to get rid of electric: "DOMException NamespaceError"
                     :attributename :attributeName
                     :attributetype :attributeType
                     :basefrequency :baseFrequency
                     :baseprofile :baseProfile
                     :calcmode :calcMode
                     :clippathunits :clipPathUnits
                     :diffuseconstant :diffuseConstant
                     :edgemode :edgeMode
                     :filterunits :filterUnits
                     :glyphref :glyphRef
                     :gradienttransform :gradientTransform
                     :gradientunits :gradientUnits
                     :kernelmatrix :kernelMatrix
                     :kernelunitlength :kernelUnitLength
                     :keypoints :keyPoints
                     :keysplines :keySplines
                     :keytimes :keyTimes
                     :lengthadjust :lengthAdjust
                     :limitingconeangle :limitingConeAngle
                     :markerheight :markerHeight
                     :markerunits :markerUnits
                     :markerwidth :markerWidth
                     :maskcontentunits :maskContentUnits
                     :maskunits :maskUnits
                     :numoctaves :numOctaves
                     :pathlength :pathLength
                     :patterncontentunits :patternContentUnits
                     :patterntransform :patternTransform
                     :patternunits :patternUnits
                     :pointsatx :pointsAtX
                     :pointsaty :pointsAtY
                     :pointsatz :pointsAtZ
                     :preservealpha :preserveAlpha
                     :preserveaspectratio :preserveAspectRatio
                     :primitiveunits :primitiveUnits
                     :referrerpolicy :referrerPolicy
                     :refx :refX
                     :refy :refY
                     :repeatcount :repeatCount
                     :repeatdur :repeatDur
                     :requiredextensions :requiredExtensions
                     :requiredfeatures :requiredFeatures
                     :specularconstant :specularConstant
                     :specularexponent :specularExponent
                     :spreadmethod :spreadMethod
                     :startoffset :startOffset
                     :stddeviation :stdDeviation
                     :stitchtiles :stitchTiles
                     :surfacescale :surfaceScale
                     :systemlanguage :systemLanguage
                     :tablevalues :tableValues
                     :targetx :targetX
                     :targety :targetY
                     :textlength :textLength
                     :viewbox :viewBox
                     :xchannelselector :xChannelSelector
                     :ychannelselector :yChannelSelector
                     :zoomandpan :zoomAndPan
                     :autoreverse :autoReverse})

(defn rename-svg-attributes [attrs]
  (set/rename-keys attrs SVG-ATTRIBUTES))

(defn text-str [ns t]
  (if-not (empty? (str/trim t)) (str "(" ns "/text "  (pr-str t) ")") ""))

(defn props-str [ns attrs]
  (letfn [(attr-str [{:keys [class] :as attrs}]
            (let [class-line (if class
                               (str (pr-str :class class) (if (> (count attrs) 1) \newline ""))
                               "")]
              (str class-line (str/join " " (map (partial apply pr-str) (dissoc attrs :class))))))]
    (str "(" ns "/props {" (attr-str attrs) "})")))

(defn comment-str [content]
  (str "(comment " (pr-str content) ")"))

(defn electric-component-str
  "Wrap body in an electric component."
  [body]
  (str "(e/defn Component" "\n"
       " []" "\n"
       " (e/client\n"
       "  " body ")" ")"))

(defn element-str [ns def-ns tag attrs content]
  (str "(" ns "/" (name tag)
       (if (empty? content)
         (str " " (props-str def-ns attrs))
         (str
          (if *element-props-on-new-line* "\n" " ")
          (props-str def-ns attrs)
          (if *element-content-on-new-line* "\n" " ")
          content)) ")"))

(defn siblings-str [coll]
  (->> coll (str/join (if *siblings-on-new-line* "\n" " "))))

(tests
 (rename-svg-attributes {:width 1 :viewbox "0 0 1 1"}) := {:width 1 :viewBox "0 0 1 1"})

(tests
 (text-str "dom" "hello") := "(dom/text \"hello\")"
 (text-str "dom" " \t  \n ") := "")

(tests
 (props-str "dom"  {:a 1 :b 2}) := "(dom/props {:a 1 :b 2})"
 (props-str "dom"  {:a 1 :b 2 :class "ok" :d 3}) :=
 "(dom/props {:class \"ok\"\n:a 1 :b 2 :d 3})")

(tests
 (comment-str "Hello") :=
 "(comment \"Hello\")")

(tests
 (electric-component-str "(dom/div\n(dom/props {})\n(dom/text \"ok\"))") :=
 (str "(e/defn Component\n"
      " []\n"
      " (e/client\n"
      "  (dom/div\n"
      "(dom/props {})\n(dom/text \"ok\"))))"))

(tests
 (doseq [param [true false]]
   ;; siblings-str
   (binding [*siblings-on-new-line* param]
     (siblings-str []) := ""
     (siblings-str ["a" "b" 1 2 3]) :=
     (if param "a\nb\n1\n2\n3" "a b 1 2 3"))

   ;; element-str
   (binding [*element-props-on-new-line* param
             *element-content-on-new-line* param]
     (element-str "dom" "dom" :div {:class "k"} nil) := "(dom/div (dom/props {:class \"k\"}))"
     (element-str "dom" "dom" :div {:class "k"} "") := "(dom/div (dom/props {:class \"k\"}))"
     (element-str "dom" "dom" :div {:class "k"} "(dom/text \"Hello\")") :=
     (str "(dom/div"
          (if param "\n" " ")
          "(dom/props {:class \"k\"})"
          (if param "\n" " ")
          "(dom/text \"Hello\"))"))

   (element-str "svg" "dom" :g {:class "k"} "") :=
   "(svg/g (dom/props {:class \"k\"}))")

 :rcf)
