module KnowsAboutMatrixParams
  def params_path(params = {'v' => 0})
    "params;" << params.sort_by { |key,value|
      key.to_s
    }.map { |k,v|
      "#{k}=#{v}"
    }.join(";")
  end
end
World(KnowsAboutMatrixParams)