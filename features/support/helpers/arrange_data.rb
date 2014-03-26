require "fileutils"

module KnowsHowToPutFilesOnTheResourceServer
  TEST_FOLDER = "rs-test-#{(0...8).map { (65 + rand(26)).chr }.join}"

  def upload_and_return_info_for(object_type, which: nil)
    info = data_for_a(object_type, which: which)

    local_path = File.join(__dir__, "../data", info['local_path'])
    url_path = File.join(TEST_FOLDER, info['isbn'][0..3], *info['isbn'][4..-1].scan(/.{3}/), info['local_path'])
    remote_path = File.join(TEST_CONFIG["mount_dir"],url_path)

    FileUtils.mkdir_p(File.dirname(remote_path))
    FileUtils.cp(local_path, remote_path)

    info[:uploaded_path] = "/" << url_path
    info[:local_path] = local_path
    
    info
  end
  
  def make_subject_name(string)
    string.downcase.tr(" ","_").to_sym
  end
end
World(KnowsHowToPutFilesOnTheResourceServer)

After do
  FileUtils.rm_rf(File.join(TEST_CONFIG["mount_dir"],KnowsHowToPutFilesOnTheResourceServer::TEST_FOLDER))
end