package kornell.server.jdbc.repository


import java.sql.ResultSet
import kornell.server.repository.Entities
import kornell.core.entity.CourseVersion
import kornell.server.jdbc.SQL._

class CourseVersionRepo(uuid:String) {
    implicit def toCourseVersion(rs:ResultSet):CourseVersion =
      Entities.newCourseVersion(rs.getString("uuid"),rs.getString("name"),
          rs.getString("course_uuid"),rs.getString("repository_uuid"),
          rs.getDate("versionCreatedAt"),rs.getString("distributionPrefix"))
      
	def get = sql"""
		select * from CourseVersion where uuid=$uuid
	""".get[CourseVersion]
}

object CourseVersionRepo{
  def apply(uuid:String) = new CourseVersionRepo(uuid:String) 
}