# pixel

Datomic-based usage tracking of freshdiet-app.

## Development

### Install Datomic

* Download the version of [Datomic Pro](http://downloads.datomic.com/pro.html) specified in the `project.clj`.
* Run `bin/maven-install` to install the artifact locally.

## Running

Logging behavior, port numbers, hosts, and nREPL options are all Leiningen profile directed.  See the profiles listed in `project.clj`.  For each profile there is a directory in `envs/` that is added to the resource path per-profile.  This directory contains further runtime and logging configuration as informed by the `config.edn` and `log4j.properties` files.

### Development

    lein with-profile dev trampoline run

This runs the server locally on port 3000, and an nrepl server on port 4005.  Logs are to stdout.  Creates and uses an in-memory Datomic database.

### Production

    lein with-profile jenkins.thefreshdiet.trmk-3000 trampoline run

This runs the server (presumably on Jenkins) on port 3000, an nrepl server on 4005, and logs to `log/pixel-jenkins.thefreshdiet.trmk:3000.log`.

## License

Copyright (C) 2013 The Fresh Diet, Inc.
