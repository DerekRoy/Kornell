package kornell.server.api

import javax.ws.rs._
import kornell.core.entity.Course
import kornell.server.jdbc.repository.CourseRepo
import kornell.server.service.ContentService
import kornell.server.util.AccessDeniedErr
import kornell.server.util.Conditional.toConditional

class CourseResource(uuid: String) {

  @GET
  @Produces(Array(Course.TYPE))
  def get: Course = {
    CourseRepo(uuid).get
  }.requiring(isPlatformAdmin, AccessDeniedErr())
    .or(isInstitutionAdmin, AccessDeniedErr())
    .or(isPublisher, AccessDeniedErr())
    .get

  @PUT
  @Consumes(Array(Course.TYPE))
  @Produces(Array(Course.TYPE))
  def update(course: Course): Course = {
    CourseRepo(uuid).update(course)
  }.requiring(isPlatformAdmin, AccessDeniedErr())
    .or(isInstitutionAdmin, AccessDeniedErr())
    .or(isPublisher, AccessDeniedErr())
    .get

  @DELETE
  @Produces(Array(Course.TYPE))
  def delete(): Course = {
    CourseRepo(uuid).delete
  }.requiring(isPlatformAdmin, AccessDeniedErr())
    .or(isInstitutionAdmin, AccessDeniedErr())
    .or(isPublisher, AccessDeniedErr())
    .get

  @POST
  @Path("copy")
  @Produces(Array(Course.TYPE))
  def copy: Course = {
    CourseRepo(uuid).copy
  }.requiring(isPlatformAdmin, AccessDeniedErr())
    .or(isInstitutionAdmin, AccessDeniedErr())
    .or(isPublisher, AccessDeniedErr())
    .get

  @GET
  @Path("uploadUrl")
  @Produces(Array("text/plain"))
  def getUploadUrl(@QueryParam("filename") filename: String, @QueryParam("path") path: String): String = {
    ContentService.getCourseUploadUrl(uuid, filename, path)
  }.requiring(isPlatformAdmin, AccessDeniedErr())
    .or(isInstitutionAdmin, AccessDeniedErr())
    .or(isPublisher, AccessDeniedErr())
    .get

  @GET
  @Path("wizardContentUploadUrl")
  @Produces(Array("text/plain"))
  def getUploadUrl(@QueryParam("filename") filename: String): String = {
    ContentService.getCourseWizardContentUploadUrl(uuid, filename)
  }.requiring(isPlatformAdmin, AccessDeniedErr())
    .or(isInstitutionAdmin, AccessDeniedErr())
    .or(isPublisher, AccessDeniedErr())
    .get

}

object CourseResource {
  def apply(uuid: String) = new CourseResource(uuid)
}
