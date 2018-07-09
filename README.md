# clj-nakadi-java
[![Build Status](https://travis-ci.org/dryewo/clj-nakadi-java.svg?branch=master)](https://travis-ci.org/dryewo/clj-nakadi-java)
[![Clojars Project](https://img.shields.io/clojars/v/me.dryewo/clj-nakadi-java.svg)](https://clojars.org/me.dryewo/clj-nakadi-java)

[Nakadi] client in Clojure. Thin wrapper around [nakadi-java].

```
[me.dryewo/clj-nakadi-java "0.0.2"]
```

## Usage

Recommended way:

*project.clj:*
```clj
(defproject my/project "0.1.0-SNAPSHOT"
    ...
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [functionalbytes/mount-lite "2.1.0"]
                 [cyrus/config "0.1.0"]
                 [me.dryewo/clj-nakadi-java "0.0.2"]])
```

*my-project.clj:*
```clj
(ns my.project.events
  (:require [cyrus-config.core :as cfg]
            [mount.lite :as m]
            [clojure.string :as str]
            [clj-nakadi-java.core :as nakadi]))


(cfg/def nakadi-url {:required true})
(cfg/def subscription-id {:required true})
(cfg/def access-token {:required true})
(cfg/def batch-limit)


(m/defstate client
  :start (nakadi/make-client nakadi-url (fn [] access-token)))


(defn callback [event]
  (println "Processed" (pr-str event)))


(m/defstate consumer
  :start (do
           (nakadi/consume-subscription @client {:subscription-id subscription-id :batch-limit batch-limit} callback)
           ;(nakadi/consume-raw-events @client "foobar.event" callback)
           )
  :stop (time (@consumer)))


(comment
  (nakadi/publish-events @client "foobar.event" [{:foo "bar"}])
  (nakadi/publish-events @client "foobar.event" (for [i (range 10)]
                                                      {:x i})))
```

Currently, the following stream parameters are supported:


| parameter                | type | description                                                                                                                                                   | required |           default |
| ---                      | ---  | ---                                                                                                                                                           | ---      |               --- |
| :subscription-id         | int  |                                                                                                                                                               | true     |                   |
| :batch-limit             | int  | Maximum number of Events in each batch of the stream                                                                                                          | false    |                 1 |
| :max-retry-attempts      | int  |                                                                                                                                                               | false    | Integer.MAX_VALUE |
| :max-uncommited-events   | int  |                                                                                                                                                               | false    |                10 |
| :stream-keep-alive-limit | int  | Set the maximum number of empty keep alive batches to get in a row before closing. If 0 or undefined will send keep alive messages indefinitely.              | false    |                 0 |
| :stream-limit            | ini  | Set the maximum number of Events in this stream (over all partitions being streamed in this connection). If 0 or undefined, will stream batches indefinitely. | false    |                 0 |



## License

Copyright © 2018 Dmitrii Balakhonskii

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

[Nakadi]: https://zalando.github.io/nakadi/
[nakadi-java]: https://github.com/dehora/nakadi-java
