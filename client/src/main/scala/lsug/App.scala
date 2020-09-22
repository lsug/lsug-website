package lsug

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.extra.router._
import Function.const

import cats.effect._
import org.scalajs.dom.document
import java.util.Locale
import java.time.LocalDateTime
import java.time.Clock

object App extends IOApp {

  import ui.Page
  import lsug.{ui => lui}
  import lsug.{protocol => P}

  val routerConfig = RouterConfigDsl[Page].buildConfig { dsl =>
    import dsl._

    def page(node: LocalDateTime => VdomNode): VdomElement = {
      val now = LocalDateTime.now(Clock.systemUTC())
      React.Fragment(
        lui.NavBar.NavBar(),
        node(now),
        lui.Footer.Footer(now.toLocalDate)
      )
    }

    def homeRoute =
      staticRoute(root, Page.Home) ~> renderR { ctl =>
        page(now => lui.Home.Home(
            lui.Home.Props(
              ctl,
              now
            )))
      }

    def sponsorsRoute =
      staticRoute(root / "sponsors", Page.Sponsors) ~> renderR { _ =>
        page(const(lui.sponsors.Sponsors()))
      }

    def aboutRoute =
      staticRoute(root / "about", Page.About) ~> renderR { _ =>
        page(const(lui.about.About()))
      }

    def eventRoute =
      dynamicRouteCT(
        root / "events" / (string("[0-9\\-]+") / int).caseClass[Page.Event]
      ) ~> dynRenderR {
        case (Page.Event(meetupId, eventId), ctl) =>
          page(const(
            lui.event.EventPage.EventPage(
              (
                ctl.narrow,
                new P.Meetup.Id(meetupId),
                new P.Meetup.Event.Id(eventId)
              )
            )))
      }

    def meetupRoute =
      dynamicRouteCT(
        root / "meetups" / (string("[0-9\\-]+")).caseClass[Page.Meetup]
      ) ~> dynRenderR {
        case (Page.Meetup(meetupId), ctl) =>
          page(const(
            lui.meetup.Meetup.Meetup(
              (
                ctl.narrow,
                new P.Meetup.Id(meetupId)
              )
            )))
      }

    (homeRoute | eventRoute | meetupRoute | sponsorsRoute | aboutRoute).notFound(
      redirectToPage(Page.Home)(SetRouteVia.HistoryReplace)
    )
  }

  override def run(args: List[String]): IO[ExitCode] = {
    IO.delay {
        Locale.setDefault(Locale.ENGLISH)
        val router = Router(BaseUrl.fromWindowOrigin, routerConfig)
        val div = document.createElement("div")
        document.body.appendChild(div)
        router().renderIntoDOM(div)
      }
      .map(_ => ExitCode.Success)
  }
}
