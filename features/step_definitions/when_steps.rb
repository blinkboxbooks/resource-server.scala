When(/^I request (?:the|an?) (.+) from within that sample ePub(?:, (.+))?$/) do |internal_object, additional_requirements|
  # We also need to set up the resulting
  internal_filename = subject(:sample_epub)['internal_paths'][internal_object.downcase] rescue nil
  raise RuntimeError, "Test error: The sample ePub given doesn't have a #{internal_object}" if internal_filename.nil?

  # Set the expected response so the then step can grab it
  content = Zip::File.open(subject(:sample_epub)[:local_path]) do |zip|
    zip.get_input_stream(internal_filename) do |io|
      io.read
    end
  end
  subject(make_subject_name(internal_object + " data"), content)

  # Prepare the request
  params = { 'v' => 0 }

  case additional_requirements
  when nil
    # No additional requirements
  when "scaled down"
    params['img:w'] = 10
    subject(:resize_attributes, {'Width' => params['img:w']})
  else
    raise RuntimeError, "I don't know how to deal with the additional requirement: #{additional_requirements}"
  end

  request_path = File.join(params_path(params), subject(:sample_epub)[:uploaded_path], internal_filename)
  http_get :resource_server, request_path
  subject(:content_type, MIME::Types.type_for(request_path).first)
end

When(/^I request a file from within that sample ePub which does not exist$/) do
  request_path = File.join(params_path, subject(:sample_epub)[:uploaded_path], "definitely_shouldnt_exist.not_an_ext")
  http_get :resource_server, request_path
end

When(/^I request a (direct|processed) download of that (.+?)( from a specific byte location onwards)?$/) do |download_type, object_type, byte_range|
  subject_object = subject(make_subject_name(object_type))
  request_path = subject_object[:uploaded_path]
  request_path = File.join(params_path, request_path) if download_type == "processed"
  headers = {}
  if byte_range
    subject_object_size = File.size(File.join(__dir__, "../support/data", subject_object['local_path']))
    # Pick a random byte index which isn't the start and isn't the end
    byte_index = 1 + Random.rand(subject_object_size - 2)
    subject(:start_byte, byte_index)
    headers['Range'] = "bytes=#{subject(:start_byte)}-"
  end
  http_get :resource_server, request_path, headers
end

When(/^I request that image with the following filters:$/) do |table|
  params = { 'v' => 0 }.merge(parse_format_details(table.rows_hash))
  request_path = File.join(params_path(params), subject(:image)[:uploaded_path])
  http_get :resource_server, request_path
  subject(:content_type, MIME::Types.type_for(request_path).first)
end

When(/^I request that image in (jpg|png) format$/) do |format|
  request_path = File.join(params_path, subject(:image)[:uploaded_path] + ".#{format}")
  http_get :resource_server, request_path
  subject(:content_type, MIME::Types.type_for(request_path).first)
end
