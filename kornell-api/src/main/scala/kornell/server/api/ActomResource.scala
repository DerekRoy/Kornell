package kornell.server.api

import java.sql.ResultSet
import java.util.HashMap

import scala.collection.JavaConversions._

import javax.servlet.http.HttpServletRequest
import javax.ws.rs.Consumes
import javax.ws.rs.GET
import javax.ws.rs.PUT
import javax.ws.rs.Path
import javax.ws.rs.PathParam
import javax.ws.rs.Produces
import javax.ws.rs.core.Context
import kornell.core.entity.ActomEntries
import kornell.server.ep.EnrollmentSEP
import kornell.server.jdbc.SQL._
import kornell.server.jdbc.repository.ActomEntriesRepo
import kornell.server.repository.Entities

class ActomResource(enrollmentUUID: String, actomURL: String) {
  implicit def toString(rs: ResultSet): String = rs.getString("entryValue")

  @GET
  def get() = actomKey

  val actomKey = if (actomURL.contains("?"))
    actomURL.substring(0, actomURL.indexOf("?"))
  else
    actomURL

  @Path("entries/{entryKey}")
  @Produces(Array("text/plain"))
  @GET
  def getValue(@PathParam("entryKey") entryKey: String) =
    ActomEntriesRepo.getValue(enrollmentUUID, actomKey, entryKey)

  @Path("entries/{entryKey}")
  @Produces(Array("text/plain"))
  @Consumes(Array("text/plain"))
  @PUT
  def putValue(@PathParam("entryKey") entryKey: String, entryValue: String) = {
    updateEventModel(entryKey, entryValue)
    updateQueryModel(entryKey, entryValue)
  }

  def updateEventModel(entryKey: String, entryValue: String) = sql"""
  	insert ignore into ActomEntryChangedEvent (uuid, enrollment_uuid, actomKey, entryKey, entryValue, ingestedAt) 
  	values (${randomUUID}, ${enrollmentUUID} , ${actomKey}, ${entryKey}, ${entryValue}, now())
  """.executeUpdate    
  

  def updateQueryModel(entryKey: String, entryValue: String) = sql"""
  	insert into ActomEntries (uuid, enrollment_uuid, actomKey, entryKey, entryValue) 
  	values (${randomUUID}, ${enrollmentUUID} , ${actomKey}, ${entryKey}, ${entryValue})
  	on duplicate key update entryValue = ${entryValue}
  """.executeUpdate

  @Path("entries")
  @Consumes(Array(ActomEntries.TYPE))
  @Produces(Array(ActomEntries.TYPE))
  @PUT
  def putEntries(@Context req: HttpServletRequest, entries: ActomEntries) = {
    if (entries != null) {
      val actomEntries = entries.getEntries
      for ((key, value) <- actomEntries) putValue(key, value)
      val hasProgress = containsProgress(actomEntries)
      if (hasProgress)
        EnrollmentSEP.onProgress(enrollmentUUID)
      val hasAssessment = containsAssessment(actomEntries)
	    if (hasAssessment) {
	      EnrollmentSEP.onAssessment(enrollmentUUID);
	    }  
    }
    entries
  }

  def containsProgress(entries: java.util.Map[String, String]) =
    entries.containsKey("cmi.core.lesson_status") ||
      entries.containsKey("cmi.core.lesson_location")

  def containsAssessment(entries: java.util.Map[String, String]) =
    entries.containsKey("cmi.core.score.raw") 
      
  @Path("entries")
  @Produces(Array(ActomEntries.TYPE))
  @GET
  def getEntries(): ActomEntries = {
    val entries = Entities.newActomEntries(enrollmentUUID, actomKey, new HashMap[String, String])
    sql"""
  	select * from ActomEntries 
  	where enrollment_uuid=${enrollmentUUID}
  	  and actomKey=${actomKey}""".foreach { rs =>
      entries.getEntries().put(rs.getString("entryKey"), rs.getString("entryValue"))
    }
    entries
  }
}

object ActomResource {
  def apply(enrollmentUUID: String, actomKey: String) = new ActomResource(enrollmentUUID, actomKey);
}