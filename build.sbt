name := "Scala Gear Simulation"

//Legacy sbt config for the last supported version of the Typesafe Console via the atmos sbt plugin
//https://stackoverflow.com/questions/24652401/what-happened-to-typesafe-console-for-akka-play-monitoring

scalaVersion := "2.10.3"

libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.2.3"

libraryDependencies += "com.typesafe.akka" %% "akka-testkit" % "2.2.3" % "test"

libraryDependencies += "org.scala-lang" % "scala-swing" % "2.10.3"

atmosSettings

atmosTestSettings