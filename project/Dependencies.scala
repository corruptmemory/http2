package com.typesafe.akka.http2

import sbt._


object D {
  val akkaVersion = "2.4.0"
  val akkaHttpVersion = "1.0"
  val scalaTestVersion = "2.2.4"

  val akka = "com.typesafe.akka" %% "akka-actor" % akkaVersion
  val akkaTestkit = "com.typesafe.akka" %% "akka-testkit" % akkaVersion % "test"

  val akkaHttpCore = "com.typesafe.akka" %% "akka-http-core" % akkaHttpVersion
  val akkaHttp = "com.typesafe.akka" %% "akka-http" % akkaHttpVersion
  val akkaHttpTestkit = "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % "test"

  val scalaTest = "org.scalatest" %% "scalatest" % scalaTestVersion % "test"
}