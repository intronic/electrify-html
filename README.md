# com.github.intronic/electrify-html

Convert HTML Fragments, eg from Tailwind CSS snippets, to electric dom code.

## Installation

Download from https://github.com/intronic/electrify-html

## Usage

Run the project directly, via `:exec-fn`:

    $ clojure -X:run-x :file _file_name_ [:keep-comments true]
  eg:
    $ clojure -X:run-x :file ./resources/test/badge.html

Run the project:

    $ clojure -X intronic.electrify-html/-main :file ./resources/test/badge.html

Run the project's tests:

    $ clojure -T:build test

Run the project's CI pipeline and build an uberjar (this will fail until you edit the tests to pass):

    $ clojure -T:build ci

This will produce an updated `pom.xml` file with synchronized dependencies inside the `META-INF`
directory inside `target/classes` and the uberjar in `target`. You can update the version (and SCM tag)
information in generated `pom.xml` by updating `build.clj`.

If you don't want the `pom.xml` file in your project, you can remove it. The `ci` task will
still generate a minimal `pom.xml` as part of the `uber` task, unless you remove `version`
from `build.clj`.

Run that uberjar:

    $ java -jar target/com.github.intronic/electrify-html-0.1.0-SNAPSHOT.jar

## Options

FIXME: listing of options this app accepts.

## License

Copyright © 2024 Mike Pheasant

Distributed under the Eclipse Public License version 1.0.