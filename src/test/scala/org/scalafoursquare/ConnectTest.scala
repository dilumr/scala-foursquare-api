package org.scalafoursquare

import org.scalafoursquare.call.{AuthApp, UserlessApp, HttpCaller, Request}
import org.scalafoursquare.response.{Meta}
import org.junit.Test
import org.specs.SpecsMatchers
import net.liftweb.common.Empty
import net.liftweb.util.Props

class ConnectTest extends SpecsMatchers {

  val USER_TOKEN = Props.get("access.token.user").open_!
  val CONSUMER_KEY = Props.get("consumer.key").open_!
  val CONSUMER_SECRET = Props.get("consumer.secret").open_!
  val TEST_URL = Props.get("foursquare.test.url").open_!
  val API_VERSION = Props.get("api.version").open_!

  @Test
  def errorHandling() {
    val mockCaller = TestCaller
    val mockApp = new UserlessApp(mockCaller)

    val failVenue = mockApp.venueDetail("missingId").get

    failVenue.meta.code must_== 400
    failVenue.meta.errorType must_== Some("param_error")
    failVenue.response.isDefined must_== false

    case class ErrorTest()

    val failEndpoint = new Request[ErrorTest](mockApp, "/bad/endpoint/blargh").get
    failEndpoint.meta must_== Meta(404, Some("other"), Some("Endpoint not found"))
  }

  @Test
  def venueDetail() {
    val mockCaller = TestCaller
    val mockApp = new UserlessApp(mockCaller)

    val mockVenue = mockApp.venueDetail("someVenueId").get

    mockVenue.meta must_== Meta(200, None, None)
    mockVenue.notifications must_== None
    mockVenue.response.get.venue.name must_== "Fake Venue"
    mockVenue.response.get.venue.location.crossStreet must_== Some("At Fake Street")
    mockVenue.response.get.venue.mayor.count must_== 15
    mockVenue.response.get.venue.tags(0) must_== "a tag"

    // This one actually makes a web call!

    val caller = new HttpCaller(CONSUMER_KEY, CONSUMER_SECRET, TEST_URL, API_VERSION)
    val app = new UserlessApp(caller)

    val venue = app.venueDetail("1234").get
    println(venue.toString)
  }

  @Test
  def userDetail() {
    val mockCaller = TestCaller
    val mockUserApp = new AuthApp(mockCaller, "Just Testing!")

    val mockSelf = mockUserApp.self.get
    val mockById = mockUserApp.userDetail("someUserId").get

    println(mockSelf.toString)
    println(mockById.toString)
    mockSelf must_== mockById
    mockSelf.meta must_== Meta(200, None, None)
    mockSelf.notifications must_== None
    mockSelf.response.get.user.firstName must_== "Fake"
    mockSelf.response.get.user.mayorships.count must_== 20
    mockSelf.response.get.user.checkins.items(0).venue.get.name must_== "Fake Venue"
    mockSelf.response.get.user.checkins.items(0).venue.get.location.lat must_== Some(40.0)
    mockSelf.response.get.user.checkins.items(0).venue.get.location.lng must_== Some(-73.5)
    mockSelf.response.get.user.following.get.count must_== 70
    mockSelf.response.get.user.followers.isDefined must_== false
    mockSelf.response.get.user.scores.checkinsCount must_== 30

    // These actually make a web call!

    val caller = HttpCaller(CONSUMER_KEY, CONSUMER_SECRET, TEST_URL, API_VERSION)
    val userApp = new AuthApp(caller, USER_TOKEN)

    val self = userApp.self.get
    println(self.toString)

    val mtv = userApp.userDetail(660771.toString).get
    println(mtv.toString)
  }

  @Test
  def venueCategories() {
    val mockCaller = TestCaller
    val mockApp = new UserlessApp(mockCaller)

    val mockVenueCategories = mockApp.venueCategories.get
    mockVenueCategories.meta must_== Meta(200, None, None)
    mockVenueCategories.notifications must_== None
    mockVenueCategories.response.get.categories.length must_== 1
    mockVenueCategories.response.get.categories(0).name must_== "Fake Category"
    mockVenueCategories.response.get.categories(0).pluralName must_== "Fake Categories"
    mockVenueCategories.response.get.categories(0).id must_== "fakeId"
    mockVenueCategories.response.get.categories(0).icon must_== "noIcon"
    mockVenueCategories.response.get.categories(0).categories.length must_== 0

    // This one actually makes a web call!
    
    val caller = HttpCaller(CONSUMER_KEY, CONSUMER_SECRET, TEST_URL, API_VERSION)
    val app = new UserlessApp(caller)

    val venueCategories = app.venueCategories.get

    println(venueCategories.toString)
  }

  @Test
  def multiNoAuth() {
    val mockCaller = TestCaller
    val mockApp = new UserlessApp(mockCaller)

    val mockVenueReq = mockApp.venueDetail("someVenueId")
    val mockCategoryReq = mockApp.venueCategories
    val mockMulti = mockApp.multi(mockVenueReq, mockCategoryReq).get
    val mockVenue = mockMulti.responses._1.get
    val mockVenueCategories = mockMulti.responses._2.get

    mockMulti.responses._3 must_== None
    mockMulti.responses._4 must_== None
    mockMulti.responses._5 must_== None

    mockVenue.meta must_== Meta(200, None, None)
    mockVenue.notifications must_== None
    mockVenue.response.get.venue.name must_== "Fake Venue"
    mockVenue.response.get.venue.location.crossStreet must_== Some("At Fake Street")
    mockVenue.response.get.venue.mayor.count must_== 15
    mockVenue.response.get.venue.tags(0) must_== "a tag"

    mockVenueCategories.meta must_== Meta(200, None, None)
    mockVenueCategories.notifications must_== None
    mockVenueCategories.response.get.categories.length must_== 1
    mockVenueCategories.response.get.categories(0).name must_== "Fake Category"
    mockVenueCategories.response.get.categories(0).pluralName must_== "Fake Categories"
    mockVenueCategories.response.get.categories(0).id must_== "fakeId"
    mockVenueCategories.response.get.categories(0).icon must_== "noIcon"
    mockVenueCategories.response.get.categories(0).categories.length must_== 0

    // This one actually makes a web call!

    val caller = new HttpCaller(CONSUMER_KEY, CONSUMER_SECRET, TEST_URL, API_VERSION)
    val app = new UserlessApp(caller)

    val venue = app.venueDetail("1234")
    val venueCategories = app.venueCategories

    val multi = app.multi(venue, venueCategories).get

    println(multi)

    (multi.responses._1.get == venue.get) must_== true
    (multi.responses._2.get == venueCategories.get) must_== true
  }

  @Test
  def multiAuthed() {
    val mockCaller = TestCaller
    val mockUserApp = new AuthApp(mockCaller, "Just Testing!")

    val mockSelfReq = mockUserApp.self
    val mockByIdReq = mockUserApp.userDetail("someUserId")
    val mockMulti = mockUserApp.multi(mockSelfReq, mockByIdReq).get
    val mockSelf = mockMulti.responses._1.get
    val mockById = mockMulti.responses._2.get

    mockMulti.responses._3 must_== None
    mockMulti.responses._4 must_== None
    mockMulti.responses._5 must_== None

    println(mockSelf.toString)
    println(mockById.toString)
    mockSelf must_== mockById
    mockSelf.meta must_== Meta(200, None, None)
    mockSelf.notifications must_== None
    mockSelf.response.get.user.firstName must_== "Fake"
    mockSelf.response.get.user.mayorships.count must_== 20
    mockSelf.response.get.user.checkins.items(0).venue.get.name must_== "Fake Venue"
    mockSelf.response.get.user.checkins.items(0).venue.get.location.lat must_== Some(40.0)
    mockSelf.response.get.user.checkins.items(0).venue.get.location.lng must_== Some(-73.5)
    mockSelf.response.get.user.following.get.count must_== 70
    mockSelf.response.get.user.followers.isDefined must_== false
    mockSelf.response.get.user.scores.checkinsCount must_== 30

    // These actually make a web call!

    val caller = HttpCaller(CONSUMER_KEY, CONSUMER_SECRET, TEST_URL, API_VERSION)
    val userApp = new AuthApp(caller, USER_TOKEN)

    val self = userApp.self
    val mtv = userApp.userDetail(660771.toString)

    val multi = userApp.multi(self, mtv).get

    println(multi)

    (multi.responses._1.get == self.get) must_== true
    (multi.responses._2.get == mtv.get) must_== true
  }
}