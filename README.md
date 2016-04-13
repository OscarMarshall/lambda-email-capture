# Lambda Email Capture

This project provides a quick AWS Lambda function to save email addresses to a
Datomic database.

## Setting Up

### Datomic Schema

Before the code can work, a Datomic database with this schema needs to be set
up.

```clojure
[{:db/id                 #db/id[:db.part/db]
  :db/ident              :capture/email
  :db/valueType          :db.type/string
  :db/cardinality        :db.cardinality/one
  :db/unique             :db.unique/identity
  :db/doc                "A capture's email"
  :db.install/_attribute :db.part/db}]
```

### AWS API Gateway

If this Lambda function is being set up to capture emails sent in POST requests
with the body matching `{"email": "..."}`, then a Resource needs to be created
with a POST Method that invokes the Lambda function with the following Mapping
Template.

#### Request Body Mapping Template

* application/json

```javascript
{
  "body":    $input.json('$'),
  "datomic": {
    "hostname": "$stageVariables.datomicHostname",
    "port":     $stageVariables.datomicPort,
    "alias":    "$stageVariables.datomicAlias",
    "database": "$stageVariables.datomicEmailCaptureDatabase"
  }
}
```

The referenced stage variables will also need to be filled in.

## Deploying

Run `lein cljs-lambda default-iam-role` if you don't have yet have suitable
execution role to place in your project file.  This command will create an IAM
role under your default (or specified) AWS CLI profile, and modify your project
file to specify it as the execution default.

Otherwise, add an IAM role ARN under the function's `:role` key in the
`:functions` vector of your profile file, or in
`[:cljs-lambda :defaults :role]`.

Then:

```sh
$ lein cljs-lambda deploy
$ lein cljs-lambda invoke email-capture '
> {
>   "body": {
>     "email": "..."
>   },
>   "datomic": {
>     "host": "...",
>     "port": ...,
>     "alias": "...",
>     "database": "..."
>   }
> }'
```

## Development

```sh
$ rlwrap lein run -m "clojure.main" script/figwheel.clj
```

## Testing

```sh
$ lein doo node
```

Doo is provided to avoid including code to set the process exit code after a
test run.
