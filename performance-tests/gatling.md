## Running performance tests

**NOTE: ** These tests and docs are not 100% up to date and may need fixes to run.

The performance tests use Gatling 2, which at the time of writing is not released yet. The latest snapshot can be downloaded
from [the Sonatype repository](https://oss.sonatype.org/content/repositories/snapshots/io/gatling/highcharts/gatling-charts-highcharts/2.0.0-SNAPSHOT/). (These tests will not run with Gatling 1)

### Test setup ###

In `bin/gatling.sh` (in your Gatling installation), set the following if you have enough memory:

```
-Xms10G -Xmx10G -Xmn1G
```

Also, in `conf/gatling.conf`, set the following:

```
percentile1 = 50				# in percent
percentile2 = 90				# in percent
allowPoolingConnection = false	# allow pooling HTTP connections (keep-alive header automatically added)
connectionTimeout = 1000		# timeout when establishing a connection
```

Configure the resource server to point at the test images located in `<baldrick home>/performance-tests/images/`.

* Rename the files in `performance-tests/images` from `*.properties.example` to `*.properties`, to create the following files:
  * ResourceServerScenarios.properties
  * ResourceServerSimulations.properties
* Set the resource server document root to point to the `<baldrick root>/performance-test/images/test-files` directory.
* Run the script `test-files/duplicate.sh' that copies the `big-0000.png` and epub files 10,000 times.

To include checking of the content of returned images using MD5 hashes:

* Edit `ImageScenarios.scala` to include or exclude the MD5 response size check (this is commented out by default).

The setup above describes how to run the performance tests on a set on well known input files. You can also run some of the tests against an arbitrary set of test files, such as a selection of images and ePub files from the real system. In this case, you have to disable the MD5 checks, and the ePub file tests, as these currently only work with well-known input data.


### Running Gatling tests ###

To run tests from a 'clean' state, that takes into account work done on image background processing:

* Stop the resource server.
* Delete any cached files (in the directory set in the `cache.directory` property in the resource server config.
* Start the resource server.

If testing performance of the resource server given an already populated cache of resized images, run the tests to completion first, then restart the test *without* clearing the cache directory.

Run tests by executing:

```
<gatling2_home>/bin/gatling.sh -sf <baldrick home>/performance-tests/images/ -s com.blinkboxbooks.resourceserver.ResourceServerSimulation
```

You can also add an alias into your `.profile/.bash_profile` if running on Linux or OS/X:

```
alias grun='<gatling2 home>/bin/gatling.sh'
```

Or,  do environment or PATH variable updates, whatever is easiest.

When the tests have completed, Gatling will generate a report containing the results.
