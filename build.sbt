sbtPlugin		:= true

name			:= "xsbt-zipper"

organization	:= "de.djini"

version			:= "0.1.1"

scalacOptions	++= Seq("-deprecation", "-unchecked")

libraryDependencies	++= Seq(
	"org.apache.commons"	% "commons-compress"	% "1.4.1"	% "compile"
)
