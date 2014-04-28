<pre>

Preparing Baldrick performance test
-----------------------------------

Download Gatling 2 latest, these tests will not run with Gatling1

Gatling files changes:

bin/gatling.sh set the following if you have enough memory:
 -Xms10G -Xmx10G -Xmn1G 

conf/gatling.conf

 			percentile1 = 50						# in percents
 			percentile2 = 90						# in percents
 			allowPoolingConnection = false				# allow pooling HTTP connections (keep-alive header automatically added)
 			connectionTimeout = 1000					# timeout when establishing a connection



to test the MD5 response check:
set the resource server document root to point to the baldrick test-files directory

run the duplicate script that copies the big-0000.png and epub files 10000 times

Running Baldrick performance test
---------------------------------

stop the resource server
delete any cached files
start the resource server

edit the test properties for this test on this environment:
-> test properties are located in <baldrick home>/performance-tests/images/
-> rename from *.properties.example to *.properties
ImageScenarios.properties
ImageSimulations.properties

edit the ImageScenarios.scala to include or exclude the MD5 response size check

run test by executing:

<gatling2_home>/bin/gatling.sh -sf <baldrick home>/performance-tests/images/ -s com.blinkboxbooks.resourceserve.ResourceServerSimulation

you can also add an alias into your .profile if running on unix:
alias grun='<gatling2 home>/bin/gatling.sh'
or environment or PATH variable updates, whatever is easiest.

</pre>
