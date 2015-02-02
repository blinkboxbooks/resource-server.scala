
mergeStrategy in assembly <<= (mergeStrategy in assembly) { (old) =>
  {
    case PathList("asm-license.txt")     => MergeStrategy.discard
    case "application.conf" => MergeStrategy.discard
    case "mime.types" => MergeStrategy.concat
    case x => old(x)
  }
}

artifact in (Compile, assembly) ~= { art =>
  art.copy(`classifier` = Some("assembly"))
}

addArtifact(artifact in (Compile, assembly), assembly)
