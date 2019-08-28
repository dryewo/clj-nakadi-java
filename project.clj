(defproject me.dryewo/clj-nakadi-java "0.0.8"
  :description "Nakadi client in Clojure. Thin wrapper around nakadi-java."
  :url "http://github.com/dryewo/clj-nakadi-java"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[net.dehora.nakadi/nakadi-java-client "0.13.0"]
                 [cheshire "5.9.0"]]
  :repositories [["bintray" {:url "https://dl.bintray.com/dehora/maven"}]]
  :plugins [[lein-cloverage "1.0.13"]
            [lein-shell "0.5.0"]
            [lein-ancient "0.6.15"]
            [lein-changelog "0.3.2"]]
  :aliases {"update-readme-version" ["shell" "sed" "-i" "s/\\\\[me\\\\.dryewo\\\\/clj-nakadi-java \"[0-9.]*\"\\\\]/[me\\\\.dryewo\\\\/clj-nakadi-java \"${:version}\"]/" "README.md"]}
  :release-tasks [["shell" "git" "diff" "--exit-code"]
                  ["change" "version" "leiningen.release/bump-version"]
                  ["change" "version" "leiningen.release/bump-version" "release"]
                  ["changelog" "release"]
                  ["update-readme-version"]
                  ["vcs" "commit"]
                  ["vcs" "tag"]
                  ["deploy"]
                  ["vcs" "push"]]
  :deploy-repositories [["releases" :clojars]]
  :profiles {:dev {:dependencies [[org.clojure/clojure "1.10.1"]]}})
