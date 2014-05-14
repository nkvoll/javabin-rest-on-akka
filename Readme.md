# Javabin: REST on Akka using Spray

This is an example service that shows how Spray and Akka can be used in a larger example.

It provides a relatively simple way of registered users to send persistent messages to other registered users.

Before being allowed to send messages, each user needs the ``messages`` permission, which by default can only be granted
by users having the ``users.user.attributes.edit`` permission, which the ``root`` user has by default.

Note: this service is intended as an example and is in no way feature complete.

## Backend

The backend is written in Scala using Akka and Spray. It uses spray-json to read and write JSON.

### Running locally

It can either be run using ``sbt run`` or by using the sbt-revolver plugin, which takes care of re-starting the service
after every code change:

    $ sbt '~re-start'

The REST API is available at http://localhost:8080/api/v0 by default.

### Running clustered

Example that uses ``config/clusted.conf``:

 - Start node 1:

    $ sbt '~re-start --- -Dconfig.file=config/clustered.conf'

 - Start node 2:

    $ sbt '~re-start --- -Djavabin-rest-on-akka.http.port=8081 -Dakka.remote.netty.tcp.port=2552 -Dconfig.file=config/clustered.conf'

After a short while, the services should be available on port 8080 and 8081, and clients connected to either service can
send messages to each other.

### Running the tests

Run the tests using:

    $ sbt test

### Generating an IDEA project:

    $ sbt gen-idea

## Frontend

The frontend is written in Javascript using RequireJS, Backbone, Underscore, MarionetteJS, Underscore.string and Bootstrap.

### Building:

cd app
./build.sh