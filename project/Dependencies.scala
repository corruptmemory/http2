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

  // Needed for Http2 support until implemented in the JDK
  lazy val alpnApi = "org.eclipse.jetty.alpn" % "alpn-api" % "1.1.2.v20150522"

  // Note that the alpn_boot version is JVM version specific. Check the docs if getting weird errors.
  // Also note that only java8 and above has the require cipher suite for http2.
  lazy val alpnBoot = "org.mortbay.jetty.alpn" % "alpn-boot" % "8.1.5.v20150921"

  val scalaTest = "org.scalatest" %% "scalatest" % scalaTestVersion % "test"
}
