name := "util"

libraryDependencies  ++= Seq(
    "org.specs2" %% "specs2" % "2.2.2" % "test",
    "org.scalacheck" %% "scalacheck" % "1.10.1" % "test",
    "org.scalaz" %% "scalaz-core" % "7.0.6"//,
    //"org.scalatest" % "scalatest_2.10" % "2.1.0" % "test"
)

resolvers ++= Seq(
  "sonatype snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"  )
