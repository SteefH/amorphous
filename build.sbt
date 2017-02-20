organization := "io.github.steefh"
name := "amorphous"
scalaVersion := "2.12.1"
crossScalaVersions := Seq(
//  "2.10.6",
  "2.11.8",
  "2.12.1"
)

libraryDependencies ++= Seq(
  "com.chuusai" %% "shapeless" % "2.3.2",
  "org.scalatest" %% "scalatest" % "3.0.1" % Test
)

scalacOptions ++= Seq(
  "-unchecked",
  "-feature",
  //      "-explaintypes",
  "-deprecation",
  "-Xfuture",
  "-Yno-adapted-args",
  "-Ywarn-dead-code",
  "-Ywarn-numeric-widen",
  "-Ywarn-value-discard",
  "-Xfatal-warnings"
)

licenses += ("MIT", url("http://opensource.org/licenses/MIT"))
releaseCrossBuild := true
