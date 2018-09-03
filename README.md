# clj-nakadi-java
[![Build Status](https://travis-ci.org/dryewo/clj-nakadi-java.svg?branch=master)](https://travis-ci.org/dryewo/clj-nakadi-java)
[![Clojars Project](https://img.shields.io/clojars/v/me.dryewo/clj-nakadi-java.svg)](https://clojars.org/me.dryewo/clj-nakadi-java)

[Nakadi] client in Clojure. Thin wrapper around [nakadi-java].

```
[me.dryewo/clj-nakadi-java "0.0.5"]
```

## Usage

Recommended way:

*project.clj:*
```clj
(defproject my/project "0.1.0-SNAPSHOT"
    ...
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [functionalbytes/mount-lite "2.1.1"]
                 [cyrus/config "0.2.2"]
                 [me.dryewo/clj-nakadi-java "0.0.5"]])
```

*my-project.clj:*
```clj
(ns my.project.events
  (:require [cyrus-config.core :as cfg]
            [mount.lite :as m]
            [clojure.string :as str]
            [clj-nakadi-java.core :as nakadi]))


(cfg/def NAKADI_URL {:required true})
(cfg/def SUBSCRIPTION_ID {:required true})
(cfg/def ACCESS_TOKEN {:required true})
(cfg/def BATCH_LIMIT)


(m/defstate client
  :start (nakadi/make-client NAKADI_URL (fn [] ACCESS_TOKEN)))


(defn callback [event]
  (println "Processed" (pr-str event)))


(m/defstate consumer
  :start (do
           (nakadi/consume-subscription @client SUBSCRIPTION_ID callback)
           ;(nakadi/consume-subscription @client {:subscription-id SUBSCRIPTION_ID :batch-limit BATCH_LIMIT} callback)
           ;(nakadi/consume-subscription @client stream-config callback)
           ;(nakadi/consume-raw-events @client "foobar.event" callback)
           )
  :stop (time (@consumer)))


(comment
  (nakadi/publish-events @client "foobar.event" [{:foo "bar"}])
  (nakadi/publish-events @client "foobar.event" (for [i (range 10)]
                                                      {:x i})))
```

## License

Copyright © 2018 Dmitrii Balakhonskii

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

[Nakadi]: https://zalando.github.io/nakadi/
[nakadi-java]: https://github.com/dehora/nakadi-java
