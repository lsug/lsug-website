package lsug.ui

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.CatsReact._
import cats._
import cats.implicits._
import lsug.{protocol => P}
import Function.const
import java.time.LocalDateTime
import japgolly.scalajs.react.extra.router.RouterCtl
import cats.implicits._
import monocle.Lens
import monocle.macros.{GenLens}
import monocle.function.At.{at => _at}
import java.time.format.DateTimeFormatter
import java.time.ZoneOffset

object home {

  import common.tabs

  type PEventSummary = P.Event.Summary[P.Event.Blurb]

  import common.{MaterialIcon, markup, ProfilePicture}

  type Speakers = Map[P.Speaker.Id, P.Speaker.Profile]
  type Venues = Map[P.Venue.Id, P.Venue.Summary]

  val EventLocation = {
    ScalaComponent
      .builder[(P.Event.Location, Option[P.Venue.Summary])]("EventLocation")
      .render_P {
        case (P.Event.Location.Virtual, _) =>
          <.div(
            ^.cls := "event-location",
            MaterialIcon("cloud"),
            <.span("Virtual only")
          )
        case (P.Event.Location.Physical(_), venue) =>
          <.div(
            MaterialIcon("place"),
            venue
              .map { v =>
                <.div(
                  ^.cls := "venue-address",
                  <.span(v.name),
                  <.span(","),
                  <.span(v.address.head)
                )
              }
              .getOrElse(<.span(^.cls := "placeholder venue-address"))
          )
      }
      .build
  }

  object event {

    val ItemSpeakers = ScalaComponent
      .builder[(List[P.Speaker.Id], Speakers)]("Speakers")
      .render_P {
        case (ids, speakers) =>
          <.section(
            ^.cls := "speakers",
            <.ul(
              ids.map { id =>
                <.li(
                  <.div(
                    ^.cls := "speaker",
                    speakers
                      .get(id)
                      .map(s => <.span(^.cls := "name", s.name))
                      .getOrElse(
                        <.span(^.cls := "name placeholder")
                      ),
                    ProfilePicture(speakers.get(id))
                  )
                )
              }.toTagMod
            )
          )
      }
      .build

    val Item = ScalaComponent
      .builder[(P.Event.Blurb, Speakers)]("EventItem")
      .render_P {
        case (P.Event.Blurb(event, desc, speakerIds, _), speakers) =>
          <.section(
            ^.cls := "event-item",
            <.header(
              <.h2(event)
            ),
            <.div(
              ^.cls := "event-item-blurb",
              desc.headOption.map { m =>
                React.Fragment(
                  markup.Markup(m, markup.Options(false)),
                  desc.tail.headOption.map(const(<.p("..."))).getOrElse(None)
                )
              }.toTagMod
            ),
            ItemSpeakers((speakerIds, speakers))
          )
      }
      .build

    val format = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    case class Props(
        now: LocalDateTime,
        summary: P.Event.Summary[P.Event.Blurb],
        speakers: Speakers,
        venues: Venues
    )

    val Event = ScalaComponent
      .builder[Props]("Event")
      .render_P {
        case Props(
            now,
            P.Event.Summary(_, P.Event.Time(start, end), venue, events),
            speakers,
            venues
            ) =>
          <.a(
            ^.cls := "event",
            ^.href := s"/events/${start.format(format)}",
            <.header(
              <.h1(EventTime(now, start, end)),
              EventLocation((venue, venue.getId.flatMap(venues.get)))
            ),
            <.div(
              ^.cls := "event-content",
              events.map {
                case item @ P.Event.Blurb(e, _, _, _) =>
                  Item.withKey(e)((item, speakers))
              }.toTagMod,
              <.ul(
                ^.cls := "event-tags",
                events
                  .flatMap(_.tags)
                  .distinct
                  .map { t =>
                    <.li(
                      TagBadge(t)
                    )
                  }
                  .toTagMod
              )
            )
          )
      }
      .build
  }

  case class Props(
      router: RouterCtl[Page],
      now: LocalDateTime
  )

  case class State(
      tab: Tab,
      upcoming: Option[List[PEventSummary]],
      past: Option[List[PEventSummary]],
      speakers: Map[P.Speaker.Id, P.Speaker.Profile],
      venues: Map[P.Venue.Id, P.Venue.Summary]
  )

  object State {

    val initial = State(Tab.Upcoming, none, none, Map.empty, Map.empty)
    val _speakers = GenLens[State](_.speakers)
    val _venues = GenLens[State](_.venues)
    val _upcoming = GenLens[State](_.upcoming)
    val _past = GenLens[State](_.past)
    val _tab = GenLens[State](_.tab)
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

  val Home = {

    final class Backend($ : BackendScope[Props, State]) {

      def tab(tab: Tab)(curr: Tab) =
        tabs.Tab
          .withKey(tab.show)
          .withChildren(
            <.span(tab.show)
          )((tab.show, curr === tab, $.modState(State._tab.set(tab))))

      val UpcomingTab = tab(Tab.Upcoming) _
      val PastTab = tab(Tab.Past) _

      val labels = List(Tab.Upcoming, Tab.Past)

      def render(state: State, props: Props): VdomNode = {
        val State(tab, upcoming, past, speakers, venues) = state
        <.main(
          <.h1(^.cls := "screenreader-only", "Events at the London Scala User Group"),
          tabs.Tabs.withChildren(
            UpcomingTab(tab),
            PastTab(tab)
          )(labels.indexOf(tab)),
          tabs.TabPanel
            .withKey(Tab.upcoming.show)
            .withChildren(
              upcoming
                .map {
                  _.toNel
                    .map { events =>
                      <.ul(
                        ^.cls := "events",
                        events
                          .map { e =>
                            <.li(
                              event.Event.withKey(e.id.show)(
                                event.Props(
                                  props.now,
                                  e,
                                  speakers,
                                  venues
                                )
                              )
                            )
                          }
                          .toList
                          .toTagMod
                      )
                    }
                    .getOrElse(
                      <.div(
                        ^.cls := "placeholder",
                        <.span("There are no upcoming events.")
                      )
                    )
                }
                .getOrElse(<.div(^.cls := "placeholder"))
            )(Tab.upcoming.show, tab === Tab.upcoming),
          tabs.TabPanel
            .withKey(Tab.past.show)
            .withChildren(
              <.ul(
                ^.cls := "events",
                past
                  .map {
                    _.map { e =>
                      <.li(
                        event.Event.withKey(e.id.show)(
                          event.Props(
                            props.now,
                            e,
                            speakers,
                            venues
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
            )(Tab.past.show, tab === Tab.past)
        )
      }

      def load: Callback = {

        val pass = ().pure[AsyncCallback]

        def loadEvent(event: PEventSummary): AsyncCallback[Unit] = {
          val loadSpeakers = event.events
            .flatMap(_.speakers)
            .traverse { id =>
              for {
                speakerResource <- Resource[P.Speaker.Profile](
                  s"speakers/${id.show}/profile"
                ).value
                _ <- speakerResource
                  .bimap(
                    const(pass),
                    speaker =>
                      $.modState(State._speaker(id).set(speaker.some)).async
                  )
                  .merge
              } yield ()
            }
            .void

          val loadVenues = event.location match {
            case P.Event.Location.Physical(id) =>
              for {
                resource <- Resource[P.Venue.Summary](
                  s"venues/${id.show}/summary"
                ).value
                _ <- resource
                  .bimap(
                    const(pass),
                    venue => $.modState(State._venue(id).set(venue.some)).async
                  )
                  .merge
              } yield ()
            case _ => pass
          }

          loadSpeakers >> loadVenues
        }

        def loadEvents(
            param: String,
            lens: Lens[State, Option[List[PEventSummary]]]
        ): AsyncCallback[Unit] = {
          Resource[List[PEventSummary]](s"events?$param").value >>= (_.bimap(
            AsyncCallback.throwException,
            events =>
              $.modState(lens.set(events.some)).async >> events
                .traverse(loadEvent(_))
                .void
          ).merge)
        }

        (for {
          now <- $.props.async.map(_.now)
          instant <- AsyncCallback.point(
            now.atZone(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT)
          )
          _ <- loadEvents(s"after=$instant", State._upcoming)
          _ <- loadEvents(s"before=$instant", State._past)
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
