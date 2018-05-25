package kornell.server.content

import java.io.{FileInputStream, InputStream}
import java.net.URL
import java.nio.file.{Files, Paths}

import kornell.core.entity.ContentRepository
import kornell.core.util.StringUtils
import kornell.server.jdbc.repository.InstitutionRepo
import org.apache.commons.io.FileUtils

import scala.io.Source
import scala.util.Try

class FSContentManager(fsRepo: ContentRepository) extends SyncContentManager {
  def source(keys: String*): Try[Source] = Try {
    val path = Paths.get(fsRepo.getPath, url(keys: _*))
    Source.fromFile(path.toFile, "UTF-8")
  }

  def inputStream(keys: String*): Try[InputStream] = Try {
    val path = Paths.get(fsRepo.getPath, url(keys: _*))
    val file = path.toFile
    if (file.exists()) new FileInputStream(file)
    else throw new IllegalArgumentException(s"$path not found")
  }

  def put(input: InputStream, contentLength: Int, contentType: String, contentDisposition: String, metadataMap: Map[String, String], keys: String*): Unit = {
    val fullFilePath = (if (fsRepo.getPath.endsWith("/")) fsRepo.getPath else fsRepo.getPath + "/") + url(keys: _*)
    val directory = Paths.get(fullFilePath).toFile.getParentFile
    if (!directory.exists) directory.mkdirs
    Files.copy(input, Paths.get(fullFilePath))
  }

  def delete(keys: String*): Unit = {
    val fullFilePath = (if (fsRepo.getPath.endsWith("/")) fsRepo.getPath else fsRepo.getPath + "/") + StringUtils.mkurl("", keys: _*)
    val file = Paths.get(fullFilePath).toFile
    if (file.exists) Files.delete(Paths.get(fullFilePath))
  }

  def deleteFolder(keys: String*): Unit = {
    val fullFilePath = (if (fsRepo.getPath.endsWith("/")) fsRepo.getPath else fsRepo.getPath + "/") + StringUtils.mkurl("", keys: _*)
    val file = Paths.get(fullFilePath).toFile
    if (file.exists && file.isDirectory) FileUtils.deleteDirectory(file)
  }

  def getUploadUrl(path: String, contentType: String): String = {
    val institution = InstitutionRepo(fsRepo.getInstitutionUUID).get
    val institutionBaseUrl = new URL(institution.getBaseURL)
    val baseUrl = institutionBaseUrl.getProtocol + "://" + institutionBaseUrl.getHost +
      (if (institutionBaseUrl.getPort != -1 && institutionBaseUrl.getPort != 80 && institutionBaseUrl.getPort != 443) ":" + institutionBaseUrl.getPort else "")
    val fullPath = StringUtils.mkurl(baseUrl, "upload", path)
    fullPath
  }

  def getPrefix: String = fsRepo.getPrefix
}
