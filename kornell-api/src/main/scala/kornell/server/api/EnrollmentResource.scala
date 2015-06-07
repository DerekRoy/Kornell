package kornell.server.api

import scala.collection.JavaConverters._
import javax.ws.rs.Consumes
import javax.ws.rs.DELETE
import javax.ws.rs.GET
import javax.ws.rs.PUT
import javax.ws.rs.Path
import javax.ws.rs.PathParam
import javax.ws.rs.Produces
import javax.ws.rs.core.Context
import javax.ws.rs.core.SecurityContext
import kornell.core.entity.Assessment
import kornell.core.entity.Enrollment
import kornell.core.lom.Contents
import kornell.server.jdbc.SQL._
import kornell.server.jdbc.repository.AuthRepo
import kornell.server.jdbc.repository.CourseClassRepo
import kornell.server.jdbc.repository.EnrollmentRepo
import kornell.server.jdbc.repository.PersonRepo
import kornell.server.util.Conditional.toConditional
import kornell.server.util.Err
import kornell.server.util.AccessDeniedErr
import kornell.server.repository.ContentRepository
import java.util.HashMap
import kornell.core.entity.ActomEntries
import kornell.core.entity.EnrollmentEntries
import kornell.server.repository.Entities
import kornell.core.to.EnrollmentLaunchTO
import kornell.server.repository.TOs
import kornell.server.scorm12.SCORM12

@Produces(Array(Enrollment.TYPE))
class EnrollmentResource(uuid: String) {
  lazy val enrollment = get
  lazy val enrollmentRepo = EnrollmentRepo(uuid)

  def get = enrollmentRepo.get

  @GET
  def first = enrollmentRepo.first

  @PUT
  @Produces(Array("text/plain"))
  @Consumes(Array(Enrollment.TYPE))
  def update(enrollment: Enrollment) = {
    EnrollmentRepo(enrollment.getUUID).update(enrollment)
  }
    .requiring(PersonRepo(getAuthenticatedPersonUUID).hasPowerOver(enrollment.getPersonUUID), AccessDeniedErr())
    .get

  @Path("actoms/{actomKey}")
  def actom(@PathParam("actomKey") actomKey: String) = ActomResource(uuid, actomKey)

  @GET
  @Path("contents")
  @Produces(Array(Contents.TYPE))
  def contents(): Option[Contents] = AuthRepo().withPerson { person =>
    first map { e =>
      ContentRepository.findKNLVisitedContent(e)
    }
  }

  @GET
  @Path("launch")
  @Produces(Array(EnrollmentLaunchTO.TYPE))
  def launch() = {
    val eLaunch: EnrollmentLaunchTO = TOs.newEnrollmentLaunchTO

    val eContents = contents.get
    eLaunch.setContents(eContents)

    val eEntries = getEntries
    val mEntries = eEntries.getModuleEntries.asScala
    for {
      (enrollmentUUID, actomEntriesMap) <- mEntries
      (actomKey,actomEntries) <- actomEntriesMap.asScala
    } {            
      actomEntries.setEntries(SCORM12.dataModel.initialize(actomEntries.getEntries))
    }

    eLaunch.setEnrollmentEntries(eEntries)
    eLaunch
  }

  @GET
  @Path("approved")
  @Produces(Array("application/octet-stream"))
  def approved = {
    val e = first.get
    if (Assessment.PASSED == e.getAssessment) {
      if (e.getAssessmentScore != null)
        e.getAssessment.toString()
      else
        ""
    } else {
      ""
    }
  }

  @DELETE
  @Produces(Array(Enrollment.TYPE))
  def delete(implicit @Context sc: SecurityContext) = {
    val enrollmentRepo = EnrollmentRepo(uuid)
    val enrollment = enrollmentRepo.get
    enrollmentRepo.delete(uuid)
    enrollment
  }.requiring(isPlatformAdmin, AccessDeniedErr())
    .or(isInstitutionAdmin(CourseClassRepo(EnrollmentRepo(uuid).get.getCourseClassUUID).get.getInstitutionUUID), AccessDeniedErr())
    .or(isCourseClassAdmin(EnrollmentRepo(uuid).get.getCourseClassUUID), AccessDeniedErr())

  @GET
  @Produces(Array(EnrollmentEntries.TYPE))
  def getEntries() = {
    val eEntries: EnrollmentEntries = Entities.newEnrollmentEntries()
    val mEntries = eEntries.getModuleEntries();

    sql"""
      select * from ActomEntries 
      where enrollment_uuid in (
        select uuid 
        from Enrollment 
        where uuid= $uuid
           or parentEnrollmentUUID = $uuid )
      order by enrollment_uuid, actomKey
    """.foreach { rs =>

      val enrollmentUUID = rs.getString("enrollment_uuid")
      val actomKey = rs.getString("actomKey")
      val entryKey = rs.getString("entryKey")
      val entryValue = rs.getString("entryValue")

      val enrollmentMap = Option(mEntries.get(enrollmentUUID)) match {
        case Some(e) => e
        case None => {
          val e = new HashMap[String, ActomEntries]()
          mEntries.put(enrollmentUUID, e)
          e
        }
      }

      val actomEntries = Option(enrollmentMap.get(actomKey)) match {
        case Some(a) => a
        case None => {
          val a: ActomEntries = Entities.newActomEntries(enrollmentUUID, actomKey, new HashMap[String, String]())
          enrollmentMap.put(actomKey, a)
          a
        }
      }

      actomEntries.getEntries.put(entryKey, entryValue)
    }
    eEntries
  }

}