(ns lambda-email-capture.core-test
  (:require [lambda-email-capture.core :as core]
            [cljs.test
             :as t
             :include-macros true
             :refer-macros [deftest is testing]]
            [cljs-lambda.local :refer [channel]]
            [cljs.core.async :refer [<!]]
            [datomic-cljs.api :as d]
            [schema.core :as s])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(s/set-fn-validation! true)

(deftest email-capture-lambda-test
  (t/async done
    (go (t/testing "The critical path"
          (let [transact-atom (atom nil)]
            (with-redefs [datomic-cljs.api/transact (fn [& xs]
                                                      (reset! transact-atom xs)
                                                      (go true))
                          datomic-cljs.api/tempid (fn [& xs] xs)]
              (let [[tag response]
                    (<! (channel core/email-capture-lambda
                                 {:body    {:email "email"}
                                  :datomic {:hostname "hostname"
                                            :port     80
                                            :alias    "alias"
                                            :database "database"}}))]
                (is (= tag :succeed))
                (is (= response "acknowledged"))
                (is (= @transact-atom
                       [(d/connect "hostname" 80 "alias" "database")
                        [{:db/id         [:db.part/user]
                          :capture/email "email"}]])))))
          (t/testing "with email formatting"
            (let [transact-atom (atom nil)]
              (with-redefs [datomic-cljs.api/transact
                            (fn [& xs] (reset! transact-atom xs) (go true))

                            datomic-cljs.api/tempid
                            (fn [& xs] xs)]
                (let [[tag response]
                      (<! (channel core/email-capture-lambda
                                   {:body    {:email "\t EmAiL AdDrEsS \r\n"}
                                    :datomic {:hostname "hostname"
                                              :port     80
                                              :alias    "alias"
                                              :database "database"}}))]
                  (is (= tag :succeed))
                  (is (= response "acknowledged"))
                  (is (= @transact-atom
                         [(d/connect "hostname" 80 "alias" "database")
                          [{:db/id         [:db.part/user]
                            :capture/email "email address"}]])))))))
        (t/testing "The failure path"
          (with-redefs [datomic-cljs.api/transact (fn [& xs]
                                                    (go (js/Error. "Error")))]
            (let [[tag response]
                  (<! (channel core/email-capture-lambda
                               {:body    {:email "email address"}
                                :datomic {:hostname "hostname"
                                          :port     80
                                          :alias    "alias"
                                          :database "database"}}))]
              (is (= tag :fail))
              (is (= (.-message ^js/Error response) "Error")))))
        (done))))
