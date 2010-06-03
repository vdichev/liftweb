/*
 * Copyright 2008-2010 WorldWide Conferencing, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.liftweb {
package http {
package testing {

import _root_.net.liftweb.util.Helpers._
import _root_.net.liftweb.util._
import _root_.net.liftweb.json._
import JsonAST._
import JsonDSL._
import _root_.net.liftweb.common._
import _root_.scala.xml._
import _root_.java.util.{Map => JavaMap, Set => JavaSet, Iterator => JavaIterator, List => JavaList}
import _root_.java.util.regex.Pattern
import _root_.java.io.IOException
import _root_.org.apache.commons.httpclient._
import _root_.org.apache.commons.httpclient.cookie._
import methods._
import _root_.java.io.OutputStream
import _root_.org.apache.commons.httpclient.auth.AuthScope

trait ToResponse {
  self: BaseGetPoster =>
  implicit def responseCapture(fullUrl: String,
                                       httpClient: HttpClient,
                                       getter: HttpMethodBase): Response = {
    
    val ret = try {
      (baseUrl + fullUrl, httpClient.executeMethod(getter)) match {
        case (server, responseCode) =>
          val respHeaders = slurpApacheHeaders(getter.getResponseHeaders)

          new HttpResponse(baseUrl,
            responseCode, getter.getStatusText,
            respHeaders, getter.getResponseBody,
            httpClient)
      }
    } catch {
      case e: IOException => new CompleteFailure(baseUrl + fullUrl, Full(e))
    } finally {
      getter.releaseConnection
    }

    ret
  }                                         
}

trait ToBoxHttpResponse {
  self: BaseGetPoster =>

  implicit def responseCapture(fullUrl: String,
                               httpClient: HttpClient,
                               getter: HttpMethodBase): 
  Box[HttpResponse] = {
    
    val ret = try {
      (baseUrl + fullUrl, httpClient.executeMethod(getter)) match {
        case (server, responseCode) =>
          val respHeaders = slurpApacheHeaders(getter.getResponseHeaders)
        
        Full(new HttpResponse(baseUrl,
                              responseCode, getter.getStatusText,
                              respHeaders, getter.getResponseBody,
                              httpClient))
      }
    } catch {
      case e: IOException => Failure(baseUrl + fullUrl, Full(e), Empty)
    } finally {
      getter.releaseConnection
    }

    ret
  }                                         

}

trait GetPoster extends BaseGetPoster with ToResponse

/**
 * A trait that wraps the Apache Commons HTTP client and supports GET and POST
 */
trait BaseGetPoster {
  /**
   * The base URL for all requests
   */
  def baseUrl: String

  protected def slurpApacheHeaders(in: Array[Header]):
  Map[String, List[String]] = {
    val headerSet: List[(String, String)] = for (e <- in.toList) yield (e.getName -> e.getValue)

    headerSet.foldLeft[Map[String, List[String]]](Map.empty)((acc, e) =>
        acc + (e._1 -> (e._2 :: acc.getOrElse(e._1, Nil))))
  }


  /**
   * Perform an HTTP GET
   *
   * @param url - the URL to append to the baseUrl
   * @param headers - any additional headers to include with the request
   * @param faux_params - the request parameters to include with the request
   */
  def get[T](url: String, httpClient: HttpClient,
          headers: List[(String, String)],
          faux_params: (String, Any)*)
  (implicit capture: (String, HttpClient, HttpMethodBase) => T): T // Response
  = {
    val params = faux_params.toList.map(x => (x._1, x._2.toString))
    val fullUrl = url + (params.map(v => urlEncode(v._1) + "=" + urlEncode(v._2)).mkString("&") match {case s if s.length == 0 => ""; case s => "?" + s})
    val getter = new GetMethod(baseUrl + fullUrl)
    getter.getParams().setCookiePolicy(CookiePolicy.RFC_2965)
    for ((name, value) <- headers) getter.setRequestHeader(name, value)

    capture(fullUrl, httpClient, getter)
  }

  /**
   * Perform an HTTP DELETE
   *
   * @param url - the URL to append to the baseUrl
   * @param headers - any additional headers to include with the request
   * @param faux_params - the request parameters to include with the request
   */
  def delete[T](url: String, httpClient: HttpClient,
                headers: List[(String, String)],
                faux_params: (String, Any)*)
  (implicit capture: (String, HttpClient, HttpMethodBase) => T): T 
  = {
    val params = faux_params.toList.map(x => (x._1, x._2.toString))
    val fullUrl = url + (params.map(v => urlEncode(v._1) + "=" + urlEncode(v._2)).mkString("&") match {case s if s.length == 0 => ""; case s => "?" + s})
    val getter = new DeleteMethod(baseUrl + fullUrl)
    getter.getParams().setCookiePolicy(CookiePolicy.RFC_2965)
    for ((name, value) <- headers) getter.setRequestHeader(name, value)
    
    capture(fullUrl, httpClient, getter)
  }

  /**
   * Perform an HTTP POST
   *
   * @param url - the URL to append to the baseUrl
   * @param headers - any additional headers to include with the request
   * @param faux_params - the request parameters to include with the request
   */
  def post[T](url: String, httpClient: HttpClient,
           headers: List[(String, String)],
           faux_params: (String, Any)*)
  (implicit capture: (String, HttpClient, HttpMethodBase) => T): T 
  = {
    val params = faux_params.toList.map(x => (x._1, x._2.toString))
    val poster = new PostMethod(baseUrl + url)
    poster.getParams().setCookiePolicy(CookiePolicy.RFC_2965)
    for ((name, value) <- headers) poster.setRequestHeader(name, value)
    for ((name, value) <- params) poster.setParameter(name, value)

    capture(url, httpClient, poster)
  }

  implicit def xmlToRequestEntity(body: NodeSeq): RequestEntity = 
    new RequestEntity {
      val bytes = body.toString.getBytes("UTF-8")
      
      def getContentLength() = bytes.length
      
      def getContentType() = "text/xml; charset=utf-8"
      
      def isRepeatable() = true
      
      def writeRequest(out: OutputStream) {
        out.write(bytes)
      }
    }

  implicit def jsonToRequestEntity(body: JValue): RequestEntity = 
    new RequestEntity {
      val bytes = compact(render(body)).toString.getBytes("UTF-8")
      
      def getContentLength() = bytes.length
      
      def getContentType() = "application/json"
      
      def isRepeatable() = true
      
      def writeRequest(out: OutputStream) {
        out.write(bytes)
      }
    }

  /**
   * Perform an HTTP POST with an XML body
   *
   * @param url - the URL to append to the baseUrl
   * @param headers - any additional headers to include with the request
   * @param body - the xml to post
   */
  def post[T, RT](url: String, httpClient: HttpClient,
                  headers: List[(String, String)],
                  body: RT)
  (implicit capture: (String, HttpClient, HttpMethodBase) => T,
 bodyToRequestEntity: RT => RequestEntity): T 
  = {
    val poster = new PostMethod(baseUrl + url)
    poster.getParams().setCookiePolicy(CookiePolicy.RFC_2965)
    for ((name, value) <- headers) poster.setRequestHeader(name, value)
    poster.setRequestEntity(bodyToRequestEntity(body))
    
    capture(url, httpClient, poster)
  }

  /**
   * Perform an HTTP POST with a pile of bytes in the body
   *
   * @param url - the URL to append to the baseUrl
   * @param headers - any additional headers to include with the request
   * @param body - the pile of bytes to POST to the target server
   * @param contentType - the content type of the pile of bytes
   */
  def post[T](url: String, httpClient: HttpClient,
              headers: List[(String, String)],
              body: Array[Byte],
              contentType: String)
  (implicit capture: (String, HttpClient, HttpMethodBase) => T): T 
  = {
    val poster = new PostMethod(baseUrl + url)
    poster.getParams().setCookiePolicy(CookiePolicy.RFC_2965)
    for ((name, value) <- headers) poster.setRequestHeader(name, value)
    poster.setRequestEntity(new RequestEntity {
      private val bytes = body
      
      def getContentLength() = bytes.length
      
      def getContentType() = contentType

      def isRepeatable() = true
      
      def writeRequest(out: OutputStream) {
        out.write(bytes)
      }
    })
    capture(url, httpClient, poster)
  }

  /**
   * Perform an HTTP PUT
   *
   * @param url - the URL to append to the baseUrl
   * @param headers - any additional headers to include with the request
   */
  def put[T](url: String, httpClient: HttpClient,
          headers: List[(String, String)])
  (implicit capture: (String, HttpClient, HttpMethodBase) => T): T 
  = {
    val poster = new PutMethod(baseUrl + url)
    poster.getParams().setCookiePolicy(CookiePolicy.RFC_2965)
    for ((name, value) <- headers) poster.setRequestHeader(name, value)

    capture(url, httpClient, poster)
  }

  /**
   * Perform an HTTP PUT with an XML body
   *
   * @param url - the URL to append to the baseUrl
   * @param headers - any additional headers to include with the request
   * @param body - the xml to post
   */
  def put[T, RT](url: String, httpClient: HttpClient,
          headers: List[(String, String)],
          body: RT)
    (implicit capture: (String, HttpClient, HttpMethodBase) => T,
     bodyToRequestEntity: RT => RequestEntity): T 
    = {
      val poster = new PutMethod(baseUrl + url)
      poster.getParams().setCookiePolicy(CookiePolicy.RFC_2965)
      for ((name, value) <- headers) poster.setRequestHeader(name, value)
      poster.setRequestEntity(bodyToRequestEntity(body))
      
      capture(url, httpClient, poster)
    }

  /**
   * Perform an HTTP PUT with a pile of bytes in the body
   *
   * @param url - the URL to append to the baseUrl
   * @param headers - any additional headers to include with the request
   * @param body - the pile of bytes to POST to the target server
   * @param contentType - the content type of the pile of bytes
   */
  def put[T](url: String, httpClient: HttpClient,
          headers: List[(String, String)],
          body: Array[Byte],
          contentType: String)
  (implicit capture: (String, HttpClient, HttpMethodBase) => T): T 
  = {
    val poster = new PutMethod(baseUrl + url)
    poster.getParams().setCookiePolicy(CookiePolicy.RFC_2965)
    for ((name, value) <- headers) poster.setRequestHeader(name, value)
    poster.setRequestEntity(new RequestEntity {
      private val bytes = body
      
      def getContentLength() = bytes.length
      
      def getContentType() = contentType
      
      def isRepeatable() = true
      
      def writeRequest(out: OutputStream) {
        out.write(bytes)
      }
    })

    capture(url, httpClient, poster)
  }
}

/**
 * A trait for reporting failures
 */
trait ReportFailure {
  def fail(msg: String): Nothing
}

trait GetPosterHelper {
  self: BaseGetPoster =>

  /**
   * Create the HTTP client for a new get/post request
   */
  def theHttpClient: HttpClient


  /**
   * Perform an HTTP GET with a newly minted httpClient
   *
   * @param url the URL to make the request on
   * @param params the parameters to pass
   */
  def get[T](url: String, params: (String, Any)*)
  (implicit capture: (String, HttpClient, HttpMethodBase) => T): T =
    get(url, theHttpClient, Nil, params: _*)(capture)

  /**
   * Perform an HTTP DELETE with a newly minted httpClient
   *
   * @param url the URL to make the request on
   * @param params the parameters to pass
   */
  def delete[T](url: String, params: (String, Any)*)
  (implicit capture: (String, HttpClient, HttpMethodBase) => T): T =
    delete(url, theHttpClient, Nil, params: _*)(capture)

  /**
   * Perform an HTTP POST with a newly minted httpClient
   *
   * @param url the URL to make the request on
   * @param params the parameters to pass
   */
  def post[T](url: String, params: (String, Any)*)
  (implicit capture: (String, HttpClient, HttpMethodBase) => T): T =
    post(url, theHttpClient, Nil, params: _*)(capture)

  /**
   * Perform an HTTP POST with a newly minted httpClient
   *
   * @param url the URL to make the request on
   * @param xml the XML to POST to the server
   */
  def post[T, RT](url: String, xml: RT)
  (implicit capture: (String, HttpClient, HttpMethodBase) => T,
   bodyToRequestEntity: RT => RequestEntity): T =
    post(url, theHttpClient, Nil, xml)(capture, bodyToRequestEntity)

  /**
   * Perform an HTTP POST with a newly minted httpClient
   *
   * @param url the URL to make the request on
   * @param body the bytes to POST to the server
   * @param contentType the content type of the message
   */
  def post[T](url: String, body: Array[Byte], contentType: String)
  (implicit capture: (String, HttpClient, HttpMethodBase) => T): T =
    post(url, theHttpClient, Nil, body, contentType)(capture)

  /**
   * Perform an HTTP PUT with a newly minted httpClient
   *
   * @param url the URL to make the request on
   * @param xml the XML to PUT to the server
   */
  def put[T, RT](url: String, xml: RT)
  (implicit capture: (String, HttpClient, HttpMethodBase) => T,
   bodyToRequestEntity: RT => RequestEntity): T =
    put(url, theHttpClient, Nil, xml)(capture, bodyToRequestEntity)

  /**
   * Perform an HTTP POST with a newly minted httpClient
   *
   * @param url the URL to make the request on
   * @param body the bytes to POST to the server
   * @param contentType the content type of the message
   */
  def put[T](url: String, body: Array[Byte], contentType: String)
  (implicit capture: (String, HttpClient, HttpMethodBase) => T): T =
    put(url, theHttpClient, Nil, body, contentType)(capture)

}

/**
 * Mix this trait into your test so you can make HTTP requests on a target
 */
trait TestKit extends ClientBuilder with GetPoster with GetPosterHelper {
  /**
   * The base URL for all GET and POST requests
   */
  def baseUrl: String

  class TestHandler(res: Response) {
    def then(f: Response => Response): Response = f(res)

    def also(f: Response => Any): Response = {f(res); res}
  }
  implicit def reqToHander(in: Response): TestHandler = new TestHandler(in)
}

trait ClientBuilder {
  /**
   * Create the HTTP client for a new get/post request
   */
  def theHttpClient: HttpClient = buildNoAuthClient


  /**
   * Create a new HTTP client that does not do any form of AUTH
   */
  def buildNoAuthClient =
    new HttpClient(new SimpleHttpConnectionManager(false))

  /**
   * Create a new HTTP client that does BASIC AUTH with username/pwd
   */
  def buildBasicAuthClient(name: String, pwd: String) = {
    val ret = new HttpClient(new SimpleHttpConnectionManager(false))
    val defaultcreds = new UsernamePasswordCredentials(name, pwd)
    ret.getState().setCredentials(AuthScope.ANY, defaultcreds)

    ret
  }

}

/**
 * Mix this trait into your test so you can make HTTP requests on a target
 */
trait RequestKit extends ClientBuilder with BaseGetPoster with GetPosterHelper with ToBoxHttpResponse {
  /**
   * The base URL for all GET and POST requests
   */
  def baseUrl: String
}


/**
 * A legacy test framework
 */
trait TestFramework extends TestKit {
  // def runner: TestRunner
  def tests: List[Item]

  def buildRunner: TestRunner

  /**
   * Create the base HTTP client
   */
  override def theHttpClient: HttpClient = defaultHttpClient

  lazy val defaultHttpClient = buildNoAuthClient

  // protected lazy val httpClient = new HttpClient(new MultiThreadedHttpConnectionManager)

  def fork(cnt: Int)(f: Int => Any) {
    val threads = for (t <- (1 to cnt).toList) yield {
      val th = new Thread(new Runnable {def run {f(t)}})
      th.start
      th
    }

    def waitAll(in: List[Thread]) {
      in match {
        case Nil =>
        case x :: xs => x.join; waitAll(xs)
      }
    }

    waitAll(threads)
  }


}

object TestHelpers {
  /**
   * Get the function name given a particular comet actor name
   *
   * @param cometName the name (default prefix) for the comet actor
   * @param body the body of the response
   *
   * @return the name of the JSON function associated with the Comet actor
   */
  def jsonFuncForCometName(cometName: String, body: String): Box[String] = {
    val p = Pattern.compile("""JSON Func """ + cometName + """ \$\$ ([Ff][^ ]*)""")
    val m = p.matcher(body)
    if (m.find) Full(m.group(1))
    else Empty
  }


  /**
   * Given an HTML page, find the list of "lift_toWatch" names and values
   * These can be fed back into a comet request
   *
   * @param body the page body returned from an HTTP request
   *
   * @return a list of the "to watch" tokens and the last update tokens
   */
  def toWatchFromPage(body: String): List[(String, String)] = {
    val p = Pattern.compile("""lift_toWatch[ ]*\=[ ]*\{([^}]*)\}""")
    val rp = new REMatcher(body, p)
    val p2 = Pattern.compile("""'([^']*)'\: ([0-9]*)""")

    for (it <- rp.capture;
         val _ = println("Captured: " + it);
         val _ = println("Does match: " + p2.matcher(it).find);
         val q = new REMatcher(it, p2);
         em <- q.eachFound) yield (em(1), em(2))
  }

  /**
   * Given the body of a Comet response, parse for updates "lift_toWatch" values
   * and update the current sequence to reflect any updated values
   *
   * @param old the old toWatch sequence
   * @param body the body of the comet response
   *
   * @return the updated sequences
   */
  def toWatchUpdates(old: Seq[(String, String)], body: String): Seq[(String, String)] = {
    val p = Pattern.compile("""lift_toWatch\[\'([^\']*)\'] \= \'([0-9]*)""")
    val re = new REMatcher(body, p)
    val np = re.eachFound.foldLeft(Map(old: _*))((a, b) => a + ((b(1), b(2))))
    np.elements.toList
  }


  def getCookie(headers: List[(String, String)],
                respHeaders: Map[String, List[String]]): Box[String]
  =
    {
      val ret = (headers.filter {case ("Cookie", _) => true; case _ => false}.
          map(_._2) :::
          respHeaders.get("Set-Cookie").toList.flatMap(x => x)) match {
        case Nil => Empty
        case "" :: Nil => Empty
        case "" :: xs => Full(xs.mkString(","))
        case xs => Full(xs.mkString(","))
      }

      ret
    }

  type CRK = JavaList[String]

  implicit def jitToIt[T](in: JavaIterator[T]): Iterator[T] = new Iterator[T] {
    def next: T = in.next

    def hasNext = in.hasNext
  }

  private def snurpHeaders(in: JavaMap[String, CRK]): Map[String, List[String]] = {
    def morePulling(e: JavaMap.Entry[String, CRK]): (String, List[String]) = {
      e.getValue match {
        case null => (e.getKey, Nil)
        case a => (e.getKey, a.iterator.toList)
      }
    }

    Map(in.entrySet.iterator.toList.filter(e => (e ne null) && (e.getKey != null)).map(e => morePulling(e)): _*)
  }
}

/**
 * A response returned from an HTTP request.  Responses can be chained and tested in a for comprehension:
 * <pre>
 *
 * implicit val
 *
 * for  {
 *    login &lt;- post("/api/login", "user" -> "me", "pwd" -> "me") !@ "Failed to log in"
 *    info &lt;- login.get("/api/info") if info.bodyAsString must_== "My Info"
 *    moreInfo &lt;- info.post("/api/moreInfo", &lt;info&gt;Some Info&lt;/info&gt;)
 * } moreInfoheaders("X-MyInfo") must_== List("hello", "goodbye")
 * </pre>
 */
trait Response {
  /**
   * The XML for the body
   */
  def xml: Elem

  /**
   * The response headers
   */
  def headers: Map[String, List[String]]

  /**
   * Test the response as a 200.  If the response is not a 200, call the errorFunc with the msg
   *
   * @param msg the String to report as an error
   * @param errorFunc the error reporting thing.
   */
  def !@(msg: => String)(implicit errorFunc: ReportFailure): Response

  /**
   * Test that the server gave a response.  If the server failed to respond, call the errorFunc with the msg
   *
   * @param msg the String to report as an error
   * @param errorFunc the error reporting thing.
   */
  def !(msg: => String)(implicit errorFunc: ReportFailure): Response

  /**
   * Test that the server gave a particular response code.  If the response is not a 200, call the errorFunc with the msg
   *
   * @param msg the String to report as an error
   * @param errorFunc the error reporting thing.
   */
  def !(code: Int, msg: => String)(implicit errorFunc: ReportFailure): Response

  /**
   * the Response has a foreach method for chaining in a for comprehension
   */
  def foreach(f: HttpResponse => Unit): Unit

  /**
   * the Response has a filter method for chaining in a for comprehension.  Note that the filter method does *NOT* have
   * to return a Boolean, any expression (e.g., an assertion)
   */
  def filter(f: HttpResponse => Unit): HttpResponse
}

/**
 * The response to an HTTP request, as long as the server responds with *SOMETHING*
 *
 */
class HttpResponse(override val baseUrl: String,
                   val code: Int, val msg: String,
                   override val headers: Map[String, List[String]],
                   val body: Array[Byte],
                   val theHttpClient: HttpClient) extends
  Response with BaseGetPoster with GetPosterHelper
{
  private object FindElem {
    def unapply(in: NodeSeq): Option[Elem] = in match {
      case e: Elem => Some(e)
      case d: Document => unapply(d.docElem)
      case g: Group => unapply(g.nodes)
      case n: Text => None
      case sn: SpecialNode => None
      case n: NodeSeq => n.flatMap(unapply).firstOption
    }
  }

  /**
   * Get the body of the response as XML
   */
  override lazy val xml: Elem = {
    PCDataXmlParser(new _root_.java.io.ByteArrayInputStream(body)) match {
      case Full(FindElem(e)) => e
    }
  }

  /**
   * The content type header of the response
   */
  lazy val contentType: String = headers.filter {case (name, value) => name equalsIgnoreCase "content-type"}.toList.firstOption.map(_._2.head) getOrElse ""

  /**
   * The response body as a UTF-8 encoded String
   */
  lazy val bodyAsString = new String(body, "UTF-8")


  def !@(msg: => String)(implicit errorFunc: ReportFailure): Response =
    if (code == 200) this else {errorFunc.fail(msg)}

  def !(msg: => String)(implicit errorFunc: ReportFailure): Response = this

  def !(code: Int, msg: => String)(implicit errorFunc: ReportFailure): Response =
    if (this.code != code) errorFunc.fail(msg) else this

  def foreach(f: HttpResponse => Unit): Unit = f(this)

  def filter(f: HttpResponse => Unit): HttpResponse = {
    f(this)
    this
  }
}

class CompleteFailure(val serverName: String, val exception: Box[Throwable]) extends Response {
  override def toString = serverName + (exception.map(e => " Exception: " + e.getMessage) openOr "")

  def headers: Map[String, List[String]] = throw (exception openOr new java.io.IOException("HTTP Failure"))

  def xml: Elem = throw (exception openOr new java.io.IOException("HTTP Failure"))

  def !@(msg: => String)(implicit errorFunc: ReportFailure): Response = errorFunc.fail(msg)

  def !(msg: => String)(implicit errorFunc: ReportFailure): Response = errorFunc.fail(msg)

  def !(code: Int, msg: => String)(implicit errorFunc: ReportFailure): Response = errorFunc.fail(msg)

  def foreach(f: HttpResponse => Unit): Unit = throw (exception openOr new java.io.IOException("HTTP Failure"))

  def filter(f: HttpResponse => Unit): HttpResponse = throw (exception openOr new java.io.IOException("HTTP Failure"))
}

}
}
}
