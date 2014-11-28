(ns clj.rb-test
  (:refer-clojure :exclude [eval require])
  (:require [clojure.test :refer :all]
            [clj.rb :refer :all]))

(def rt (atom nil))

(use-fixtures :each
  (fn [f]
    (reset! rt (runtime))
    (try
      (f)
      (finally
        (shutdown-runtime @rt)
        (reset! rt nil)))))

(defn rb-helper []
  (#'clj.rb/rb-helper @rt))

(defn assert-in-ruby [exp actual]
  (.callMethod @rt (rb-helper) "assert" (object-array [exp actual])))

(deftest test-clj->rb
  (are [clj rb] (assert-in-ruby (clj->rb clj @rt) (.runScriptlet @rt rb))
       :foo                ":foo"
       "foo"               "'foo'"
       {:foo :bar}         "{:foo => :bar}"
       {:foo {:bar "baz"}} "{:foo => {:bar => 'baz'}}"
       nil                  "nil"
       [1 :foo]             "[1, :foo]"
       '(1 :foo)            "[1, :foo]"
       1                    "1"
       1.0                  "1.0"))

(deftest test-rb->clj
  (are [rb clj] (= (rb->clj (.runScriptlet @rt rb)) clj)
       ":foo"                       :foo
       "'foo'"                      "foo"
       "{:foo => :bar}"             {:foo :bar}
       "{:foo => {:bar => 'baz'}}"  {:foo {:bar "baz"}}
       "nil"                        nil
       "1"                          1
       "1.0"                        1.0
       "[1, :foo]"                  [1 :foo]))

(deftest test-clj->rb-rb->clj-round-trip
  (are [clj] (= clj (rb->clj (clj->rb clj @rt)))
       :foo
       "foo"
       {:foo :bar}
       {:foo {:bar "baz"}}
       nil
       [1 :foo]
       '(1 :foo)
       1
       1.0))

(deftest call-method-should-coerce-arguments
  (is (call-method @rt
        (rb-helper) "assert" (clj->rb :foo @rt) :foo)))

(deftest call-method-should-coerce-return-value
  (is (= :foo (call-method @rt
                (rb-helper) "identity" (clj->rb :foo @rt)))))

(deftest eval-should-coerce-return-value
  (is (= :foo (eval @rt ":foo"))))

(deftest setvar-getvar
  (setvar @rt "foo" :foo)
  (is (= :foo (getvar @rt "foo"))))
