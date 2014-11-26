(ns clj.rb
  "Tools for interacting with JRuby from Clojure."
  (:refer-clojure :exclude [eval require])
  (:require [clojure.string :as str]
            [clojure.java.io :as io])
  (:import [org.jruby
            RubyArray
            RubyHash
            RubyString
            RubySymbol]
           [org.jruby.embed
            LocalVariableBehavior
            ScriptingContainer]
           org.jruby.runtime.builtin.IRubyObject
           java.io.File))

(defprotocol Clj->Rb
  "A protocol for converting Clojure objects to JRuby implementation equivalents."
  (clj->rb [v rt]
    "Converts `v` to the appropriate Ruby object in runtime `rt`."))

(defprotocol Rb->Clj
  "A protocol for converting JRuby implementation objects to Clojure equivalents."
  (rb->clj [v]
    "Converts `v` to the appropriate Clojure object"))

(defn eval
  "Evaluates the `script` String in `rt`, applying `rb->clj` to the result.

  `clojure.core/format` is applied to `script` and `args`."
  [^ScriptingContainer rt script & args]
  (rb->clj
    (.runScriptlet rt (apply format script args))))

(defn eval-file
  "Evaluates `file` in `rt`, applying `rb->clj` to the result."
  [^ScriptingContainer rt ^File file]
  (rb->clj
    (.runScriptlet rt (io/reader file) (.getPath file))))

(defn call-method
  "Calls method named `method-name` on IRubyObject `obj`, "
  [^ScriptingContainer rt ^IRubyObject obj ^String method-name & args]
  (rb->clj
    (.callMethod rt obj
      method-name (object-array (map #(clj->rb % rt) args)))))

(defn- rb-helper [rt]
  (eval rt "require 'clj_rb_util';CljRbUtil"))

(defn- ruby-runtime [rt]
  (-> rt .getProvider .getRuntime))

(extend-protocol Clj->Rb
  nil
  (clj->rb [_ _]
    nil)

  Object
  (clj->rb [v _]
    v)

  clojure.lang.Keyword
  (clj->rb [v rt]
    (RubySymbol/newSymbol (ruby-runtime rt) (name v)))

  java.util.List
  (clj->rb [v rt]
    (doto (RubyArray/newEmptyArray (ruby-runtime rt))
      (.addAll (map #(clj->rb % rt) v))))

  java.util.Map
  (clj->rb [v rt]
    (reduce
      (fn [h [k v]] (doto h (.put (clj->rb k rt) (clj->rb v rt))))
      (RubyHash. (ruby-runtime rt))
      v)))

(extend-protocol Rb->Clj
  Object
  (rb->clj [v]
    v)

  nil
  (rb->clj [_]
    nil)

  RubyArray
  (rb->clj [v]
    (into [] (map rb->clj v)))

  RubyHash
  (rb->clj [v]
    (->> v
      (map (fn [[k v]] [(rb->clj k) (rb->clj v)]))
      (into {})))

  RubySymbol
  (rb->clj [v]
    (keyword (str v))))

(defn require
  "Requires each of `libs` in `rt`."
  [rt & libs]
  (last (map #(eval rt "require '%s'" %) libs)))

(defn setenv
  "Sets `value` for `key` in the `ENV` hash in `rt`.

  `key` and `value` are both converted to strings."
  [rt key value]
  (eval rt "ENV['%s']='%s'" key value))

(defn runtime
  "Creates a new JRuby runtime.

  Optionally takes a map of options [default]:

  * :preserve-locals? - any locals defined in an eval will persist, visible to future evals [false]
  * :load-paths - a sequence of paths to add to the runtime's load path [nil]
  * :env - a map of values to set in the ruby ENV [nil]
  * :gem-paths - a sequence of paths to search for gems [nil]"
  ([] (runtime nil))
  ([{:keys [preserve-locals? gem-paths load-paths env]}]
     (let [rt (ScriptingContainer. (if preserve-locals?
                                     LocalVariableBehavior/PERSISTENT
                                     LocalVariableBehavior/TRANSIENT))]
       (.setLoadPaths rt (conj load-paths
                           (.toExternalForm (io/resource "clj-ruby-helpers"))))
       (when-let [paths (seq gem-paths)]
         (setenv rt "GEM_PATH" (str/join ":" (map pr-str paths))))
       (doseq [[k v] env]
         (setenv rt k v))
       (require "rubygems")
       rt)))


(defn install-gem
  "Downloads and installs the gem specificied by `name` and `version`.

  Optionally takes a map of options [default]:

  * :sources - a sequence additional gem sources to add to the default list [nil]
  * :install-dir - a path to a directory where the gem should be installed. If nil,
                   the default gem path is used. [nil]
  * :force? - install the gem, even if it is already installed [false]
  * :ignore-dependencies? - don't install the gems dependencies [false]"
  ([rt name version]
     (install-gem rt name version nil))
  ([rt name version {:keys [ignore-dependencies? force? sources install-dir]}]
     (let [helper (rb-helper rt)]
       (if (and (not force?)
             (call-method rt helper "gem_installed?" name version))
         (println (format "%s v%s already installed, skipping." name version))
         (let [curr-sources (eval rt "Gem.sources")
               installer (call-method rt helper "gem_installer"
                           (boolean ignore-dependencies?)
                           (boolean force?)
                           (if install-dir
                             install-dir
                             (call-method rt helper "first_writeable_gem_path")))]
           (try
             (when sources
               (eval rt helper "add_gem_sources" sources))
             (call-method rt installer "install" name version)
             (finally
               (call-method rt helper "add_gem_sources" curr-sources (boolean :replace)))))))))

(defn shutdown-runtime
  "Shuts down the given JRuby runtime."
  [rt]
  (.finalize rt))
