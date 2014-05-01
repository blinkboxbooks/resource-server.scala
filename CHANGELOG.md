# Change log

## 0.1.9 ([#17](https://git.mobcastdev.com/Platform/baldrick/pull/17) 2014-04-30 16:39:02)

Fixes to performance tests

This is a patch with some minor fixes for the Gatling performance tests.

I've updated @alexb's docs, moving them from a separate README file into the main one, for easy access.

Also fixed an issue that caused URLs with double slashes (//) to be generated in some tests.

And, moved the setting of expected MD5 values into its own exec() step as it wasn't working for me as previously checked in.

## 0.1.8 ([#16](https://git.mobcastdev.com/Platform/baldrick/pull/16) 2014-04-28 13:46:05)

Use number of CPUs - 1 for background processing of images

This is a patch requested by @alexb to use one less than the number of CPUs to do background processing of images.

## 0.1.7 ([#13](https://git.mobcastdev.com/Platform/baldrick/pull/13) 2014-04-28 10:28:32)

Gatling tests

patch to add missing props files and add comments in read me

## 0.1.6 ([#12](https://git.mobcastdev.com/Platform/baldrick/pull/12) 2014-04-24 16:39:46)

initial update of baldrick gatling perf tests

### Small patch to improve performance tests

* updated scripts to gatling 2
* added script structure that should scale across projects
* added script properties to externalise environment/test settings
* added checks and assertions to determine whether the test has passed or
* failed - WIP
* will need further documentation on confluence

