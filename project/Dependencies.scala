package com.typesafe.akka.http2

import sbt._


object D {
  val akkaVersion = "2.4.0"
  val akkaHttpVersion = "1.0"
  val akkaStreamsVersion = "1.0"
  val scalaTestVersion = "2.2.4"

  val akka = "com.typesafe.akka" %% "akka-actor" % akkaVersion
  val akkaTestkit = "com.typesafe.akka" %% "akka-testkit" % akkaVersion % "test"

  val akkaStream = "com.typesafe.akka" %% "akka-stream-experimental" % akkaStreamsVersion
  val akkaHttpCore = "com.typesafe.akka" %% "akka-http-core-experimental" % akkaHttpVersion
  val akkaHttp = "com.typesafe.akka" %% "akka-http-experimental" % akkaHttpVersion
  val akkaHttpTestkit = "com.typesafe.akka" %% "akka-http-testkit-experimental" % akkaHttpVersion % "test"

  val scalaTest = "org.scalatest" %% "scalatest" % scalaTestVersion % "test"
}