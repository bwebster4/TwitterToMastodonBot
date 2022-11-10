lazy val root = (project in file("."))
  .settings(
    scalaVersion := "2.13.10",
    name := "TwitterToMastodon",
    libraryDependencies ++= Seq(
      "com.lihaoyi" %% "cask" % "0.8.3",
      "com.twitter" % "twitter-api-java-sdk" % "2.0.3",
      "de.sciss" %% "scaladon" % "0.5.1",
      "ch.qos.logback" % "logback-classic" % "1.4.4"
    ),
    mainClass := Some("Main"),
    assemblyMergeStrategy := {
      case PathList("javax", xs @ _*) => MergeStrategy.first
      case "application.conf" => MergeStrategy.concat
      case "module-info.class" => MergeStrategy.discard
      case x if x.endsWith("/module-info.class") => MergeStrategy.discard
      case x =>
        val oldStrategy = (ThisBuild / assemblyMergeStrategy).value
        oldStrategy(x)
    }
  )