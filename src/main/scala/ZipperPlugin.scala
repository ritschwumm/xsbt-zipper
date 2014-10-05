import sbt._
import Keys.TaskStreams
import Project.Initialize

import java.io.FileInputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry

import org.apache.commons.compress.archivers.zip._

import xsbtUtil._

object ZipperPlugin extends Plugin {
	val zipper			= taskKey[File]("build the zip file and return it")
	val zipperFiles		= taskKey[Traversable[PathMapping]]("files to be included in the bundle zip")
	val zipperBundle	= settingKey[String]("default name for common directory prefix and bundle zip")
	val zipperPrefix	= settingKey[Option[String]]("common directory prefix for all files in the bundle zip")
	val zipperExtension	= settingKey[String]("file extension for the bundle zip")
	val zipperName		= settingKey[String]("file name of the bundle zip")
	val zipperTargetDir	= settingKey[File]("directory for the bundle zip file")
	val zipperZip		= settingKey[File]("bundle zip file to be created")
	
	lazy val zipperSettings:Seq[Def.Setting[_]]	=
			Seq(
				zipper	:= 
						zipperTask(
							streams	= Keys.streams.value,
							files	= zipperFiles.value,
							prefix	= zipperPrefix.value,
							zip		= zipperZip.value
						),
				zipperFiles		:= Seq.empty,
				zipperBundle	:= Keys.name.value + "-" + Keys.version.value,
				zipperPrefix	:= Some(zipperBundle.value),
				zipperExtension	:= ".zip",
				zipperName		:= zipperBundle.value + zipperExtension.value,
				zipperTargetDir	:= Keys.crossTarget.value / "zipper",
				zipperZip		:= zipperTargetDir.value / zipperName.value
			)
	
	private def zipperTask(
		streams:TaskStreams,
		files:Traversable[PathMapping], 
		prefix:Option[String],
		zip:File
	):File	= {
		streams.log info s"creating bundle zip ${zip}"
		IO delete zip
		zip.mkParentDirs()
		
		val addPrefix:PathMapping=>PathMapping	=
				prefix match {
					case Some(s)	=> modifySecond(s + "/" + (_:String))
					case None		=> identity
				}
		val prefixed	= files map addPrefix
		bundle(streams, prefixed, zip)
		
		zip
	}
	
	private val fileMode	= parseOctal("100000")
	private val dirMode		= parseOctal("040000")
	private val linkMode	= parseOctal("120000")
		
	private val rwxr_xr_x	= parseOctal("755")
	private val rw_r__r__	= parseOctal("644")
	
	/** paths must use a forward slash, unix mode is optional, symlinks are ignored */
	private def bundle(streams:TaskStreams, sources:Traversable[PathMapping], outputZip:File) {
		val outputStream	= new ZipArchiveOutputStream(outputZip)
		// outputStream	setMethod	ZipOutputStream.DEFLATED
		// outputStream	setLevel	0
		try {
			val sourceDirectories		= sources filter { _._1.isDirectory	}
			val sourceFiles				= sources filter { _._1.isFile		}
			
			// ensure every file has a parent directory
			val sourceDirectoryPaths	= sourceDirectories map { _._2 + "/" }
			val sourceParentPaths		= sourceFiles		map { _._2 } flatMap pathDirs
			val zipDirectories			= (sourceDirectoryPaths ++ sourceParentPaths).toVector.distinct
			
			val now			= System.currentTimeMillis
			val emptyCRC	= (new CRC32).getValue
			
			zipDirectories foreach { path =>
				val entry	= new ZipArchiveEntry(path)
				entry	setMethod	ZipEntry.STORED
				entry	setSize		0
				entry	setTime		now
				entry	setCrc		emptyCRC
				entry	setUnixMode	(dirMode | rwxr_xr_x)
				outputStream  	putArchiveEntry entry
				outputStream.closeArchiveEntry()
			}
			
			sourceFiles foreach { case (file, path) => 
				val entry	= new ZipArchiveEntry(path)
				entry	setMethod	ZipEntry.STORED
				entry	setSize		file.length
				entry	setTime		file.lastModified
				entry	setUnixMode	(fileMode | (if (file.canExecute) rwxr_xr_x else rw_r__r__))
				outputStream  	putArchiveEntry	entry
				
				(Using fileInputStream file) { inputStream =>
					IO transfer (inputStream, outputStream)
				}
				
				outputStream.closeArchiveEntry()
			}
		}
		finally {
			outputStream.close()
		}
	}
	
	private def pathDirs(path:String):Seq[String]	= 
			(path split "/").init.inits.toList.init.reverse map { _ mkString ("", "/", "/") }
}
