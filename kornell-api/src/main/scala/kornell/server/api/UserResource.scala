package kornell.server.api

import javax.ws.rs._
import javax.ws.rs.core.MediaType._
import scala.reflect.BeanProperty
import javax.ws.rs.core.Response._
import javax.ws.rs.core.Response.Status._
import javax.ws.rs.core.Context
import javax.ws.rs.core.SecurityContext
import javax.persistence.EntityManager
import javax.inject.Inject
import javax.enterprise.context.RequestScoped
import kornell.server.repository.slick.plain.Persons
import kornell.server.repository.TOs
import kornell.server.repository.jdbc.Auth
import kornell.server.repository.jdbc.Registrations
import kornell.core.shared.to.UserInfoTO
import kornell.server.repository.jdbc.SQLInterpolation._

@Produces(Array(UserInfoTO.TYPE))
@Path("user")
class UserResource extends Resource with TOs{

  @GET
  def get(implicit @Context sc: SecurityContext):Option[UserInfoTO] =
    Auth.withPerson { p =>
    	val user = newUserInfoTO
    	user.setPerson(p)
    	val signingNeeded = Registrations.signingNeeded(p)
    	user.setSigningNeeded(signingNeeded)
    	Option(user)
  }
  
  @PUT
  @Path("placeChange")
  def putPlaceChange(implicit @Context sc: SecurityContext,newPlace:String) = 
    Auth.withPerson { p => 
    	sql"""
    	update Person set lastPlaceVisited=$newPlace
    	where uuid=${p.getUUID}
    	""".executeUpdate
    }
  
  
}
