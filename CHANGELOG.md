# Change log

## 1.2.1 ([#48](https://git.mobcastdev.com/Platform/baldrick/pull/48) 2015-01-07 11:04:13)

PT-23 Status code for partial responses

### Bugfixes:

- Return 206 instead of 200 for partial responses triggered by Range header in request.
- Remove broken support for suffix ranges.

### Improvements:

- Refactored Range class and its usage.


## 1.2.0 ([#47](https://git.mobcastdev.com/Platform/baldrick/pull/47) 2014-11-25 10:34:50)

Now logs messages to Graylog

### New features

- Logs details of requests as well as debug info (when enabled) to Graylog

### Improvements

- Now uses Scala 2.11 and the latest versions of common libraries.

## 1.1.18 ([#46](https://git.mobcastdev.com/Platform/baldrick/pull/46) 2014-11-18 15:35:35)

Add tests for If-Range header

### Improvements

- Added tests for the (correctly unsupported) `If-Range` header.
- Removed `vendor` from git accepted files

## 1.1.17 ([#44](https://git.mobcastdev.com/Platform/baldrick/pull/44) 2014-11-13 16:19:25)

Handles URL-encoded resource names

### Bug Fixes

- URL-decodes the file name portion of the path as well as the params,
e.g. to handle items with spaces in the path.

## 1.1.16 ([#43](https://git.mobcastdev.com/Platform/baldrick/pull/43) 2014-11-12 12:16:57)

Now supports floating point pixels (!)

### Bug Fixes

* The old Ruby resource server allowed floating point values for pixels
and quality, and Tesco have taken a dependency on this for their Hudl2
promotion cards. As such, we have no choice but to support floating
point pixels in the new resource server. Sigh.

## 1.1.15 ([#42](https://git.mobcastdev.com/Platform/baldrick/pull/42) 2014-11-12 10:18:40)

Now supports lowercase url-encoding of path

### Bug Fixes

- When paths are received URL-encoded in lowercase these are also
supported. This shouldn’t happen but it appears that Level 3 mangle the
already mangled URLs sent by the Android app to make them even more
broken than they were…

## 1.1.14 ([#41](https://git.mobcastdev.com/Platform/baldrick/pull/41) 2014-10-31 17:52:15)

CP-2029 Cope with double slashes in paths inside epubs

### Improvements:

- Be even more lenient with double slashes in paths.


## 1.1.13 ([#40](https://git.mobcastdev.com/Platform/baldrick/pull/40) 2014-10-28 15:47:50)

CP-2012: Deal with 'difficult' images.

### Improvement:

- Added Twelvemonkeys ImageIO plugin to deal with 'difficult' images.


## 1.1.12 ([#39](https://git.mobcastdev.com/Platform/baldrick/pull/39) 2014-10-16 16:59:15)

CP-1865: Allow double slash at start of path

### Bugfix:

- Allow multiple slashes at start of path, while still treating these as relative paths, for requests with parameters, to be backwards compatible with previous resource server, and cope with client that does it that way.


## 1.1.11 ([#38](https://git.mobcastdev.com/Platform/baldrick/pull/38) 2014-10-16 15:11:57)

CP-1782: Fix miscoloured output for RGBA PNG files

### Bugfix:

- Fix strange pink/purple hue in JPEG files that are generated from input files that are PNG files with an RGBA channel (i.e. has a transparency layer).


## 1.1.10 ([#37](https://git.mobcastdev.com/Platform/baldrick/pull/37) 2014-10-15 17:02:27)

CP-1951: URL-decode parameters in request

### Improvement

- CP-1951: URL decode parameters in request, to cope with client double-encoding them.


## 1.1.9 ([#36](https://git.mobcastdev.com/Platform/baldrick/pull/36) 2014-09-04 11:25:34)

Remove unneeded http headers

Patch to remove unneeded(?) HTTP headers

## 1.1.8 ([#35](https://git.mobcastdev.com/Platform/baldrick/pull/35) 2014-09-02 11:32:05)

CP-1789: scale! vs scale

PATCH: 

- CP-1789: do not upscale with "scale" mode, only with "scale!" mode. 

## 1.1.7 ([#34](https://git.mobcastdev.com/Platform/baldrick/pull/34) 2014-09-01 15:13:54)

CP-1701: Etag header value should have quote marks

PATCH 

Bug Fixed: 
- CP-1701 ETag header should be surrounded by quotes

## 1.1.6 ([#33](https://git.mobcastdev.com/Platform/baldrick/pull/33) 2014-08-29 11:04:26)

Serve non-images requested with image params

### Bug fix

- [CP-1784](http://jira.blinkbox.local/jira/browse/CP-1784): Allow non-images to be requested with image parameters.

## 1.1.5 ([#31](https://git.mobcastdev.com/Platform/baldrick/pull/31) 2014-08-01 10:52:12)

CP-1604: I introduced a couple of regressions, this fixes

patch

I introduced two regressions, that were caught by the cucumber tests, which I neglected to run... they all pass now!

## 1.1.4 ([#30](https://git.mobcastdev.com/Platform/baldrick/pull/30) 2014-07-31 09:34:36)

CP-1604: Poor Quality Images

patch
- Use Lanczos resize from https://code.google.com/p/java-image-scaling/
- Takes between 150-200ms on local mbpro after a brief warmup. 

## 1.1.3 ([#28](https://git.mobcastdev.com/Platform/baldrick/pull/28) 2014-07-14 17:37:23)

Fix date-time format

Bug fix to use correct date-time format in headers, see CP-1597.


## 1.1.2 ([#29](https://git.mobcastdev.com/Platform/baldrick/pull/29) 2014-07-15 11:04:45)

Added GPG key for signed RPMs

### Improvement

- Added the GPG folder to the repo so the RPM generated will be signed.

## 1.1.1 ([#26](https://git.mobcastdev.com/Platform/baldrick/pull/26) 2014-07-10 10:28:47)

Updating gemfile to reflect changes in rubygem servers

A patch to include the sources for the new rubygems server and an update to cucumber-blinkbox

## 1.1.0 ([#25](https://git.mobcastdev.com/Platform/baldrick/pull/25) 2014-07-04 18:12:20)

Change quality setting and build as RPM

### New features

- Can now build as an RPM.
- Uses the standard configuration library to load configuration (using `application.conf` as the example properties file is required for the RPM build).

### Improvements

- Uses the `AUTOMATIC` quality level rather than `BALANCED` to produce higher quality images at smaller sizes.

## 1.0.1 ([#24](https://git.mobcastdev.com/Platform/baldrick/pull/24) 2014-06-05 11:55:38)

Tiny tweak to force rebuild

Patch: Tiny change to bump version number and force rebuild, should be the first build to go to Artifactory.

## 1.0.0 ([#22](https://git.mobcastdev.com/Platform/baldrick/pull/22) 2014-05-12 16:07:57)

Update major version to 1.0.0

Major release/breaking change: bump version to 1.0.0 to avoid clashes with version in the Ruby resource server, which used version 0.n.n.


## 0.1.13 ([#21](https://git.mobcastdev.com/Platform/baldrick/pull/21) 2014-05-09 11:17:14)

Added ability to disable pre-computing of smaller image sizes

A patch for disabling caching:

As discussed here: https://tools.mobcastdev.com/confluence/display/REL/Deployment+Concerns, we want to be able to roll out this version of the resource server, **without** the production of cached intermediate image sizes.

This feature is enabled by setting the configuration property:

cache.threads.count=0

or by not setting it at all, i.e. this is the default value for it.

Generation of smaller image sizes for faster serving of smaller files can still be enabled by setting this configuration property to greater than 0.

## 0.1.12 ([#20](https://git.mobcastdev.com/Platform/baldrick/pull/20) 2014-05-06 19:43:55)

patch change to gatling script

patch change to gatling script to allow for difference with live version message

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

