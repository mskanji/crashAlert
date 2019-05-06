name := "AnomaliesDetection"

version := "0.1"

scalaVersion := "2.12.1"

libraryDependencies += "org.apache.kafka" % "kafka-clients" % "0.11.0.0"
libraryDependencies += "org.apache.spark" %% "spark-core" % "2.4.1"
libraryDependencies += "org.apache.spark" %% "spark-sql" % "2.4.2"
libraryDependencies += "org.apache.spark" % "spark-streaming_2.12" % "2.4.2"
libraryDependencies += "org.apache.spark" %% "spark-streaming-kafka-0-10" % "2.4.1"