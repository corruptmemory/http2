import com.typesafe.akka.http2.D

name := "akka-http2"
version := "1.0-SNAPSHOT"
scalaVersion := "2.11.7"

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")

libraryDependencies ++= Seq(
  D.akka,
  D.akkaStream,
  D.akkaTestkit,
  D.akkaHttpCore,
  D.akkaHttp,
  D.akkaHttpTestkit,
  D.scalaTest
)

