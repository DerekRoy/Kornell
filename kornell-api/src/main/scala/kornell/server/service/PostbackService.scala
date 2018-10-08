package kornell.server.service

import java.net.URLEncoder
import java.nio.charset.Charset
import java.util.logging.{Level, Logger}

import javax.servlet.http.HttpServletRequest
import kornell.core.entity.{PostbackType, RegistrationType}
import kornell.server.jdbc.ConnectionHandler
import kornell.server.jdbc.repository.{CourseClassesRepo, InstitutionRepo, PostbackConfigRepo}
import kornell.server.repository.TOs
import kornell.server.util.DateConverter
import org.apache.http.client.methods.{HttpGet, HttpPost}
import org.apache.http.client.utils.URLEncodedUtils
import org.apache.http.impl.client.HttpClients
import org.apache.http.util.EntityUtils

import scala.collection.JavaConverters._
import scala.xml.XML

object PostbackService {

  /**
   * Example pagseguro transaction XML
   * <transaction>
   * <date>2011-02-10T16:13:41.000-03:00</date>
   * <code>9E884542-81B3-4419-9A75-BCC6FB495EF1</code>
   * <reference>REF1234</reference>
   * <type>1</type>
   * <status>3</status>
   * <paymentMethod>
   * <type>1</type>
   * <code>101</code>
   * </paymentMethod>
   * <grossAmount>49900.00</grossAmount>
   * <discountAmount>0.00</discountAmount>
   * <creditorFees>
   * <intermediationRateAmount>0.40</intermediationRateAmount>
   * <intermediationFeeAmount>1644.80</intermediationFeeAmount>
   * </creditorFees>
   * <netAmount>49900.00</netAmount>
   * <extraAmount>0.00</extraAmount>
   * <installmentCount>1</installmentCount>
   * <itemCount>2</itemCount>
   * <items>
   * <item>
   * <id>aucb1f1e-b36b-11e6-bd35-768f6b6cd8f9</id>
   * <description>Produto PagSeguroI</description>
   * <quantity>1</quantity>
   * <amount>99999.99</amount>
   * </item>
   * <item>
   * <id>0002</id>
   * <description>Produto PagSeguroII</description>
   * <quantity>1</quantity>
   * <amount>99999.98</amount>
   * </item>
   * </items>
   * <sender>
   * <name>José Comprador</name>
   * <email>comprador@uol.com.br</email>
   * <phone>
   * <areaCode>99</areaCode>
   * <number>99999999</number>
   * </phone>
   * </sender>
   * <shipping>
   * <address>
   * <street>Av. PagSeguro</street>
   * <number>9999</number>
   * <complement>99o andar</complement>
   * <district>Jardim Internet</district>
   * <postalCode>99999999</postalCode>
   * <city>Cidade Exemplo</city>
   * <state>SP</state>
   * <country>ATA</country>
   * </address>
   * <type>1</type>
   * <cost>21.50</cost>
   * </shipping>
   * </transaction>
   */

  val logger: Logger = Logger.getLogger("kornell.server.repository.service.PostbackService")

  //Paypal URLs
  //One URL for sandbox transactions, one for live
  val paypal_sandbox_validation_url = "https://www.sandbox.paypal.com/cgi-bin/webscr"
  val paypal_validation_url = "https://www.paypal.com/cgi-bin/webscr"

  //PagSeguro URLs
  //One for sandbox, one for live
  val pag_sandbox_get_trans_url = "https://ws.pagseguro.uol.com.br/v3/transactions/notifications/"
  val pag_get_trans_url = "https://ws.pagseguro.uol.com.br/v3/transactions/notifications/"

  val pag_sandbox_validation = "https://pagseguro.uol.com.br/Security/NPI/Default.aspx?Comando=validar"
  val pag_validation = "https://pagseguro.uol.com.br/Security/NPI/Default.aspx?Comando=validar"

  def paypalPostback(env: String, payload: String): Unit = {
    //Need to prepend a string to message we send back to validate authenticity
    val validation_message = "?cmd=_notify-validate&" + payload
    val hello = new Thread(new Runnable {
      override def run(): Unit = {
        try {
          val current_url = if (env != "live") paypal_sandbox_validation_url else paypal_validation_url
          val postbackType = if (env != "live") PostbackType.PAYPAL_SANDBOX else PostbackType.PAYPAL
          val client = HttpClients.createDefault
          val request = new HttpPost(current_url + validation_message)
          val response = client.execute(request)
          val paypal_confirmation = EntityUtils.toString(response.getEntity)
          response.close()
          client.close()
          if (paypal_confirmation == "VERIFIED") {
            logger.info("POSTBACKLOG: Verified request " + payload)
            createEnrollment(payload, postbackType)
          } else {
            logger.warning("POSTBACKLOG: Invalid request " + payload)
          }
        } catch {
          case e: Throwable => logger.log(Level.SEVERE, "POSTBACKLOG: Exception while processing postback " + payload, e)
        } finally {
          DateConverter.clearTimeZone()
          try {
            // in new thread we get a new connection, no filter so we need to commit/rollback manually
            ConnectionHandler.commit()
          } catch {
            case e: Throwable => {
              ConnectionHandler.rollback()
              logger.log(Level.SEVERE, "POSTBACKLOG: Exception while processing postback " + payload, e)
            }
          }
        }
      }
    })
    hello.start()
  }

  def paypalWCPostback(env: String, institutionUUID: String, request: HttpServletRequest): Unit = {
    val postbackType = if (env == "live") {
      PostbackType.PAYPAL
    } else {
      PostbackType.PAYPAL_SANDBOX
    }
    //validation maybe...
    val postbackConfig = PostbackConfigRepo.getConfig(institutionUUID, postbackType).get
    if (postbackConfig.getContents == request.getParameter("token")) {
      val name = request.getParameter("first_name") + " " + request.getParameter("last_name")
      val email = request.getParameter("payer_email")
      val itemCount = request.getParameter("num_cart_items").toInt
      for (item <- 1 to itemCount) {
        val productCode = request.getParameter("item_number" + item)
        val courseClass = CourseClassesRepo.byEcommerceIdentifier(productCode)
        logger.log(Level.INFO, "POSTBACKLOG: Trying to process postback response for Paypal => " +
          "productId [" + productCode + "] and request [" + prettyParams(request) + "] and " +
          "institution: [" + institutionUUID + "].")
        val enrollmentRequest = TOs.tos.newEnrollmentRequestTO.as
        enrollmentRequest.setFullName(name)
        enrollmentRequest.setUsername(email)
        enrollmentRequest.setCourseClassUUID(courseClass.get.getUUID)
        enrollmentRequest.setInstitutionUUID(institutionUUID)
        enrollmentRequest.setRegistrationType(RegistrationType.email)
        enrollmentRequest.setCancelEnrollment(false)
        RegistrationEnrollmentService.postbackRequestEnrollment(enrollmentRequest, prettyParams(request))
      }
    } else {
      logger.log(Level.INFO, "POSTBACKLOG: Trying to process postback response for Paypal => " +
        "mismatched token [" + request.getParameter("token") + "] and request [" + prettyParams(request) + "] and " +
        "institution: [" + institutionUUID + "].")
    }
  }

  def getPostbackTypeAndUrl(env: String, notificationCode: String): (PostbackType, String) = {
    if (env == "live") {
      if (notificationCode != null) {
        (PostbackType.PAGSEGURO, pag_get_trans_url)
      } else {
        (PostbackType.PAGSEGURO_VALIDATION, pag_validation)
      }
    } else {
      if (notificationCode != null) {
        (PostbackType.PAGSEGURO_SANDBOX, pag_sandbox_get_trans_url)
      } else {
        (PostbackType.PAGSEGURO_VALIDATION_SANDBOX, pag_sandbox_validation)
      }
    }
  }

  def prettyParams(request: HttpServletRequest): String = {
    var paramsString = ""
    for (param <- request.getParameterNames.asScala) {
      paramsString += param + "->" + request.getParameter(param) + ","
    }
    paramsString
  }

  def pagseguroPostback(env: String, institutionUUID: String, pagseguroRequest: HttpServletRequest): Unit = {
    val (postbackType, current_url) = getPostbackTypeAndUrl(env, pagseguroRequest.getParameter("notificationCode"))
    val postbackConfig = PostbackConfigRepo.getConfig(institutionUUID, postbackType).orNull
    if (postbackConfig == null) {
      logger.log(Level.SEVERE, "POSTBACKLOG: Missing postback config for Pagseguro transaction ID [" + prettyParams(pagseguroRequest) + "] and " +
        " institution: [" + institutionUUID + "] and env [" + env + "], could not process")
    } else {
      logger.log(Level.INFO, "POSTBACKLOG: Trying to process postback for Pagseguro => transaction ID [" + prettyParams(pagseguroRequest) + "] and " +
        " institution: [" + institutionUUID + "] and env [" + env + "].")

      val client = HttpClients.createDefault
      if (pagseguroRequest.getParameter("notificationCode") != null) {
        //pagseguro direct post
        val creds_email = postbackConfig.getContents.split("##")(0)
        val creds_token = postbackConfig.getContents.split("##")(1)
        val notificationCode = pagseguroRequest.getParameter("notificationCode")
        val get_url = current_url + notificationCode + "?email=" + creds_email + "&token=" + creds_token

        //do GET to pagseguro API
        val request = new HttpGet(get_url)
        val response = client.execute(request)
        val response_contents = EntityUtils.toString(response.getEntity)
        try {
          processPagseguroResponse(institutionUUID, response_contents)
        } catch {
          case e: Throwable => logger.log(Level.SEVERE, "POSTBACKLOG: Exception while processing postback " + response_contents, e)
        }
      } else {
        //woocommerce form-style POST
        var params = ""
        for (name <- pagseguroRequest.getParameterNames.asScala) {
          // we need to send params exactly as we received them
          params += "&" + name + "=" + URLEncoder.encode(pagseguroRequest.getParameter(name), "windows-1252")
        }
        processPagseguroResponseWooCommerce(institutionUUID, pagseguroRequest)
      }
    }
  }

  def processPagseguroResponseWooCommerce(institutionUUID: String, pagseguroRequest: HttpServletRequest): Unit = {
    val user_email = pagseguroRequest.getParameter("CliEmail")
    val name = pagseguroRequest.getParameter("CliNome")
    val pagseguroIds = pagseguroRequest.getParameter("Referencia").split("/")
    for (pagseguroId <- pagseguroIds) {
      val courseClass = CourseClassesRepo.byEcommerceIdentifier(pagseguroId)
      if (courseClass.isEmpty || courseClass.get.getInstitutionUUID != institutionUUID) {
        logger.log(Level.SEVERE, "POSTBACKLOG: No courseClass found for ecommerceIdentifier [" + pagseguroId + "] and " +
          "institution [" + institutionUUID + "]")
      } else {
        logger.log(Level.INFO, "POSTBACKLOG: Trying to process postback response for Pagseguro => " +
          "ecommerceIdentifier [" + pagseguroId + "] and request [" + prettyParams(pagseguroRequest) + "] and " +
          "institution: [" + institutionUUID + "].")
        val enrollmentRequest = TOs.tos.newEnrollmentRequestTO.as
        enrollmentRequest.setFullName(name)
        enrollmentRequest.setUsername(user_email)
        enrollmentRequest.setCourseClassUUID(courseClass.get.getUUID)
        enrollmentRequest.setInstitutionUUID(institutionUUID)
        enrollmentRequest.setRegistrationType(RegistrationType.email)
        enrollmentRequest.setCancelEnrollment(false)
        RegistrationEnrollmentService.postbackRequestEnrollment(enrollmentRequest, prettyParams(pagseguroRequest))
      }
    }
  }

  def processPagseguroResponse(institutionUUID: String, xmlResponse: String): Unit = {
    val response_xml = XML.loadString(xmlResponse)

    val user_email = (response_xml \\ "sender" \\ "email").text
    val name = (response_xml \\ "sender" \\ "name").text
    val pagseguroIds = response_xml \\ "items" \\ "item" \\ "id"

    for (pagseguroId <- pagseguroIds) {
      val textId = (pagseguroId \\ "id").text
      val courseClass = CourseClassesRepo.byEcommerceIdentifier(textId)
      if (courseClass.isEmpty || courseClass.get.getInstitutionUUID != institutionUUID) {
        logger.log(Level.SEVERE, "POSTBACKLOG: No courseClass found for ecommerceIdentifier [" + textId + "] and " +
          "institution [" + institutionUUID + "], could not process XML " + xmlResponse)
      } else {
        logger.log(Level.INFO, "POSTBACKLOG: Trying to process postback response for Pagseguro => " +
          "ecommerceIdentifier [" + textId + "] and xmlResponse [" + xmlResponse + "] and " +
          "institution: [" + institutionUUID + "].")
        val enrollmentRequest = TOs.tos.newEnrollmentRequestTO.as
        enrollmentRequest.setFullName(name)
        enrollmentRequest.setUsername(user_email)
        enrollmentRequest.setCourseClassUUID(courseClass.get.getUUID)
        enrollmentRequest.setInstitutionUUID(institutionUUID)
        enrollmentRequest.setRegistrationType(RegistrationType.email)
        enrollmentRequest.setCancelEnrollment(false)
        RegistrationEnrollmentService.postbackRequestEnrollment(enrollmentRequest, response_xml.text)
      }
    }
  }

  def createEnrollment(payload: String, postbackType: PostbackType): Unit = {
    val payloadMap = URLEncodedUtils.parse(payload, Charset.forName("utf-8")).asScala.map(t => t.getName -> t.getValue).toMap

    val token = getValueFromPayloadMap(payloadMap, "custom").get
    val ecommerceIdentifier = getValueFromPayloadMap(payloadMap, "item_number").get
    val courseClass = CourseClassesRepo.byEcommerceIdentifier(ecommerceIdentifier).get
    val institutionUUID = courseClass.getInstitutionUUID
    DateConverter.setTimeZone(new InstitutionRepo(institutionUUID).get.getTimeZone)
    val postbackConfig = PostbackConfigRepo.checkConfig(institutionUUID, postbackType, token).orNull

    if (postbackConfig != null) {
      val enrollmentRequest = TOs.tos.newEnrollmentRequestTO.as
      val firstName = getValueFromPayloadMap(payloadMap, "first_name")
      val lastName = getValueFromPayloadMap(payloadMap, "last_name")
      if (firstName.isDefined) {
        if (lastName.isDefined) {
          enrollmentRequest.setFullName(firstName.get + " " + lastName.get)
        } else {
          enrollmentRequest.setFullName(firstName.get)
        }
      }
      enrollmentRequest.setUsername(getValueFromPayloadMap(payloadMap, "payer_email").get)
      enrollmentRequest.setCourseClassUUID(courseClass.getUUID)
      enrollmentRequest.setInstitutionUUID(institutionUUID)
      enrollmentRequest.setRegistrationType(RegistrationType.email)
      enrollmentRequest.setCancelEnrollment(false)
      RegistrationEnrollmentService.postbackRequestEnrollment(enrollmentRequest, payload)
    } else {
      logger.severe("POSTBACKLOG: Mismatched token for institution " + payload)
    }
  }

  def getValueFromPayloadMap(payloadMap: Map[String, String], key: String): Option[String] = {
    try {
      Some(payloadMap(key))
    } catch {
      case _: Throwable => None
    }
  }
}
