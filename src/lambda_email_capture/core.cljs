;; This project provides a quick AWS Lambda function to save email addresses to
;; a Datomic database. This is also an attempt to fully flesh out a project with
;; complete documentation, schemas, and tests.
;;
(ns lambda-email-capture.core
  (:require [cljs-lambda.macros :refer-macros [deflambda]]
            [cljs.core.async :as a]
            [cljs.core.async.impl.protocols :as ap]
            [clojure.string :as string]
            [datomic-cljs.api :as d]
            [schema.core :as s :include-macros true])
  (:require-macros [cljs.core.async.macros :refer [go]]))

;; # Schemas

(def ReadPort (s/protocol ap/ReadPort))
(def Handler (s/=> ReadPort {s/Keyword s/Any}))
(def EmailCaptureEvent {:body    {:email s/Str}
                        :datomic {:hostname s/Str
                                  :port     s/Num
                                  :alias    s/Str
                                  :database s/Str}})

;; # Pre Middleware

;; ## Schema Validate
;;
;; Before we start doing anything with a request, we need to make sure that it
;; follows the EmailCaptureEvent schema.
;;
(s/defn wrap-schema-validate :- Handler
  "Middleware that validates the request follows schema"
  [handler :- Handler, schema :- s/Any]
  (s/fn :- ReadPort [request :- ReadPort]
    (go (let [request (a/<! request)]
          (try (s/validate schema request)
               (a/<! (handler (go request)))
               (catch js/Error e
                 e))))))

;; ## Datomic Connection
;;
;; The next thing we need to do with a request is to create a Datomic connection
;; using the given connection parameters.
;;
(s/defn wrap-datomic-connection :- Handler
  "Middleware to add a DatomicConnection to the request"
  [handler :- Handler]
  (s/fn :- ReadPort [request :- ReadPort]
    (go (let [{{:keys [hostname port alias database]} :datomic
               :as                                    request}
              (a/<! request)]
          (a/<! (handler (go (assoc request
                               :datomic-connection (d/connect hostname
                                                              port
                                                              alias
                                                              database)))))))))

;; # Endpoint
;;
;; After we have our connection, we can now add the email capture to it. We also
;; take this opportunity to format the email.
;;
(s/defn email-capture-endpoint :- ReadPort
  "Endpoint which adds :body as a capture to :datomic-connection"
  [request :- ReadPort]
  (go (let [{{:keys [email]} :body
             :keys           [datomic-connection]}
            (a/<! request)]
        (a/<! (d/transact datomic-connection
                          [{:db/id         (d/tempid :db.part/user)
                            :capture/email (-> email
                                               string/lower-case
                                               string/trim)}])))))

;; # Post Middleware

;; ## Acknowledge
;;
;; Lastly, if everything went smoothly -- a.k.a. we didn't get a js/Error --
;; then send back "acknowledged". Otherwise, pass along the error.
;;
(s/defn wrap-acknowledge :- Handler
  "Returns \"acknowledge\" or passes on returned value if it's an error"
  [handler :- Handler]
  (s/fn :- ReadPort [request :- ReadPort]
    (go (let [result (a/<! (handler request))]
          (if (instance? js/Error result)
            result
            "acknowledged")))))

;; # Handler
;;
;; We package up our endpoint and middleware up into a nice handler that we'll
;; call from our Lambda definition.
;;
(def email-capture-handler (-> email-capture-endpoint
                               wrap-acknowledge
                               wrap-datomic-connection
                               (wrap-schema-validate EmailCaptureEvent)))

;; # AWS Lambda Definition
;;
;; ## Input
;;
;; Inputs to AWS Lambda functions come in the form of two parameters, the event
;; and context. The context provides information about the execution as well as
;; callbacks to signal success or faliure. Because cljs-lambda abstracts away
;; the need to interact with these callbacks and because we are only interested
;; in the event, we can ignore the context.
;;
;; The event must conform to the EmailCaptureEvent schema. The value at
;; `[:body :email]` is what gets recorded in the capture and the `:datomic` map
;; describes the Datomic database where the capture should be recorded.
;;
;; ## Output
;;
;; If the function executes correctly, it will return the string
;; `"acknowledged"`. Otherwise, it will return an `Error` instance.
;;
(deflambda email-capture-lambda
  "Lambda which adds email captures to a Datomic database"
  [event _]
  (email-capture-handler (go event)))

;; # Miscellaneous

;; For optimizations :advanced to work, we need to set `*main-cli-fn*` to a
;; function. Preferably one that doesn't do anything -- including crashing.
(set! *main-cli-fn* identity)
