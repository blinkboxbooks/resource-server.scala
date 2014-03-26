Given(/^an?(?: valid)? (.+?)(?: with (.+))? exists on the resource server$/) do |object_type, with|
  subject = make_subject_name(object_type)
  subject(subject, upload_and_return_info_for(subject, which: with.nil? ? "is standard" : "has #{with}"))
end

Given(/^an image with the following attributes exists on the resource server:$/) do |table|
  has_these_attributes = "has a " << table.rows_hash.sort_by {|k,v| k}.map {|k,v| "#{k.downcase} of #{v}" }.join(", ")
  subject(:image, upload_and_return_info_for(:image, which: has_these_attributes))
end