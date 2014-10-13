sbtPlugin		:= true

name			:= "xsbt-zipper"

organization	:= "de.djini"

version			:= "1.1.0"

scalacOptions	++= Seq(
	"-deprecation",
	"-unchecked",
	// "-language:implicitConversions",
	// "-language:existentials",
	// "-language:higherKinds",
	// "-language:reflectiveCalls",
	// "-language:dynamics",
	// "-language:postfixOps",
	// "-language:experimental.macros"
	"-feature"
)

libraryDependencies	++= Seq(
	"org.apache.commons"	% "commons-compress"	% "1.8.1"	% "compile"
)

addSbtPlugin("de.djini" % "xsbt-util"	% "0.1.0")
