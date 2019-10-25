(ns amperity.gocd.secret.vault.logging
  "Basic logging support code."
  (:require
    [clojure.stacktrace :refer [print-cause-trace]]
    [clojure.string :as str]))


(let [lock (Object.)]
  (defn logger
    [level message ex]
    (locking lock
      (printf "[%s] %s\n" (str/upper-case level) message)
      (when ex
        (print-cause-trace ex)))))


(defmacro ^:private deflevel
  "Define logging functions for the given level."
  [level]
  ^:cljstyle/ignore
  `(do
     (defn ~level
       [message# & args#]
       (logger ~(str level) (apply format message# args#) nil))
     (defn ~(symbol (str level "x"))
       [ex# message# & args#]
       (logger ~(str level) (apply format message# args#) ex#))))


(deflevel debug)
(deflevel info)
(deflevel warn)
(deflevel error)
