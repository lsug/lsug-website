package lsug.ui

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.extra.Ajax
import japgolly.scalajs.react.CatsReact._
import cats._
import cats.implicits._
import cats.data._
import lsug.protocol
import lsug.{protocol => P}
import Function.const
import java.time.LocalDateTime
import japgolly.scalajs.react.extra.router.{Router, RouterCtl}
import cats.implicits._
import io.circe._
import io.circe.parser._

object home {

  import common.{Tabbed, tabs}

  type PEventSummary = P.Event.Summary[P.Event.Blurb]

  val EventSearch = {

    final class Backend(
        $ : BackendScope[
          (RouterCtl[Page.Event], LocalDateTime, List[PEventSummary]),
          Option[String]
        ]
    ) {
      def render(
          s: Option[String],
          p: (RouterCtl[Page.Event], LocalDateTime, List[PEventSummary])
      ): VdomNode = {
        <.div(
          <.div(
            ^.cls := "event-search-box",
            <.label(^.`for` := "event-search"),
            <.input(
              ^.tpe := "text",
              ^.id := "event-search",
              ^.onChange ==> ((e: ReactEventFromInput) =>
                $.setState(e.target.value.some)
              )
            )
          ),
          <.section(
            p._3.zipWithIndex.map {
              case (summary @ P.Event.Summary(_, _, _, blurbs), i) =>
                EventSummary.withKey(i)((p._1, p._2, summary)).when {
                  s.map { search =>
                      blurbs.find(b => b.event.contains(search)).isDefined
                    }
                    .getOrElse(true)
                }
            }.toTagMod
          )
        )
      }
    }

    ScalaComponent
      .builder[(RouterCtl[Page.Event], LocalDateTime, List[PEventSummary])](
        "EventSearch"
      )
      .initialState[Option[String]](none)
      .renderBackend[Backend]
      .build
  }

  val Home = {

    sealed trait State

    sealed trait Tab

    object Tab {

      case object Upcoming extends Tab
      case object Past extends Tab

      val upcoming: Tab = Upcoming
      val past: Tab = Past

      implicit val show: Show[Tab] = Show.fromToString[Tab]
      implicit val eq: Eq[Tab] = Eq.fromUniversalEquals[Tab]
    }

    object State {

      case object Loading extends State
      case object Error extends State
      case class Loaded(tab: Tab, blurbs: List[PEventSummary]) extends State
    }

    final class Backend(
        $ : BackendScope[(RouterCtl[Page], LocalDateTime), State]
    ) {

      def tab(tab: Tab)(curr: Tab) =
        tabs.Tab
          .withKey(tab.show)
          .withChildren(
            <.span(tab.show)
          )((tab.show, curr === tab, $.modState {
            case State.Loaded(_, blurbs) =>
              State.Loaded(tab, blurbs)
            case s => s
          }))

      val UpcomingTab = tab(Tab.Upcoming) _
      val PastTab = tab(Tab.Past) _

      val labels = List(Tab.Upcoming, Tab.Past)

      def render(s: State, p: (RouterCtl[Page], LocalDateTime)): VdomNode = {
        s match {
          case State.Loading =>
            common.Spinner()
          case State.Error =>
            <.div("oops")
          case State.Loaded(tab, blurbs) =>
            <.main(
              tabs.Tabs.withChildren(
                UpcomingTab(tab),
                PastTab(tab)
              )(labels.indexOf(tab)),
              tabs.TabPanel
                .withKey(Tab.upcoming.show)
                .withChildren(
                  EventSummary(p._1.narrow, p._2, blurbs.head)
                )(Tab.upcoming.show, tab === Tab.upcoming),
              tabs.TabPanel
                .withKey(Tab.past.show)
                .withChildren(
                  EventSearch(p._1.narrow, p._2, blurbs)
                )(Tab.past.show, tab === Tab.past)
            )
        }
      }

      def load: Callback =
        Resource[List[PEventSummary]]("events")
          .bimap(
            const(State.Error),
            State.Loaded(Tab.Upcoming, _)
          )
          .merge[State]
          .flatMap($.setState(_).async)
          .toCallback
    }

    ScalaComponent
      .builder[(RouterCtl[Page], LocalDateTime)]("Main")
      .initialState[State](State.Loading)
      .renderBackend[Backend]
      .componentDidMount(_.backend.load)
      .build
  }

}
