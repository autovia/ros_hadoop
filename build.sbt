import Dependencies._

lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      organization := "org.apache.spark.input",
      scalaVersion := "2.12.10",
      version      := "0.9.8"
    )),
    name := "RosbagInputFormat",
    libraryDependencies ++= Seq( 
	scalaTest % Test,
	"com.google.code.gson" % "gson" % "2.8.0",
        "org.apache.spark" %% "spark-core" % "3.0.1",
	"com.google.protobuf" % "protobuf-java" % "3.3.0"
    ),
    packageOptions in (Compile, packageBin) += 
      Package.ManifestAttributes(
        "Class-Path" -> Seq(".","scala-library-2.12.10.jar","protobuf-java-3.3.0.jar").mkString(" "))
  )
