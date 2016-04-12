;; This project provides a quick AWS Lambda function to save email addresses to
;; a Datomic database. This is also an attempt to fully flesh out a project with
;; complete documentation, schemas, and tests.
;;
(ns lambda-email-capture.core
  (:require [cljs-lambda.macros :refer-macros [deflambda]]
            [cljs.core.async :as a]
            [cljs.core.async.impl.protocols :as ap]
            [datomic-cljs.api :as d]
            [schema.core :as s :include-macros true]
            [clojure.string :as string])
  (:require-macros [cljs.core.async.macros :refer [go]]))

;; # Schemas

(def ReadPort (s/protocol ap/ReadPort))
(def Handler (s/=> {s/Keyword s/Any} ReadPort))

;; # Middleware

;; The first thing we need to do with a request is to create a Datomic
;; connection using the given connection parameters.
;;
(s/defn wrap-datomic-connection :- Handler [handler :- Handler]
  "Middleware to add a DatomicConnection to the request"
  (s/fn :- ReadPort [request :- {:datomic  {:hostname s/Str
                                            :port     s/Num
                                            :alias    s/Str
                                            :database s/Str}
                                 s/Keyword s/Any}]
    (handler (let [{{:keys [hostname port alias database]} :datomic} request
                   connection (d/connect hostname port alias database)]
               (assoc request :datomic-connection connection)))))

;; # Endpoint
;;
;; After we have our connection, we can now add the email capture to it. We also
;; take this opportunity to format the email.
;;
(s/defn email-capture-endpoint :- ReadPort
  "Endpoint which adds :body as a capture to :datomic-connection"
  [{{:keys [email]} :body
    :keys           [datomic-connection]}
   :- {:body               {:email s/Str, s/Keyword s/Any}
       :datomic-connection d/DatomicConnection
       s/Keyword           s/Any}]
  (go (a/<! (d/transact datomic-connection
                        [{:db/id         (d/tempid :db.part/user)
                          :capture/email (-> email
                                             string/lower-case
                                             string/trim)}]))))

;; # Handler
;;
;; We package up our endpoint and middleware up into a nice handler that we'll
;; call from our Lambda definition.
;;
(def email-capture-handler (-> email-capture-endpoint wrap-datomic-connection))

;; # AWS Lambda Definition

;; ## Input
;;
;; Inputs to AWS Lambda functions come in the form of two parameters, the event
;; and context. The context provides information about the execution as well as
;; callbacks to signal success or faliure. Because cljs-lambda abstracts away
;; the need to interact with these callbacks and because we are only interested
;; in the event, we can ignore the context.
;;
;; The event must conform to the Event schema. The value at `[:body :email]` is
;; what gets recorded in the capture and the `:datomic` map describes the
;; Datomic database where the capture should be recorded.
;;
;; ## Output
;;
;; If the function executes correctly, it will return the string
;; `"acknowledged"`. Otherwise, it will return an `Error` instance.
;;
(def Event {:body    {:email s/Str}
            :datomic {:hostname s/Str
                      :port     s/Num
                      :alias    s/Str
                      :database s/Str}})
(deflambda email-capture-lambda [event _]
  "Lambda which adds email captures to a Datomic database"
  (try (s/validate Event event)
       (let [result (email-capture-handler event)]
         (if (instance? js/Error result) result "acknowledged"))
       (catch js/Error e
         e)))

;; # Miscellaneous

;; For optimizations :advanced to work, we need to set `*main-cli-fn*` to a
;; function. Preferably one that doesn't do anything -- including crashing.
(set! *main-cli-fn* identity)
