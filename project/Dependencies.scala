import sbt._
import Keys._
object Dependencies {
  val shapeless = libraryDependencies += "com.chuusai" %% "shapeless" % "2.3.2"
  val compiler = libraryDependencies += "org.scala-lang" % "scala-compiler" % scalaVersion.value
  val test =  libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.1" % Test
  val macroParadise = libraryDependencies += compilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.patch)

}
