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
import monocle.macros.{GenLens}
import monocle.function.At.{at => _at}
import java.time.format.{DateTimeFormatter, TextStyle}

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

  import common.{Spinner, Banner, Markup, MaterialIcon, markup, ProfilePicture}

  type Speakers = Map[P.Speaker.Id, P.Speaker.Profile]
  type Venues = Map[P.Venue.Id, P.Venue.Summary]

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
        case (P.Event.Blurb(event, desc, speakerIds, tags), speakers) =>
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
            P.Event.Summary(id, P.Event.Time(start, end), venue, events),
            speakers,
            venues
            ) =>
          <.a(
            ^.role := "article",
            ^.cls := "event",
            ^.href := s"/events/${start.format(format)}",
            <.header(
              <.h1(EventTime(now, start, end)),
              EventLocation(venue)
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
              ),
              <.div(
                ^.cls := "event-more",
                <.span(
                  "read more"
                )
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
      locations: Map[P.Venue.Id, P.Venue.Summary]
  )

  object State {

    val initial = State(Tab.Upcoming, none, none, Map.empty, Map.empty)
    val _speakers = GenLens[State](_.speakers)
    val _upcoming = GenLens[State](_.upcoming)
    val _past = GenLens[State](_.past)
    val _tab = GenLens[State](_.tab)
    def _speaker(s: P.Speaker.Id) = _speakers ^|-> _at(s)

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
        val now = props.now
        <.main(
          tabs.Tabs.withChildren(
            UpcomingTab(tab),
            PastTab(tab)
          )(labels.indexOf(tab)),
          tabs.TabPanel
            .withKey(Tab.upcoming.show)
            .withChildren(
              <.ul(
                ^.cls := "events",
                upcoming
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

        def loadEvent(event: PEventSummary): AsyncCallback[Unit] =
          event.events
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

        (for {
          upcomingResource <- Resource[List[PEventSummary]]("events").value
          _ <- upcomingResource
            .bimap(
              const(pass),
              upcoming =>
                //TODO: remove take
                $.modState(State._upcoming.set(upcoming.take(1).some)).async >> upcoming
                  .traverse(loadEvent(_))
                  .void
            )
            .merge
          pastResource <- Resource[List[PEventSummary]]("events").value
          _ <- pastResource
            .bimap(
              const(pass),
              past =>
                $.modState(State._past.set(past.some)).async >> past
                  .traverse(loadEvent(_))
                  .void
            )
            .merge
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
