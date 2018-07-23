(ns clj-nakadi-java.core
  (:require [cheshire.core :as json]
            [clojure.reflect :as r])
  (:import (nakadi NakadiClient
                   TokenProvider
                   TypeLiterals
                   StreamConfiguration
                   StreamObserverProvider
                   StreamObserver
                   StreamOffsetObserver)
           (java.util Optional)))


(defn make-token-provider [get-token-fn]
  (reify TokenProvider
    (authHeaderValue [_ scope]
      (Optional/of (str "Bearer " (get-token-fn))))))


(defn make-client [nakadi-url get-token-fn]
  (-> (NakadiClient/newBuilder)
      (.baseURI nakadi-url)
      (.tokenProvider (make-token-provider get-token-fn))
      (.build)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn make-observer-provider [callback]
  (reify StreamObserverProvider
    (createStreamObserver [_]
      (reify StreamObserver
        (onStart [_])
        (onStop [_])
        (onCompleted [_])
        (onError [_ e])
        (onNext [_ rec]
          (let [events          (-> rec .streamBatch .events)
                cursor-context  (-> rec .streamCursorContext)
                offset-observer (-> rec .streamOffsetObserver)]
            (doseq [e events]
              (callback (json/parse-string e keyword)))
            (when-not (empty? events)
              (.onNext offset-observer cursor-context))))
        (requestBackPressure [_] (Optional/empty))
        (requestBuffer [_] (Optional/empty))))
    (typeLiteral [_] TypeLiterals/OF_STRING)))


(defn method-exists? [method]
  (first (filter #(= (str (:name %)) method) (:members (r/reflect StreamConfiguration)))))


(defn ->camel-case [kebab]
  (let [name-parts (clojure.string/split (name kebab) #"\-")]
    (str (first name-parts) (apply str (map clojure.string/capitalize (rest name-parts))))))


(defn str-invoke [instance method-str & args]
  (clojure.lang.Reflector/invokeInstanceMethod instance method-str (to-array args)))


(defn set-param [streamConfigInstance k v]
  (let [method (->camel-case k)]
    (when (method-exists? method)
      (str-invoke streamConfigInstance method v))
    streamConfigInstance))


(defn ->subscription-stream-config
  "Converts a hash-map to an instance of StreamConfiguration.
  Keys which do not correspond to variables of StreamConfiguration are ignored."
  [config-map]
  (reduce-kv set-param (StreamConfiguration.) config-map))


(defmulti consume-subscription
  "Starts consuming a subscription.
  Config can be either a string, a map, or an instance of StreamConfiguration.
  Strings and maps are internally converted to instances of StreamConfiguration.
  In case config is a map, its keys must correspond to the variables of StreamConfiguration.
  In case config is a string, it must be the subscription id."
  (fn [client config callback] (class config)))


(defmethod consume-subscription nakadi.StreamConfiguration
  [client stream-config callback]
  (let [stream-processor (-> client
                             (.resources)
                             (.streamBuilder stream-config)
                             (.streamObserverFactory (make-observer-provider callback))
                             (.build))]
    (.start stream-processor)
    (fn [] (.stop stream-processor))))


(defmethod consume-subscription clojure.lang.PersistentArrayMap
  [client config-map callback]
  {:pre [(:subscription-id config-map)]}
  (consume-subscription client (->subscription-stream-config config-map) callback))


(defmethod consume-subscription java.lang.String
  [client subscription-id callback]
  (consume-subscription client (->subscription-stream-config {:subscription-id subscription-id}) callback))


(defmethod consume-subscription :default
  [client config callback]
  (throw (AssertionError. "Invalid configuration. Configuration must either be a string, a map, or an instance of StreamConfiguration.")))


(defn ->raw-event-stream-config [event-type-or-stream-config]
  (if (string? event-type-or-stream-config)
    (-> (StreamConfiguration.)
        (.eventTypeName event-type-or-stream-config))
    (do
      (assert (.eventTypeName event-type-or-stream-config)
              "Stream configuration must include eventTypeName.")
      event-type-or-stream-config)))


(def ^:private noop-stream-offset-observer
  (reify StreamOffsetObserver
    (onNext [_ _])))


(defn consume-raw-events [client event-type-or-stream-config callback]
  (let [stream-config    (->raw-event-stream-config event-type-or-stream-config)
        stream-processor (-> client
                             (.resources)
                             (.streamBuilder stream-config)
                             (.streamObserverFactory (make-observer-provider callback))
                             (.streamOffsetObserver noop-stream-offset-observer)
                             (.build))]
    (.start stream-processor)
    (fn [] (.stop stream-processor))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn publish-events [client event-type events]
  (let [event-resource (-> client .resources .events)]
    (.send event-resource event-type (map json/generate-string events))))
