package kornell.server.jdbc.repository

import java.sql.ResultSet
import kornell.server.repository.Entities._
import kornell.server.repository.Entities
import kornell.core.entity.Person
import kornell.core.entity.Institution
import kornell.server.jdbc.SQL._
import kornell.server.repository.TOs
import kornell.core.entity.AuditedEntityType
import java.util.Date
import kornell.core.util.UUID
import kornell.core.entity.InstitutionType

object InstitutionsRepo {
  
  def create(institution: Institution): Institution = {
    if (institution.getUUID == null) {
      institution.setUUID(UUID.random)
    }
    if (institution.getActivatedAt == null) {
      institution.setActivatedAt(new Date)
    }
    sql"""
    | insert into Institution (uuid,name,terms,assetsURL,baseURL,demandsPersonContactDetails,validatePersonContactDetails,fullName,allowRegistration,allowRegistrationByUsername,activatedAt,skin,billingType,institutionType,dashboardVersionUUID,internationalized,useEmailWhitelist) 
    | values(
    | ${institution.getUUID},
    | ${institution.getName},
    | ${institution.getTerms},
    | ${institution.getAssetsURL},
    | ${institution.getBaseURL},
    | ${institution.isDemandsPersonContactDetails},
    | ${institution.isValidatePersonContactDetails},
    | ${institution.getFullName},
    | ${institution.isAllowRegistration},
    | ${institution.isAllowRegistrationByUsername},
    | ${institution.getActivatedAt},
    | ${institution.getSkin},
    | ${institution.getBillingType.toString},
    | ${institution.getInstitutionType.toString},
    | ${institution.getDashboardVersionUUID},
    | ${institution.isInternationalized},
    | ${institution.isUseEmailWhitelist})""".executeUpdate
    
    //log creation event
    EventsRepo.logEntityChange(institution.getUUID, AuditedEntityType.institution, institution.getUUID, null, institution)
    
    institution
  }  
  
  def byUUID(UUID:String) = 
	sql"select * from Institution where uuid = ${UUID}".first[Institution]
  
  def byName(institutionName:String) = 
	sql"select * from Institution where name = ${institutionName}".first[Institution]
  
  def byHostName(hostName:String) =
      sql"""
      	| select i.* from Institution i 
      	| join InstitutionHostName ihn on i.uuid = ihn.institutionUUID
      	| where ihn.hostName = ${hostName}
	    """.first[Institution]

  def byType(institutionType: InstitutionType) = 
    sql"""
        select * from Institution where institutionType = ${institutionType.toString}
    """.first[Institution]
}