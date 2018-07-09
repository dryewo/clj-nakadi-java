(ns clj-nakadi-java.core
  (:require [cheshire.core :as json])
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


(defn set-param
  "Sets properties of a StreamConfiguration instance.
  Takes the value of key from config-map and uses the supplied
  method to update the instance of StreamConfiguration.
  Parameters are optional by default."
  ([StreamConfiguration, config-map, key, method, required]
   (if-let [value (key config-map)]
     (method StreamConfiguration value)
     (do
       (if required
         (assert (key config-map) (format "Stream configuration must include %s" key))
         StreamConfiguration))))

  ([StreamConfiguration, config-map, key, method]
   (set-param StreamConfiguration config-map key method false)))


(defn ->subscription-stream-config [config-map]
  (-> (StreamConfiguration.)
      (set-param config-map :subscription-id #(.subscriptionId % %2) true)  ;; required
      (set-param config-map :batch-limit #(.batchLimit % %2))
      (set-param config-map :max-retry-attempts #(.maxRetryAttempts % %2))
      (set-param config-map :max-uncommitted-events #(.maxUncommittedEvents % %2))
      (set-param config-map :stream-keep-alive-limit #(.streamKeepAliveLimit % %2))
      (set-param config-map :stream-limit #(.streamLimit % %2))
      ))


(defn consume-subscription [client config-map callback]
  (let [stream-config    (->subscription-stream-config config-map)
        stream-processor (-> client
                             (.resources)
                             (.streamBuilder stream-config)
                             (.streamObserverFactory (make-observer-provider callback))
                             (.build))]
    (.start stream-processor)
    (fn [] (.stop stream-processor))))


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
