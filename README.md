Maven Indexer Examples
======

This example covers simple use cases and is runnable as Java App or just using Maven "test" goal (as there is a Junit test simply executing the main() method, to not have to fuss with classpath etc.)
It was originally obtained from https://github.com/apache/maven-indexer. The original example comes with the indexer core itself in a development state. The one is this repository uses the release version 5.1.1 instead.

To run the examples, set the constants in the Main file (default values are pointing to the Jenkins public repository).

```
$ cd indexer-examples
$ mvn clean test -Pexamples
  ... first run will take few minutes to download the index, and then will run showing some output
$ mvn test -Pexamples
  ... (no clean goal!) second run will finish quickly, as target folder will already contain an up-to-date index
```

TODO: The second run seems to be unable to read the up-to-date index. If that happens, a NullPointerException is raised. The next execution will download the index again.
