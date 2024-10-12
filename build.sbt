name := "idus"

version := "0.1"

scalaVersion := "2.12.15"  // 안정적인 버전으로 설정

libraryDependencies ++= Seq(
  "org.apache.spark" %% "spark-core" % "3.5.3",
  "org.apache.spark" %% "spark-sql" % "3.5.3",
  "org.apache.spark" %% "spark-hive" % "3.5.3"
)

// sbt-assembly 플러그인 및 MergeStrategy 설정
import sbtassembly.AssemblyPlugin.autoImport._

assemblyMergeStrategy in assembly := {
  case PathList("META-INF", "io.netty.versions.properties") => MergeStrategy.first
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case _ => MergeStrategy.first
}
