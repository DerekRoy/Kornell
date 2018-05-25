package kornell.server.content

import java.io.InputStream
import java.util
import java.util.logging.Logger

import com.microsoft.azure.storage.{CloudStorageAccount, SharedAccessProtocols}
import com.microsoft.azure.storage.blob.{CloudBlobClient, SharedAccessBlobPermissions, SharedAccessBlobPolicy}
import kornell.core.entity.ContentRepository
import kornell.core.util.StringUtils._
import org.joda.time.DateTime

import scala.io.{BufferedSource, Source}
import scala.util.Try

class AzureContentManager(repo: ContentRepository)
    extends SyncContentManager {

  val logger: Logger = Logger.getLogger(classOf[AzureContentManager].getName)

  lazy val blobClient: CloudBlobClient = if (isSome(repo.getAccountName)) {
    val storageConnectionString =
      "DefaultEndpointsProtocol=https;" + "AccountName=" + repo.getAccountName + ";" + "AccountKey=" + repo.getAccountKey
    CloudStorageAccount.parse(storageConnectionString).createCloudBlobClient()
  } else {
    throw new Exception("invalidConfiguration")
  }

  def source(keys: String*): Try[BufferedSource] =
    inputStream(keys: _*).map { Source.fromInputStream(_, "UTF-8") }

  def inputStream(keys: String*): Try[InputStream] = Try {
    val fqkn = url(keys: _*)
    logger.finest(s"loading key [ $fqkn ]")
    try {
      val container = blobClient.getContainerReference(repo.getContainer)
      container.getBlobReferenceFromServer(fqkn).openInputStream
    } catch {
      case e: Throwable => {
        logger.warning("Could not load object from Azure.")
        throw e
      }
    }
  }

  def put(value: InputStream, contentLength: Int, contentType: String, contentDisposition: String, metadataMap: Map[String, String], keys: String*): Unit = {
    val container = blobClient.getContainerReference(repo.getContainer)
    val blob = container.getBlockBlobReference(url(keys: _*))
    blob.getProperties.setContentDisposition(contentDisposition)
    blob.getProperties.setContentType(contentType)
    blob.upload(value, contentLength)
  }

  def delete(keys: String*): Unit = {
    // keys we support delete for already have repo prefix appended
    logger.info("Trying to delete object [ " + mkurl("", keys: _*) + " ]")
    val container = blobClient.getContainerReference(repo.getContainer)
    val blob = container.getBlockBlobReference(mkurl("", keys: _*))
    blob.delete()
  }

  def deleteFolder(keys: String*): Unit = {
    // no easy method for folder delete in Azure, we need to loop over all files so let's skip
  }

  def getUploadUrl(path: String, contentType: String): String = {
    val sasConstraints = new SharedAccessBlobPolicy
    sasConstraints.setSharedAccessExpiryTime(DateTime.now.plusSeconds(5).toDate)
    sasConstraints.setPermissions(util.EnumSet.of(SharedAccessBlobPermissions.WRITE))

    val blob = blobClient.getContainerReference(repo.getContainer).getBlockBlobReference(path)
    blob.getUri.toString + "?" + blob.generateSharedAccessSignature(sasConstraints,
      null, null, null, SharedAccessProtocols.HTTPS_ONLY)
  }

  def getPrefix: String = repo.getPrefix

}
