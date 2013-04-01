import sbt._
import Keys.TaskStreams
import Project.Initialize

import java.io.FileInputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry

import org.apache.commons.compress.archivers.zip._
import org.apache.commons.compress.utils.IOUtils

object ZipperPlugin {
	/** build the zip file */
	val zipperBuild		= TaskKey[File]("zipper")
	/** files to be included in the bundle zip */
	val zipperFiles		= TaskKey[Seq[(File,String)]]("zipper-files")
	/** default name for common directory and bundle zip */
	val zipperName		= SettingKey[String]("zipper-name")
	/** common directory prefix for all files in the bundle zip */
	val zipperPrefix	= SettingKey[String]("zipper-prefix")
	/** bundle zip file to be created */
	val zipperZip		= SettingKey[File]("zipper-zip")
	/** directory for the bundle zip file */
	val zipperTarget	= SettingKey[File]("zipper-target")
	
	lazy val zipperSettings:Seq[Project.Setting[_]]	= Seq(
		zipperBuild		<<= zipperTask,
		zipperFiles		:= Seq.empty,
		zipperTarget	<<= Keys.crossTarget { 
			_ / "zipper"
		},
		zipperName		<<= (Keys.name, Keys.version) { 
			(name, version)	=> name + "-" + version
		},
		zipperPrefix	<<= zipperName { 
			_ + "/" 
		},
		zipperZip		<<= (zipperTarget, zipperName)	{ 
			(zipperTarget, zipperName)	=> zipperTarget / (zipperName + ".zip")
		}
	)
	
	private def zipperTask:Initialize[Task[File]] = 
			(Keys.streams, zipperFiles, zipperPrefix, zipperZip) map zipperTaskImpl
	
	private def zipperTaskImpl(streams:TaskStreams, files:Seq[(File,String)], prefix:String, zip:File):File	= {
		streams.log info ("creating bundle zip as " + zip)
		IO delete zip
		zip.getParentFile.mkdirs()
		
		val rwxr_xr_x	= Integer parseInt ("755", 8)
		val rw_r__r__	= Integer parseInt ("644", 8)
		
		def mode(file:File):Option[Int]	= 
				if (file.canExecute)	Some(rwxr_xr_x) 
				else					Some(rw_r__r__)
			
		val extended	= files map {
			case (file, path)	=> (file, prefix + path, mode(file)) 
		} 
		bundle(extended, zip)
		
		zip
	}
	
	/** files must be isFile, paths must use a forward slash, unix mode is optional */
	private def bundle(sources:Seq[(File,String,Option[Int])], outputZip:File) {
		val outputStream	= new ZipArchiveOutputStream(outputZip)
		// outputStream	setMethod	ZipOutputStream.DEFLATED
		// outputStream	setLevel	0
		try {
			val now			= System.currentTimeMillis
			val emptyCRC	= new CRC32().getValue
			val buffer		= new Array[Byte](16384)
			
			val sourceDirs:Seq[String]	= (sources map { _._2 } flatMap pathDirs).distinct
				
			sourceDirs foreach { path =>
				val entry	= new ZipArchiveEntry(path)
				entry	setMethod	ZipEntry.STORED
				entry	setSize		0
				entry	setTime		now
				entry	setCrc		emptyCRC
				outputStream  	putArchiveEntry entry
				outputStream.closeArchiveEntry()
			}
			
			sources foreach { case (file, path, mode) => 
				val entry	= new ZipArchiveEntry(path)
				entry	setMethod	ZipEntry.STORED
				entry	setSize		file.length
				entry	setTime		file.lastModified
				mode			foreach			entry.setUnixMode
				outputStream  	putArchiveEntry	entry
				
				val inputStream	= new FileInputStream(file)
				try {
					IOUtils copy (inputStream, outputStream)
				}
				finally {
					inputStream.close()
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
