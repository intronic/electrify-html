(ns build
  (:require [clojure.tools.build.api :as b]
            [org.corfield.build :as bb]))

(def lib 'com.github.intronic/electrify-html)
(def version (format "1.0.%s" (b/git-count-revs nil)))
(def defaults
  {:lib     lib
   :version version
   :basis (b/create-basis {})
   :class-dir "target/classes"
   :scm {:url "https://github.com/intronic/electrify-html"
         :connection "scm:git:https://github.com/intronic/electrify-html.git"
         :developerConnection "scm:git:ssh:git@github.com:intronic/electrify-html.git"
         :tag (str "v" version)}
   :pom-data [[:description "Generate Electric DOM code from HTML fragments."]
              [:url "https://github.com/intronic/electrify-html"]
              [:licenses
               [:license
                [:name "EPL-2.0"]
                [:url "https://www.eclipse.org/legal/epl-2.0/"]]]
              [:developers
               [:developer
                [:name "Mike Pheasant"]]]]})

(defn clean [opts]
  (bb/clean opts))

(defn write-pom [opts]
  (b/write-pom (merge defaults opts)))

(defn run-tests [opts]
  (bb/run-tests (merge defaults opts)))

(defn jar [opts]
  (bb/jar (merge defaults opts)))

(defn install [opts]
  (bb/install (merge defaults opts)))

(defn git-tag-version "Apply and push git version tag." [_]
  (b/git-process {:git-args ["tag" (str "v" version)]})
  (b/git-process {:git-args ["push" "origin" "tag" (str "v" version)]}))

(defn ci "Run the CI pipeline of tests (and build the JAR)." [opts]
  (-> (merge defaults opts)
      (bb/run-tests)
      (bb/clean)
      (bb/jar)))

(defn deploy
  "Deploy the JAR to Clojars."
  [opts]
  (bb/deploy (merge defaults opts)))
