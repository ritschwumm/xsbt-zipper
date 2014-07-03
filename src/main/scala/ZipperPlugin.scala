import sbt._
import Keys.TaskStreams
import Project.Initialize

import java.io.FileInputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry

import org.apache.commons.compress.archivers.zip._
import org.apache.commons.compress.utils.IOUtils

object ZipperPlugin extends Plugin {
	val zipper			= taskKey[File]("build the zip file and return it")
	val zipperFiles		= taskKey[Traversable[(File,String)]]("files to be included in the bundle zip")
	val zipperBundle	= settingKey[String]("default name for common directory prefix and bundle zip")
	val zipperPrefix	= settingKey[Option[String]]("common directory prefix for all files in the bundle zip")
	val zipperExtension	= settingKey[String]("file extension for the bundle zip")
	val zipperName		= settingKey[String]("file name of the bundle zip")
	val zipperTarget	= settingKey[File]("directory for the bundle zip file")
	val zipperZip		= settingKey[File]("bundle zip file to be created")
	
	lazy val zipperSettings:Seq[Def.Setting[_]]	=
			Seq(
				zipper	:= 
						zipperTaskImpl(
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
				zipperTarget	:= Keys.crossTarget.value / "zipper",
				zipperZip		:= zipperTarget.value / zipperName.value
			)
	
	private def zipperTaskImpl(
		streams:TaskStreams,
		files:Traversable[(File,String)], 
		prefix:Option[String],
		zip:File
	):File	= {
		streams.log info s"creating bundle zip ${zip}"
		IO delete zip
		zip.getParentFile.mkdirs()
		
		type Mapping	= (File,String)
		val addPrefix:Mapping=>Mapping	= prefix match {
			case Some(s)	=> modifySnd(s + "/" + (_:String))
			case None		=> identity
		}
		val prefixed	= files map addPrefix
		bundle(streams, prefixed, zip)
		
		zip
	}
	
	private def modifySnd[R,S,T](func:S=>T)(it:(R,S)):(R,T)	= (it._1, func(it._2))
	
	private val fileMode	= octal("100000")
	private val dirMode		= octal("040000")
	private val linkMode	= octal("120000")
		
	private val rwxr_xr_x	= octal("755")
	private val rw_r__r__	= octal("644")
	
	/** paths must use a forward slash, unix mode is optional, symlinks are ignored */
	private def bundle(streams:TaskStreams, sources:Traversable[(File,String)], outputZip:File) {
		val outputStream	= new ZipArchiveOutputStream(outputZip)
		// outputStream	setMethod	ZipOutputStream.DEFLATED
		// outputStream	setLevel	0
		try {
			val sourceDirectories		= sources filter { _._1.isDirectory	}
			val sourceFiles				= sources filter { _._1.isFile		}
			
			// ensure every file has a parent directory
			val sourceDirectoryPaths	= sourceDirectories map { _._2 + "/" }
			val sourceParentPaths		= sourceFiles		map { _._2 } flatMap pathDirs
			val zipDirectories			= (sourceDirectoryPaths ++ sourceParentPaths).toSeq.distinct
			
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
		
	private def octal(s:String):Int	= Integer parseInt (s, 8)
}
