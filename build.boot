(set-env!
 :dependencies '[[org.clojure/clojure "1.10.1" :scope "provided"]
                 [org.jruby/jruby-complete "9.2.14.0" :scope "provided"]
                 [adzerk/bootlaces "0.2.0" :scope "test"]
                 [adzerk/boot-test "1.2.0" :scope "test"]])

(require
  '[adzerk.bootlaces :refer :all]
  '[adzerk.boot-test :refer :all])

(def +version+ "0.3.0")

(bootlaces! +version+)

;; bootlaces! blindly sets :resource-paths, so we have to set it afterward
(set-env! :resource-paths #{"src" "resources" "test"})

(task-options!
  pom  {:project 'clj.rb
        :version +version+
        :description "Utils for using JRuby from Clojure"
        :url "https://github.com/tobias/clj.rb"
        :scm {:url "https://github.com/tobias/clj.rb"}
        :license {:name "Apache Software License - v 2.0"
                  :url "http://www.apache.org/licenses/LICENSE-2.0"}}
  test {:namespaces '[clj.rb-test]})

(deftask build
  "Run tests; build and install the jar."
  []
  (comp (test) (build-jar)))
