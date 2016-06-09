Maven Indexer Examples
======

This example covers simple use cases and is runnable as Java App or just using Maven "test" goal (as there is a Junit test simply executing the main() method, to not have to fuss with classpath etc.)
It was originally obtained from https://github.com/apache/maven-indexer. However, the original example comes with the indexer core itself in a development state. Making the example compatible with a release version (5.1.1) of maven-indexer took a bit of extra work.
Since this is repository provides a standalone example, and for an older version that the one proposed in that repository, it was decided to create a new repository instead of a fork.

Try following steps:

```
$ cd indexer-examples
$ mvn clean test -Pexamples
  ... first run will take few minutes to download the index, and then will run showing some output
$ mvn test -Pexamples
  ... (no clean goal!) second run will finish quickly, as target folder will already contain an up-to-date index
```

Please, note that the tests in this module will only compile by default; the will not be executed, unless you activate the profile (-Ptests).

Have fun,  
~t~