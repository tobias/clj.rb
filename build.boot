(set-env!
  :dependencies '[[org.clojure/clojure "1.6.0" :scope "provided"]
                  [org.jruby/jruby-complete "1.7.16.1"]]
  :src-paths    #{"src"}
  :rsc-paths    #{"resources"})

(def +version+ "0.1.0")

(task-options!
  pom [:project 'clj.rb
       :version +version+
       :description "Utils for using JRuby from Clojure"
       :url "https://github.com/tobias/clj.rb"
       :scm {:url "https://github.com/tobias/clj.rb"}
       :license {:name "Eclipse Public License"
                 :url "http://www.eclipse.org/legal/epl-v10.html"}])

(deftask build
  "Build and install the artifact."
  []
  (comp (pom) (add-src) (jar) (install)))

(deftask test
  "Run tests"
  []
  (with-pre-wrap []
    (set-env! :src-paths #(conj % "test"))
    (require 'clojure.test 'clj.rb-test)
    (eval '(clojure.test/run-tests `clj.rb-test))))
