package kornell.server.service

import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest
import com.amazonaws.HttpMethod
import org.joda.time.DateTime
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.auth.BasicAWSCredentials
import kornell.core.util.StringUtils._
import kornell.server.jdbc.repository.CourseVersionRepo
import kornell.server.jdbc.repository.CourseClassRepo
import kornell.server.jdbc.repository.CourseRepo
import kornell.server.jdbc.repository.InstitutionRepo
import kornell.server.content.ContentManagers
import kornell.server.jdbc.repository.ContentRepositoriesRepo
import java.util.Date
import kornell.server.jdbc.repository.PersonRepo
import kornell.server.jdbc.repository.CoursesRepo

object S3Service {
  
  def PREFIX = "knl"
  def INSTITUTION = "institution"
  def COURSES = "courses"
  def COURSE_VERSIONS = "courseVersions"
  def COURSE_CLASSES = "courseClasses"
  def CERTIFICATES = "certificates"
  def CERTIFICATE_FILENAME = "certificate-bg.jpg"
  def THUMB_FILENAME = "thumb.jpg"
  def CLASSROOM = "classroom"
  
  def getCourseVersionContentUploadUrl(courseVersionUUID: String) = {
    val courseVersion = CourseVersionRepo(courseVersionUUID).get
    val course = CourseRepo(courseVersion.getCourseUUID).get
    val fullPath = mkurl(CLASSROOM, course.getCode, courseVersion.getDistributionPrefix, "upload" + new Date().getTime + ".zip");
    getUploadUrl(CourseRepo(courseVersion.getCourseUUID).get.getInstitutionUUID, fullPath, "application/zip")
  }
  
  def getContentType(fileName: String) = {
    fileName.split('.')(1) match {
      case "png"  => "image/png"
      case "jpg"  => "image/jpg"
      case "jpeg" => "image/jpg"
      case "ico"  => "image/x-icon"
      case _      => "application/octet-stream"
    }
  }
  
  def getCourseAssetUrl(institutionUUID: String, courseUUID: String, fileName: String, path: String) = {
    mkurl(getRepositoryUrl(institutionUUID), mkurl(PREFIX, COURSES, courseUUID, path, fileName))    
  }
  
  def getCourseUploadUrl(courseUUID: String, fileName: String, path: String) = {
    val institutionUUID = CourseRepo(courseUUID).get.getInstitutionUUID
    getUploadUrl(institutionUUID, getCourseAssetUrl(institutionUUID, courseUUID, fileName, path), getContentType(fileName))
  }
  
  def getCourseVersionAssetUrl(institutionUUID: String, courseVersionUUID: String, fileName: String, path: String) = {
    mkurl(getRepositoryUrl(institutionUUID), mkurl(PREFIX, COURSE_VERSIONS, courseVersionUUID, path, fileName))    
  }
  
  def getCourseVersionUploadUrl(courseVersionUUID: String, fileName: String, path: String) = {
    val institutionUUID = CoursesRepo.byCourseVersionUUID(courseVersionUUID).get.getInstitutionUUID
    getUploadUrl(institutionUUID, getCourseVersionAssetUrl(institutionUUID, courseVersionUUID, fileName, path), getContentType(fileName))
  }
  
  def getCourseClassAssetUrl(institutionUUID: String, courseClassUUID: String, fileName: String, path: String) = {
    mkurl(getRepositoryUrl(institutionUUID), mkurl(PREFIX, COURSE_CLASSES, courseClassUUID, path, fileName))    
  }
  
  def getCourseClassUploadUrl(courseClassUUID: String, fileName: String, path: String) = {
    val institutionUUID = CourseClassRepo(courseClassUUID).get.getInstitutionUUID
    getUploadUrl(institutionUUID, getCourseClassAssetUrl(institutionUUID, courseClassUUID, fileName, path), getContentType(fileName))
  }
  
  def getInstitutionUploadUrl(institutionUUID: String, fileName: String) = {
    val path = mkurl(PREFIX, INSTITUTION, fileName)
    getUploadUrl(institutionUUID, path, getContentType(fileName))
  }
  
  def getRepositoryUrl(institutionUUID: String) = {
    val repo = getRepo(institutionUUID)
    mkurl("repository", repo.getUUID)
  }
  
  def getUploadUrl(institutionUUID: String, path: String, contentType: String) = {  
    val repo = getRepo(institutionUUID)
    val presignedRequest = new GeneratePresignedUrlRequest(repo.getBucketName, path)
    presignedRequest.setMethod(HttpMethod.PUT)
    presignedRequest.setExpiration(new DateTime().plusMinutes(1).toDate)
    presignedRequest.setContentType(contentType)
    getAmazonS3Client(institutionUUID).generatePresignedUrl(presignedRequest).toString
  }
  
  def getAmazonS3Client(institutionUUID: String) = {    
    val repo = getRepo(institutionUUID)
    val s3 = if (isSome(repo.getAccessKeyId()))
      new AmazonS3Client(new BasicAWSCredentials(repo.getAccessKeyId(),repo.getSecretAccessKey()))
    else  
      new AmazonS3Client
      
    s3
  }
  
  def getRepo(institutionUUID: String)  = {    
    val institution = InstitutionRepo(institutionUUID).get
    ContentRepositoriesRepo.firstRepository(institution.getAssetsRepositoryUUID).get
  }
}