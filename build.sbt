ThisBuild / scalaVersion     := "2.13.14"
ThisBuild / version          := "0.1.0"
ThisBuild / organization     := "UCSC-AHD"


val chiselVersion = "3.6.1"


// The following sets it up such that when you run sbt ...
//   Grader / test you run all tests that end in "Grader"
//   test you run all tests that don't end in "Grader"
lazy val Grader = config("grader") extend(Test)
def endsWithGrader(name: String): Boolean = name endsWith "Grader"
lazy val scalatest = "org.scalatest" %% "scalatest" % "3.2.15"

lazy val root = (project in file("."))
.configs(Grader)
	.settings(
		name := "hw4",
    libraryDependencies ++= Seq(
      "edu.berkeley.cs" %% "chisel3" % chiselVersion,
      "edu.berkeley.cs" %% "chiseltest" % "0.6.2" % "test"
    ),
    scalacOptions ++= Seq(
      "-language:reflectiveCalls",
      "-deprecation",
      "-feature",
      "-Xcheckinit",
    ),
    addCompilerPlugin("edu.berkeley.cs" % "chisel3-plugin" % chiselVersion cross CrossVersion.full),

		// For Gradescope tests
		libraryDependencies += "org.scalatestplus" %% "junit-4-13" % "3.2.15.0" % "test",

    inConfig(Grader)(Defaults.testTasks),
		libraryDependencies += scalatest % Grader,
    Grader / testOptions := Seq(Tests.Filter(endsWithGrader)),
		Test / testOptions := Seq(Tests.Filter(!endsWithGrader(_))),
 )
