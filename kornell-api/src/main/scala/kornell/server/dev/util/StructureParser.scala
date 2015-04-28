package kornell.server.dev.util

import scala.collection.mutable.ListBuffer
import kornell.core.lom.Topic
import kornell.core.lom.Content
import kornell.server.repository.Entities
import kornell.core.lom.Contents
import scala.io.Source
import scala.io.BufferedSource
import kornell.core.util.StringUtils._
import kornell.server.repository.LOM
import scala.util.Try

object ContentsParser {
  
  val topicPattern = """#\s?(.*)""".r 

  def parse(prefix:String,source: String,visited:List[String]): Contents =
    parseLines(prefix,source.lines,visited)
    
  
  def parseLines(prefix:String,lines:Iterator[String],visited:List[String]) = {
    val result = ListBuffer[Content]()
    var topic: Topic = null
    var index = 0
    lines foreach { line =>
      line match {
        case topicPattern(topicName) => {
          topic = LOM.newTopic(topicName)
          result += LOM.newContent(topic) 
        }
               
        case _ => {
          
          val tokens = line.split(";")          
          val fileName = Try{tokens(0)}.getOrElse("")
          val title = Try{tokens(1)}.getOrElse("")
          val actomKey = Try{tokens(2)}.getOrElse(fileName)
          
          val page = LOM.newExternalPage(prefix,fileName,title,actomKey,index)
          page.setVisited(visited.contains(page.getKey()))
          val content = LOM.newContent(page)
          if (topic != null) topic.getChildren().add(content)
          else result += content
          index += 1
        }
        
      }
    }
    val contents = result.toList
    LOM.newContents(contents)
  }
}