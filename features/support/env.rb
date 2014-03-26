$: << __dir__

require "httpclient"
require "httpclient/capture"
require "cucumber/rest"
require "cucumber/helpers"
require "cucumber/helpers/table"
require "zip"
require "phashion"
require "tempfile"
require "mime-types"

TEST_CONFIG = {}
TEST_CONFIG["server"] = ENV["SERVER"] || "QA"
TEST_CONFIG["proxy"] = ENV["PROXY_SERVER"]
TEST_CONFIG["debug"] = !!(ENV["DEBUG"] =~ /^on|true$/i)
TEST_CONFIG["fail_fast"] = !!(TEST_CONFIG["FAIL_FAST"] =~ /^on|true$/i)
TEST_CONFIG["mount_dir"] = ENV["MOUNT_DIR"] || "/mnt/distribution"

puts "TEST_CONFIG: #{TEST_CONFIG}" if TEST_CONFIG["debug"]

require "cucumber/blinkbox/environment"
require "cucumber/blinkbox/data_dependencies"
require "cucumber/blinkbox/subjects"
require "cucumber/blinkbox/requests"
require "cucumber/blinkbox/responses"
require "cucumber/blinkbox/response_validation"

# Ensure we can push data onto the resource server for tests
raise RuntimeError, "I'm expecting #{TEST_CONFIG["mount_dir"]} to be a folder" unless File.directory?(TEST_CONFIG["mount_dir"])