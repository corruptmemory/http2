import com.typesafe.akka.http2.D


// Comment

lazy val compileOptions = Seq(
  "-unchecked",
  "-deprecation",
  "-language:_",
  "-target:jvm-1.8",
  "-encoding", "UTF-8"
)

lazy val commonDependencies = Seq(
  D.akka,
  D.akkaStream,
  D.akkaTestkit,
  D.akkaHttpCore,
  D.akkaHttp,
  D.akkaHttpTestkit,
  D.scalaTest
)

lazy val commonSettings = Seq(
  organization := "com.typesafe.training",
  version := "1.0-SNAPSHOT",
  scalaVersion := "2.11.7",
  scalacOptions ++= compileOptions,
  parallelExecution in Test := false,
  logBuffered in Test := false,
  parallelExecution in ThisBuild := false,
  libraryDependencies ++= commonDependencies
)

lazy val http2 = project
  .in(file("."))
  .aggregate(http2Core, http2Sample)

lazy val http2Core = project
  .in(file("http2-core"))
   .settings(name := "http-core")
   .settings(commonSettings: _*)
   .settings(libraryDependencies += D.alpnApi)

lazy val http2Sample = project
  .in(file("http2-sample"))
  .settings(name := "http2-sample")
  .settings(commonSettings: _*)
  .settings(libraryDependencies += D.alpnApi)
  .settings(libraryDependencies += D.alpnBoot)
  .settings(
    // Adds ALPN to the boot classpath for Http2 support
    javaOptions in run <++= (managedClasspath in Runtime) map { attList =>
      for {
        file <- attList.map(_.data)
        path = file.getAbsolutePath if path.contains("jetty.alpn")
      } yield { println(path); "-Xbootclasspath/p:" + path}
    }
  )

