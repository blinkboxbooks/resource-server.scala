package com.blinkboxbooks.resourceserver

import scala.util.Try

object MatrixParameters {

  /**
   * Try to parse the given string as Matrix parameters, see http://www.w3.org/DesignIssues/MatrixURIs.html
   *
   * @param params The string parameter to parse. Must not be null, but may be empty.
   * @returns Failure if the string can't be parsed as a valid set of parameters,
   *  or a map of (parameter name -> parameter value) for other parameters.
   *  Multi-valued parameters are not supported.
   */
  def getMatrixParams(params: String): Try[Map[String, String]] = Try {
    val declarations = params.trim.split(";").filter(str => !str.isEmpty)
    val ps = declarations.map(d => d.split("="))
    ps.foldLeft(Map[String, String]())((m, vals) => m + (parameterName(vals) -> parameterValue(vals)))
  }

  private val parameterNamePattern = """[\w:.]+""".r.pattern

  private def parameterName(values: Array[String]): String =
    if (!parameterNamePattern.matcher(values(0).trim).matches)
      throw new Exception("Invalid parameter name")
    else values(0).trim

  private def parameterValue(values: Array[String]): String =
    if (values.length < 2)
      throw new Exception("Invalid parameter syntax")
    else values(1).trim

}