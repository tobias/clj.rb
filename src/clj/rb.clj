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

(defn raw-eval
    "Evaluates the `script` String in `rt`, returning the raw result.

  `clojure.core/format` is applied to `script` and `args`.

  Note that the Ruby runtime automatically does some conversion by
  calling .toJava() on the return value, which will convert scalar
  types where appropriate, and convert Ruby objects to their JRuby
  java implementation equivalents."
  [^ScriptingContainer rt script & args]
  (.runScriptlet rt (apply format script args)))

(defn eval
  "Evaluates the `script` String in `rt`, applying `rb->clj` to the result.

  `clojure.core/format` is applied to `script` and `args`."
  [rt script & args]
  (rb->clj (apply raw-eval rt script args)))

(defn raw-eval-file
  "Evaluates the `file` in `rt`, returning the raw result.

  Note that the Ruby runtime automatically does some conversion by
  calling .toJava() on the return value, which will convert scalar
  types where appropriate, and convert Ruby objects to their JRuby
  java implementation equivalents."
  [^ScriptingContainer rt ^File file]
  (.runScriptlet rt (io/reader file) (.getPath file)))

(defn eval-file
  "Evaluates `file` in `rt`, applying `rb->clj` to the result."
  [rt file]
  (rb->clj (raw-eval-file rt file)))

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

  * :preserve-locals? [false]
  * :load-paths [nil]
  * :env [nil]
  * :gem-path [nil]"
  ([] (runtime nil))
  ([{:keys [preserve-locals? gem-path load-paths env]}]
     (let [rt (ScriptingContainer. (if preserve-locals?
                                     LocalVariableBehavior/PERSISTENT
                                     LocalVariableBehavior/TRANSIENT))]
       (.setLoadPaths rt (conj load-paths
                           (.toExternalForm (io/resource "clj-ruby-helpers"))))
       (when-let [path (seq gem-path)]
         (setenv rt "GEM_PATH" (str/join ":" (map pr-str path))))
       (doseq [[k v] env]
         (setenv rt k v))
       (require "rubygems")
       rt)))


(defn install-gem
  "* :install-dir [nil]
  * :force? [false]
  * :ignore-dependencies? [false]
  "
  ([rt name version]
     (install-gem rt name version nil))
  ([rt name version {:keys [ignore-dependencies? force? sources install-dir]}]
     (let [helper (rb-helper rt)]
       (if (call-method rt helper "gem_installed?" name version)
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
