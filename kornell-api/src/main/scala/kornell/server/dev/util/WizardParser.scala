package kornell.server.dev.util

import java.math.BigDecimal
import java.lang.Double

import kornell.core.entity.CourseClass
import kornell.core.entity.CourseVersion

import scala.collection.mutable.ListBuffer
import scala.util.parsing.json.JSON
import kornell.core.lom.Content
import kornell.core.lom.Contents
import kornell.core.lom.Topic
import kornell.core.util.StringUtils
import kornell.server.jdbc.repository.CourseVersionsRepo
import kornell.server.repository.LOM
import kornell.server.util.Settings.BUILD_NUM
import kornell.server.util.Settings.toOption

import scala.util.matching.Regex

object WizardParser {

  val topicPattern: Regex = """#\s?(.*)""".r
  val buildNum: String = BUILD_NUM.getOpt.orElse("development_build").get

  def getClassroomJsonMap(courseClass: CourseClass): Map[String, Any] = {
    val courseVersion = CourseVersionsRepo.byCourseClassUUID(courseClass.getUUID).get
    getClassroomJsonMap(courseVersion, courseClass.isSandbox)
  }

  def getClassroomJsonMap(courseVersion: CourseVersion, isSandbox: Boolean): Map[String, Any] = {
    val classroomJson = courseVersion.getClassroomJson
    val classroomJsonPublished = courseVersion.getClassroomJsonPublished
    val jsonString = if (isSandbox && StringUtils.isSome(classroomJson)) {
      Option(classroomJson)
    } else if (StringUtils.isSome(classroomJsonPublished)) {
      Option(classroomJsonPublished)
    } else {
      None
    }
    val jsonParsed = if (jsonString.isDefined) JSON.parseFull(jsonString.get) else None
    if(jsonParsed.isDefined)
      jsonParsed.get.asInstanceOf[Map[String, Any]]
    else
      null
  }

  def findVisitedContent(courseClass: CourseClass, visited: List[String]): Contents = {
    val jsonMap = getClassroomJsonMap(courseClass)
    val result = ListBuffer[Content]()
    var topic: Topic = null
    var index = 1 //should start with 1, in case of the ordering fallback

    if(jsonMap != null){
      val topics: List[Any] = jsonMap("modules").asInstanceOf[List[Any]]
      topics foreach { topicObj =>
        {
          val topicObjMap = topicObj.asInstanceOf[Map[String, Any]]
          topic = LOM.newTopic(topicObjMap("title").asInstanceOf[String])
          result += LOM.newContent(topic)

          val slides: List[Any] = topicObjMap("lectures").asInstanceOf[List[Any]]
          slides foreach { slideObj =>
          {
            val slideObjMap = slideObj.asInstanceOf[Map[String, Any]]

            val fileName = ""
            val title = slideObjMap("title").asInstanceOf[String]
            val uuid = slideObjMap("uuid").asInstanceOf[String]
            //TODO tcfaria KORNELL.PROPERTIES
            val path = "/angular/knlClassroom/index.html?cache-buster=" + buildNum + "#!/lecture?uuid=" + uuid

            val page = LOM.newExternalPage(path, fileName, title, uuid, index)
            page.setVisited(visited.contains(page.getKey))
            val content = LOM.newContent(page)
            if (topic != null)
              topic.getChildren.add(content)
            else
              result += content
            index += 1
          }
          }
        }
      }
    }
    val contents = result.toList
    LOM.newContents(contents)
  }

  def getRequiredScore(courseVersion: CourseVersion, isSandbox: Boolean): BigDecimal = {
    val jsonMap = getClassroomJsonMap(courseVersion, isSandbox)
    if(jsonMap != null){
      val topics: List[Any] = jsonMap("modules").asInstanceOf[List[Any]]
      topics foreach { topicObj =>
        {
          val topicObjMap = topicObj.asInstanceOf[Map[String, Any]]
          val slides: List[Any] = topicObjMap("lectures").asInstanceOf[List[Any]]
          slides foreach { slideObj =>
          {
            val slideObjMap = slideObj.asInstanceOf[Map[String, Any]]
            val slideType = slideObjMap("type").asInstanceOf[String]
            if(slideType == "finalExam" && slideObjMap.contains(("expectedGrade")) && slideObjMap("expectedGrade") != null){
              return new BigDecimal(slideObjMap("expectedGrade").asInstanceOf[Double])
            }
          }
          }
        }
      }
    }
    null
  }

}
