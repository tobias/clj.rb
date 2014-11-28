(set-env!
  :dependencies '[[org.clojure/clojure "1.6.0" :scope "provided"]
                  [org.jruby/jruby-complete "1.7.16.1"]]
  :src-paths    #{"src"}
  :rsc-paths    #{"resources"})

  (def +version+ "0.3.0")

(task-options!
  pom  [:project 'clj.rb
        :version +version+
        :description "Utils for using JRuby from Clojure"
        :url "https://github.com/tobias/clj.rb"
        :scm {:url "https://github.com/tobias/clj.rb"}
        :license {:name "Apache Software License - v 2.0"
                  :url "http://www.apache.org/licenses/LICENSE-2.0"}]
  push [:gpg-sign true
        :gpg-user-id "toby@tcrawley.org"
        :repo "clojars"])

(deftask run-tests
  "Run tests"
  []
  (with-pre-wrap []
    (set-env! :src-paths #(conj % "test"))
    (require 'clojure.test 'clj.rb-test)
    (let [{:keys [fail error] :as result} (eval '(clojure.test/run-tests `clj.rb-test))]
      (when (or (< 0 fail) (< 0 error))
        (throw (ex-info "Tests failed" result))))))

(deftask build
  "Build and install the artifact."
  []
  (comp (run-tests) (pom) (add-src) (jar) (install)))
