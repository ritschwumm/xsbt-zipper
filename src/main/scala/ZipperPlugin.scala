package xsbtZipper

import sbt._
import Keys.TaskStreams

import java.io.FileInputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry

import org.apache.commons.compress.archivers.zip._

import xsbtUtil.implicits._
import xsbtUtil.types._
import xsbtUtil.{ util => xu }

object Import {
	val zipper				= taskKey[File]("build the zip file and return it")
	val zipperZip			= settingKey[File]("bundle zip file to be created")

	val zipperSources		= taskKey[Traversable[PathMapping]]("files to be included in the bundle zip")

	val zipperPackageName	= settingKey[String]("default name for common directory prefix and bundle zip")
	val zipperPrefix		= settingKey[Option[String]]("common directory prefix for all files in the bundle zip")
	val zipperExtension		= settingKey[String]("file extension for the bundle zip")
	val zipperName			= settingKey[String]("file name of the bundle zip")

	val zipperBuildDir		= settingKey[File]("directory for the bundle zip file")

	// exported so they can be consumed like inTask(task)(zipperSettings ++ Seq(zipperFiles := selectSubPaths(...)))
	lazy val zipperSettings:Seq[Def.Setting[_]]	= ZipperPlugin.projectSettings
}

object ZipperPlugin extends AutoPlugin {
	//------------------------------------------------------------------------------
	//## exports

	lazy val autoImport	= Import
	import autoImport._

	override val requires:Plugins		= empty

	override val trigger:PluginTrigger	= noTrigger

	override lazy val projectSettings:Seq[Def.Setting[_]]	=
		Vector(
			zipper	:=
				ZipperPlugin zipperTask (
					streams	= Keys.streams.value,
					sources	= zipperSources.value,
					prefix	= zipperPrefix.value,
					zip		= zipperZip.value
				),
			zipperZip			:= zipperBuildDir.value / zipperName.value,

			// mandatory
			// zipperSources		:= Seq.empty,
			zipperPackageName	:= Keys.name.value + "-" + Keys.version.value,
			zipperPrefix		:= Some(zipperPackageName.value),
			zipperExtension		:= ".zip",
			zipperName			:= zipperPackageName.value + zipperExtension.value,

			zipperBuildDir		:= Keys.crossTarget.value / "zipper"
		)

	//------------------------------------------------------------------------------
	//## tasks

	def zipperTask(
		streams:TaskStreams,
		sources:Traversable[PathMapping],
		prefix:Option[String],
		zip:File
	):File	= {
		streams.log info s"creating bundle zip ${zip}"
		IO delete zip
		zip.mkParentDirs()

		val totalPrefix	= prefix.map(_ + "/").getOrElse("")

		val prefixed:Traversable[PathMapping]	=
			sources map { case (file, path) =>
				(file, totalPrefix + path)
			}

		xu.zip create (prefixed, zip)
		zip
	}
}
