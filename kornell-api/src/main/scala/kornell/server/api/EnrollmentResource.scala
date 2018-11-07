package kornell.server.api

import java.util

import javax.ws.rs._
import javax.ws.rs.core.Response
import kornell.core.entity._
import kornell.core.lom.Contents
import kornell.core.to.EnrollmentLaunchTO
import kornell.server.dev.util.WizardParser
import kornell.server.ep.EnrollmentSEP
import kornell.server.jdbc.PreparedStmt
import kornell.server.jdbc.SQL._
import kornell.server.jdbc.repository.CourseClassesRepo.byEnrollment
import kornell.server.jdbc.repository.{AuthRepo, CertificatesDetailsRepo, CourseClassesRepo, CourseRepo, CourseVersionRepo, EnrollmentRepo, PersonRepo}
import kornell.server.repository.{ContentRepository, Entities, TOs}
import kornell.server.scorm12.SCORM12
import kornell.server.util.AccessDeniedErr
import kornell.server.util.Conditional.toConditional

import scala.collection.JavaConverters._

@Produces(Array(Enrollment.TYPE))
class EnrollmentResource(uuid: String) {
  lazy val enrollment: Enrollment = get
  lazy val enrollmentRepo = EnrollmentRepo(uuid)

  def get: Enrollment = enrollmentRepo.get

  @GET
  def first: Option[Enrollment] = enrollmentRepo.first

  @PUT
  @Consumes(Array(Enrollment.TYPE))
  @Produces(Array(Enrollment.TYPE))
  def update(enrollment: Enrollment): Enrollment = {
    EnrollmentRepo(enrollment.getUUID).update(enrollment)
  }.requiring(PersonRepo(getAuthenticatedPersonUUID).hasPowerOver(enrollment.getPersonUUID), AccessDeniedErr())
    .get

  @Path("actoms/{actomKey}")
  def actom(@PathParam("actomKey") actomKey: String) = ActomResource(uuid, actomKey)

  @GET
  @Path("contents")
  @Produces(Array(Contents.TYPE))
  def contents: Option[Contents] = AuthRepo().withPerson { person =>
    first map { e =>
      ContentRepository.findVisitedContent(e, person)
    }
  }

  def findRootOf(uuid: String): String = {
    val parent = sql"""
   select parentEnrollmentUUID from Enrollment where uuid = $uuid
   """.first[String] match {
      case Some(p) => p
      case None => uuid
    }
    parent
  }

  def findFamilyOf(parentUUID: String): List[String] = {
    val selfie = List(parentUUID)
    val children = sql"""
   select uuid from Enrollment where parentEnrollmentUUID = $parentUUID
  """.map[String]
    val result = selfie ++ children
    result
  }

  def findEnrollmentsFamilyUUIDs: List[String] = {
    val selfie = Set(uuid)
    val family = findFamilyOf(findRootOf(uuid)).toSet
    val result = (selfie ++ family).toList
    result
  }

  @GET
  @Path("launch")
  @Produces(Array(EnrollmentLaunchTO.TYPE))
  def launch(): EnrollmentLaunchTO = AuthRepo().withPerson { person =>

    val courseClassUUID = Option(enrollment.getCourseClassUUID).getOrElse {
      val parent = EnrollmentRepo(enrollment.getParentEnrollmentUUID).first
      parent.map { _.getCourseClassUUID() }.orNull
    }
    val courseClass = if (courseClassUUID != null) {
      CourseClassesRepo(courseClassUUID).get
    } else null
    val courseVersion = CourseVersionRepo(courseClass.getCourseVersionUUID).get

    val eLaunch: EnrollmentLaunchTO = TOs.newEnrollmentLaunchTO

    val eContents = contents.get
    eLaunch.setContents(eContents)

    val enrollments: List[String] = findEnrollmentsFamilyUUIDs
    val eEntries = getEntries(enrollments)
    val mEntries = eEntries.getEnrollmentEntriesMap.asScala

    for {
      (_, enrollmentEntries) <- mEntries.par
      (_, actomEntries) <- enrollmentEntries.getActomEntriesMap.asScala
    } {
      val entriesMap = actomEntries.getEntries
      val launchedMap = SCORM12.initialize(entriesMap, person, enrollment, courseClass)
      entriesMap.putAll(launchedMap)
      actomEntries.setEntries(entriesMap)
    }

    if (courseClass != null) {
      if(ContentSpec.SCORM12.equals(CourseRepo(courseVersion.getCourseUUID).get.getContentSpec)) {
        //if the enrollment is on a class and it's a SCORM12 version
        //initialize the enrollmentEntries map if no attribute exists
        val enrollmentEntries = Option(eEntries.getEnrollmentEntriesMap.get(enrollment.getUUID)) match {
          case Some(ee) => ee
          case None => {
            val ee = Entities.newEnrollmentEntries
            eEntries.getEnrollmentEntriesMap.put(enrollment.getUUID, ee)
            ee
          }
        }

        //initialize for each actom
        eContents.getChildren.asScala.foreach { topic =>
          if (topic.getTopic != null) {
            topic.getTopic.getChildren.asScala.foreach { externalPage =>
              val key = externalPage.getExternalPage.getKey
              Option(enrollmentEntries.getActomEntriesMap.get(key)) match {
                case Some(ae) => ae
                case None => {
                  val entriesMap = new util.HashMap[String, String]()
                  val launchedMap = SCORM12.initialize(entriesMap, person, enrollment, courseClass)
                  entriesMap.putAll(launchedMap)
                  val ae = Entities.newActomEntries(enrollment.getUUID, key, entriesMap)
                  enrollmentEntries.getActomEntriesMap.put(key, ae)
                  ae
                }
              }
            }
          }
        }
      }
      val hasCertificateDetails = CertificatesDetailsRepo.findCertificateDetails(courseVersion.getCourseUUID, courseVersion.getUUID, courseClass.getUUID).isDefined
      eLaunch.setShowCertificationTab(hasCertificateDetails && courseClass.getRequiredScore != null)
    }

    eLaunch.setEnrollmentEntries(eEntries)
    EnrollmentSEP.onProgress(uuid)
    eLaunch
  }

  def getEntries(es: List[String]): EnrollmentsEntries = {
    val esEntries: EnrollmentsEntries = Entities.newEnrollmentsEntries()
    val esEntriesMap = esEntries.getEnrollmentEntriesMap

    val sql = s"""
      select * from ActomEntries
      where enrollmentUUID IN (${es.map { s => s"'${s}'" }.mkString(",")})
      order by enrollmentUUID, actomKey
    """

    new PreparedStmt(sql, List()).foreach { rs =>
      val enrollmentUUID = rs.getString("enrollmentUUID")
      val actomKey = rs.getString("actomKey")
      val entryKey = rs.getString("entryKey")
      val entryValue = rs.getString("entryValue")

      val enrollmentEntries = Option(esEntriesMap.get(enrollmentUUID)) match {
        case Some(e) => e
        case None => {
          val e = Entities.newEnrollmentEntries
          esEntriesMap.put(enrollmentUUID, e)
          e
        }
      }

      val aeMap = enrollmentEntries.getActomEntriesMap

      val actomEntries = Option(aeMap.get(actomKey)) match {
        case Some(a) => a
        case None => {
          val a: ActomEntries = Entities.newActomEntries(enrollmentUUID, actomKey, new util.HashMap[String, String]())
          aeMap.put(actomKey, a)
          a
        }
      }

      actomEntries.getEntries.put(entryKey, entryValue)
    }
    esEntries
  }

  @GET
  @Path("approved")
  @Produces(Array("text/plain"))
  def approved: String = {
    val e = first.get
    if (Assessment.PASSED == e.getAssessment) {
      if (e.getAssessmentScore != null)
        e.getAssessmentScore.toString
      else
        ""
    } else {
      ""
    }
  }

  @PUT
  @Path("addDaysToExpiryDate/{numberOfDays}")
  def addDaysToExpiryDate(@PathParam("numberOfDays") numberOfDays: String): Response = {
    EnrollmentRepo(uuid).addDaysToExpiryDate(numberOfDays)
    Response.noContent.build
  }.requiring(isPlatformAdmin, AccessDeniedErr())
    .or(isInstitutionAdmin, AccessDeniedErr())
    .or(isCourseClassAdmin(CourseClassesRepo.byEnrollment(uuid).get.getUUID), AccessDeniedErr())
    .get

  @GET
  @Produces(Array(EnrollmentsEntries.TYPE))
  def getEntries: EnrollmentsEntries = getEntries(List(uuid))

}
