sbtPlugin		:= true

name			:= "xsbt-zipper"

organization	:= "de.djini"

version			:= "0.5.0"

libraryDependencies	++= Seq(
	"org.apache.commons"	% "commons-compress"	% "1.4.1"	% "compile"
)

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
