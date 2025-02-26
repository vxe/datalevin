(ns datalevin.test.validation
  (:require
   [datalevin.test.core :as tdc :refer [db-fixture]]
   [clojure.test :refer [deftest testing are is use-fixtures]]
   [datalevin.util :as u]
   [datalevin.core :as d])
  (:import
   [java.util UUID]))

(use-fixtures :each db-fixture)

#?(:cljs
   (def Throwable js/Error))

(deftest test-with-validation
  (let [dir (u/tmp-dir (str "query-or-" (UUID/randomUUID)))
        db  (d/empty-db dir {:profile { :db/valueType :db.type/ref }
                             :id      {:db/unique :db.unique/identity}})]
    (are [tx] (thrown-with-msg? Throwable #"Expected number, string or lookup ref for :db/id" (d/db-with db tx))
      [{:db/id #"" :name "Ivan"}])

    (are [tx] (thrown-with-msg? Throwable #"Bad entity attribute" (d/db-with db tx))
      [[:db/add -1 nil "Ivan"]]
      [[:db/add -1 17 "Ivan"]]
      [{:db/id -1 17 "Ivan"}])

    (are [tx] (thrown-with-msg? Throwable #"Cannot store nil as a value" (d/db-with db tx))
      [[:db/add -1 :name nil]]
      [{:db/id -1 :name nil}]
      [[:db/add -1 :id nil]]
      [{:db/id -1 :id "A"}
       {:db/id -1 :id nil}])

    (are [tx] (thrown-with-msg? Throwable #"Expected number or lookup ref for entity id" (d/db-with db tx))
      [[:db/add nil :name "Ivan"]]
      [[:db/add {} :name "Ivan"]]
      [[:db/add -1 :profile #"regexp"]]
      [{:db/id -1 :profile #"regexp"}])

    (is (thrown-with-msg? Throwable #"Unknown operation" (d/db-with db [["aaa" :name "Ivan"]])))
    (is (thrown-with-msg? Throwable #"Bad entity type at" (d/db-with db [:db/add "aaa" :name "Ivan"])))
    (is (thrown-with-msg? Throwable #"Tempids are allowed in :db/add only" (d/db-with db [[:db/retract -1 :name "Ivan"]])))
    (is (thrown-with-msg? Throwable #"Bad transaction data" (d/db-with db {:profile "aaa"})))
    (d/close-db db)
    (u/delete-files dir)))

(deftest test-unique
  (let [dir (u/tmp-dir (str "query-or-" (UUID/randomUUID)))
        db  (d/db-with
              (d/empty-db dir {:name { :db/unique :db.unique/value }})
              [[:db/add 1 :name "Ivan"]
               [:db/add 2 :name "Petr"]])]
    (are [tx] (thrown-with-msg? Throwable #"unique constraint" (d/db-with db tx))
      [[:db/add 3 :name "Ivan"]]
      [{:db/add 3 :name "Petr"}])
    (d/db-with db [[:db/add 3 :name "Igor"]])
    (d/db-with db [[:db/add 3 :nick "Ivan"]])
    (d/close-db db)
    (u/delete-files dir)))
