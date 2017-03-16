lazy val commonSettings = Seq(
  organization := "io.github.steefh",
  scalaVersion := "2.12.1",
  crossScalaVersions := Seq(
    //  "2.10.6",
    "2.11.8",
    "2.12.1"
  ),
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
    "-Xfatal-warnings",
    "-language:experimental.macros"
    //  , "-Xlog-implicits"
  )
)



val amorphous = (project in file("."))
  .settings(
    commonSettings,
    Dependencies.shapeless,
    Dependencies.test,
    Dependencies.compiler,
    Dependencies.macroParadise,
    licenses += ("MIT", url("http://opensource.org/licenses/MIT")),
    releaseCrossBuild := true
  )
//  .dependsOn(`amorphous-macros`)


//lazy val `amorphous-macros` = (project in file("macros"))
//  .settings(
//    commonSettings,
//    Dependencies.shapeless,
//    Dependencies.compiler,
//    Dependencies.test
//  )
