package lsug.ui

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._
import cats._
import lsug.{protocol => P}
import java.time.LocalDateTime
import japgolly.scalajs.react.extra.router.RouterCtl
import cats.implicits._
import monocle.Lens
import monocle.macros.{GenLens}
import monocle.function.At.{at => _at}
import java.time.format.DateTimeFormatter
import java.time.ZoneOffset
import io.circe.Decoder

object Home {

  import event.{Summary => ESummary}
  import meetup.{Summary => MSummary}
  import common.tabs
  import common.tabs.TabProps
  import common.modal.control.{ModalProps => CModalProps}

  case class Props(
      router: RouterCtl[Page],
      now: LocalDateTime
  )

  case class State(
      tab: Tab,
      modal: Option[ModalId],
      upcoming: Option[List[P.Meetup]],
      past: Option[List[P.Meetup.EventWithSetting]],
      speakers: Map[P.Speaker.Id, P.Speaker.Profile],
      venues: Map[P.Venue.Id, P.Venue.Summary]
  )

  object State {

    val initial = State(Tab.Upcoming, none, none, none, Map.empty, Map.empty)
    val _speakers = GenLens[State](_.speakers)
    val _venues = GenLens[State](_.venues)
    val _upcoming = GenLens[State](_.upcoming)
    val _past = GenLens[State](_.past)
    val _tab = GenLens[State](_.tab)
    val _modal = GenLens[State](_.modal)
    def _speaker(s: P.Speaker.Id) = _speakers ^|-> _at(s)
    def _venue(v: P.Venue.Id) = _venues ^|-> _at(v)

  }

  sealed trait Tab

  object Tab {

    case object Upcoming extends Tab
    case object Past extends Tab

    val upcoming: Tab = Upcoming
    val past: Tab = Past

    implicit val show: Show[Tab] = Show.fromToString[Tab]
    implicit val eq: Eq[Tab] = Eq.fromUniversalEquals[Tab]
  }

  sealed trait Media

  object Media {

    object Slides extends Media
    object Video extends Media

    implicit val eq: Eq[Media] = Eq.fromUniversalEquals[Media]
  }

  case class ModalId(meetupId: P.Meetup.Id, eventId: String, media: Media)

  object ModalId {
    implicit val eq: Eq[ModalId] = Eq.fromUniversalEquals[ModalId]
  }

  type ModalProps = CModalProps[State, ModalId]

  val Home = {

    final class Backend($ : BackendScope[Props, State]) {

      def render(state: State, props: Props): VdomNode = {
        val State(tab, modal, upcoming, past, speakers, venues) = state

        val makePanel = tabs.makeTabPanel(state.tab) _
        val upcomingPanel = makePanel(
          Tab.Upcoming,
          <.ul(
            ^.cls := "cards",
            upcoming
              .map {
                _.map { m =>
                  <.li(
                    MSummary.Summary.withKey(m.setting.id.show)(
                      MSummary.Props(
                        props.now,
                        m,
                        speakers,
                        venues
                      )
                    )
                  )
                }.toTagMod
              }
              .getOrElse {
                <.div(
                  ^.cls := "placeholder",
                  <.span("There are no upcoming meetups.")
                )
              }
          )
        )
        val pastPanel = makePanel(
          Tab.Past,
          <.ul(
            ^.cls := "cards",
            past
              .map {
                _.map { e =>
                  <.li(
                    ESummary.Summary.withKey(e.setting.id.show)(
                      ESummary.Props(
                        props.now,
                        e,
                        speakers,
                        venues,
                        CModalProps(
                          modal,
                          State._modal,
                          $.modState
                        )
                      )
                    )
                  )
                }.toTagMod
              }
              .getOrElse {
                <.li(
                  <.div(^.cls := "placeholder")
                )
              }
          )
        )

        <.main(
          <.h1(
            ^.cls := "screenreader-only",
            "Events by the London Scala User Group"
          ),
          tabs.makeTabs(
            TabProps(
              modify = $.modState,
              lens = State._tab,
              currentTab = state.tab
            )
          )(
            List(Tab.Upcoming, Tab.Past),
            tab
          ),
          upcomingPanel,
          pastPanel
        )
      }

      def load: Callback = {

        def resource[R: Decoder](
            lens: Lens[State, Option[R]],
            path: String
        ): AsyncCallback[R] = {
          Resource.load[R, State]($.modState)(lens, path)
        }

        def speakers(ids: List[P.Speaker.Id]): AsyncCallback[Unit] =
          Resource.speakerProfiles[State]($.modState, State._speaker)(ids)

        def venues(locations: List[P.Meetup.Location]): AsyncCallback[Unit] =
          Resource.venues[State]($.modState, State._venue)(locations)

        (for {
          now <- $.props.async.map(_.now)
          instant <- AsyncCallback.point(
            now.atZone(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT)
          )
          upcoming <- resource(State._upcoming, s"meetups?after=$instant")
          past <- resource(State._past, s"events?before=$instant")
          _ <- venues(
            upcoming.map(_.setting.location) ++ past.map(_.setting.location)
          )
          _ <- speakers(
            (upcoming >>= (_.events) >>= (_.speakers)) ++
              (past >>= (_.event.speakers))
          )
        } yield ()).toCallback
      }
    }

    ScalaComponent
      .builder[Props]("Home")
      .initialState[State](State.initial)
      .renderBackend[Backend]
      .componentDidMount(_.backend.load)
      .build
  }

}
