# pixel

Datomic-based usage tracking of freshdiet-app.

## Development

### Install Datomic

* Download the version of [Datomic Pro](http://downloads.datomic.com/pro.html) specified in the `project.clj`.
* Run `bin/maven-install` to install the artifact locally.

## Running

    ENV=(dev|prod) lein ring server-headless <port>

## License

Copyright (C) 2013 The Fresh Diet, Inc.