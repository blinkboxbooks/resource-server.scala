# Change log

## 0.1.11 ([#19](https://git.mobcastdev.com/Platform/baldrick/pull/19) 2014-05-01 15:35:00)

Added response header

This patch adds a response header "X-bbb-from-intermediate-resource", as requested by @alexb. This will be set to "true" on image files, when these are produced from an intermediate (cached) image file as opposed to the full resolution file.

The intention is to make stress tests easier to implement, as the returned images are subtly different depending on whether it came straight from the full-size original vs. the precomputed intermediate size files, hence MD5 hashes of the files are different.

## 0.1.10 ([#18](https://git.mobcastdev.com/Platform/baldrick/pull/18) 2014-05-01 13:48:52)

patch change for copy and paste typo in load profile

patch change to fix copy and paste typo in load profile

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

