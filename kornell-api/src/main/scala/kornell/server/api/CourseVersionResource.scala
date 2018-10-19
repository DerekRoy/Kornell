package kornell.server.api

import javax.ws.rs._
import javax.ws.rs.core.Response
import kornell.core.entity.CourseVersion
import kornell.core.to.CourseVersionTO
import kornell.server.jdbc.repository.{CourseVersionRepo, CourseVersionsRepo}
import kornell.server.service.{ContentService, SandboxService}
import kornell.server.util.AccessDeniedErr
import kornell.server.util.Conditional.toConditional

class CourseVersionResource(uuid: String) {

  @GET
  @Produces(Array(CourseVersionTO.TYPE))
  def get: Option[CourseVersionTO] = {
    CourseVersionsRepo.getCourseVersionTO(getAutenticatedPersonInstitutionUUID, uuid)
  }.requiring(isPlatformAdmin, AccessDeniedErr())
    .or(isInstitutionAdmin, AccessDeniedErr())
    .or(isPublisher, AccessDeniedErr())
    .get

  @PUT
  @Consumes(Array(CourseVersion.TYPE))
  @Produces(Array(CourseVersion.TYPE))
  def update(courseVersion: CourseVersion, @QueryParam("skipAudit") skipAudit: Boolean): CourseVersion = {
    CourseVersionRepo(uuid).update(courseVersion, skipAudit, getAutenticatedPersonInstitutionUUID)
  }.requiring(isPlatformAdmin, AccessDeniedErr())
    .or(isInstitutionAdmin, AccessDeniedErr())
    .or(isPublisher, AccessDeniedErr())
    .get

  @DELETE
  @Produces(Array(CourseVersion.TYPE))
  def delete(): CourseVersion = {
    CourseVersionRepo(uuid).delete
  }.requiring(isPlatformAdmin, AccessDeniedErr())
    .or(isInstitutionAdmin, AccessDeniedErr())
    .or(isPublisher, AccessDeniedErr())
    .get

  @POST
  @Path("copy")
  @Produces(Array(CourseVersion.TYPE))
  def copy: CourseVersion = {
    CourseVersionRepo(uuid).copy
  }.requiring(isPlatformAdmin, AccessDeniedErr())
    .or(isInstitutionAdmin, AccessDeniedErr())
    .or(isPublisher, AccessDeniedErr())
    .get

  @GET
  @Path("uploadUrl")
  @Produces(Array("text/plain"))
  def getUploadUrl(@QueryParam("filename") filename: String, @QueryParam("path") path: String): String = {
    ContentService.getCourseVersionUploadUrl(uuid, filename, path)
  }.requiring(isPlatformAdmin, AccessDeniedErr())
    .or(isInstitutionAdmin, AccessDeniedErr())
    .or(isPublisher, AccessDeniedErr())
    .get

  @GET
  @Path("contentUploadUrl")
  @Produces(Array("text/plain"))
  def getUploadUrl: String = {
    ContentService.getCourseVersionContentUploadUrl(uuid)
  }.requiring(isPlatformAdmin, AccessDeniedErr())
    .or(isInstitutionAdmin, AccessDeniedErr())
    .or(isPublisher, AccessDeniedErr())
    .get

  @PUT
  @Path("resetSandbox")
  def resetSandbox: Response = {
    SandboxService.resetEnrollments(uuid, getAutenticatedPersonInstitutionUUID)
    Response.noContent.build
  }.requiring(isPlatformAdmin, AccessDeniedErr())
    .or(isInstitutionAdmin, AccessDeniedErr())
    .or(isPublisher, AccessDeniedErr())
    .get

  @GET
  @Path("sandboxEnrollment")
  @Produces(Array("text/plain"))
  def getSandboxEnrollment: String = {
    SandboxService.getEnrollment(uuid)
  }.requiring(isPlatformAdmin, AccessDeniedErr())
    .or(isInstitutionAdmin, AccessDeniedErr())
    .or(isPublisher, AccessDeniedErr())
    .get
}

object CourseVersionResource {
  def apply(uuid: String) = new CourseVersionResource(uuid)
}
