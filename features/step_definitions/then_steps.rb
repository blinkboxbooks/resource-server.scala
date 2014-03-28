Then(/^the response is that ([^,]+?)$/) do |internal_object|
  expected_data = subject(make_subject_name(internal_object + " data")) rescue nil

  if expected_data.nil?
    local_path = File.join(__dir__,"../support/data", subject(make_subject_name(internal_object))['local_path'])
    expected_data = File.read(local_path)
  end
  
  # Not using #expect as the diff output produces a lot of output!
  if HttpCapture::RESPONSES.last.body.force_encoding('UTF-8') != expected_data.force_encoding('UTF-8')
    random = (0...8).map { (65 + rand(26)).chr }.join
    open("/tmp/#{random}-source.bin","w") { |f| f.write expected_data }
    open("/tmp/#{random}-received.bin", "w") { |f| f.write HttpCapture::RESPONSES.last.body }
    puts "Please check /tmp/#{random}-*.bin"
    raise "The body of the response was not the same as the source"
  end
end

Then(/^the response is that(?: (.+?))? image,(?: or)? visually similar( but more compressed)?$/) do |image_type, compression|
  subject(:image_data, subject(make_subject_name(image_type + " image data"))) if image_type
  compare_response_image(ensure_response_is_compressed: !compression.nil?)
end

Then(/^the response is that image, at the requested size$/) do
  compare_response_image(alter_source: subject(:resize_attributes))
end

Then(/^the response is that image, altered to match these parameters:$/) do |table|
  compare_response_image(alter_source: table.rows_hash)
end

Then(/^it has the correct content type$/) do
  expect(HttpCapture::RESPONSES.last['Content-Type']).to eql(subject(:content_type).content_type)
end

Then(/^the "(.+)" header is set to "(.+)"$/) do |header, content|
  expect(HttpCapture::RESPONSES.last[header]).to eq(content)
end

Then(/^the "(.+)" header is present$/) do |header|
  expect(HttpCapture::RESPONSES.last[header]).to_not be_nil
end

Then(/^the "Content-Location" header references this image with the following filters:$/) do |table|
  params = { 'v' => 0 }.merge(parse_format_details(table.rows_hash))
  expect(HttpCapture::RESPONSES.last['Content-location']).to start_with("/" + params_path(params) + "/")
end

Then(/^I receive the correct bytes of that ePub$/) do
  local_path = File.join(__dir__,"../support/data", subject(:epub)['local_path'])
  expected_data = File.read(local_path)[subject(:start_byte)..-1]

  if HttpCapture::RESPONSES.last.body.force_encoding('UTF-8') != expected_data.force_encoding('UTF-8')
    random = (0...8).map { (65 + rand(26)).chr }.join
    open("/tmp/#{random}-source.bin","w") { |f| f.write expected_data }
    open("/tmp/#{random}-received.bin", "w") { |f| f.write HttpCapture::RESPONSES.last.body }
    puts "Please check /tmp/#{random}-*.bin"
    raise "The body of the response was not the same as the required byte range of the source"
  end
end