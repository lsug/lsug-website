package lsug

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.extra.router._

import cats.effect._
import org.scalajs.dom.document
import java.util.Locale
import java.time.LocalDateTime
import java.time.Clock

object App extends IOApp {

  import ui.Page
  import lsug.{ui => lui}

  val routerConfig = RouterConfigDsl[Page].buildConfig { dsl =>
    import dsl._

    def homeRoute =
      staticRoute(root, Page.Home) ~> renderR{ ctl =>
        val now = LocalDateTime.now(Clock.systemUTC())
        React.Fragment(
          lui.common.NavBar(),
          lui.home.Home(
            lui.home.Props(
              ctl,
              now
            )
          ),
          lui.common.Footer(now.toLocalDate)
        )
      }

    def sponsorsRoute =
      staticRoute(root / "sponsors", Page.Sponsors) ~> renderR{ ctl =>
        val now = LocalDateTime.now(Clock.systemUTC())
        React.Fragment(
          lui.common.NavBar(),
          lui.sponsors.Sponsors(),
          lui.common.Footer(now.toLocalDate)
        )
      }

    def eventRoute =
      dynamicRouteCT(
        root / "events" / string("[0-9\\-]+").caseClass[Page.Event]
      ) ~> dynRenderR {
        case (Page.Event(ev), ctl) =>
          val now = LocalDateTime.now(Clock.systemUTC())
          React.Fragment(
            lui.common.NavBar(),
            lui.event.Event((ctl.narrow, ev)),
            lui.common.Footer(now.toLocalDate)
          )
      }

    (homeRoute | eventRoute | sponsorsRoute).notFound(
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
