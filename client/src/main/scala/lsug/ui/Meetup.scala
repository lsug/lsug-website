package lsug.ui

import lsug.{protocol => P}
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.CatsReact._
import cats.implicits._
import java.time.format.DateTimeFormatter
import monocle.macros.{GenLens}
import monocle.function.At.{at => _at}
import Function.const

object meetup {

  import common.{
    Markup,
    panel,
    MaterialIcon,
    sidesheet,
  }

  type PEvent = P.Meetup

  val Youtube = event1.Item.Youtube

  val Blurb = ScalaComponent
    .builder[P.Meetup.Event]("EventItem")
    .render_P {
      case item: P.Meetup.Event =>
        <.section(
          ^.cls := "event-item",
          <.h2(item.title),
          <.div(
            ^.cls := "text-content",
            item.description.zipWithIndex.map {
              case (d, i) =>
                Markup.withKey(i)(d)
            }.toTagMod
          )
        )
    }
    .build

  val Speaker = event1.Speaker.Speaker

  case class EventState(
      event: Option[PEvent],
      showSchedule: Boolean
  )

  type Speakers = Map[P.Speaker.Id, P.Speaker]

  case class State(
      event: Option[PEvent],
      meetup: Option[P.Meetup.MeetupDotCom.Event],
      showSchedule: Boolean,
      speakers: Speakers,
      tabs: Map[String, event1.Item.Tab],
      modal: Option[(String, event1.Item.Media)]
  )

  object State {
    val _showSchedule = GenLens[State](_.showSchedule)
    val _speakers = GenLens[State](_.speakers)
    def _speaker(s: P.Speaker.Id) = _speakers ^|-> _at(s)
    val _tabs = GenLens[State](_.tabs)
    def _tab(s: String) = _tabs ^|-> _at(s)
    val _modal = GenLens[State](_.modal)
    val _meetup = GenLens[State](_.meetup)
    val _event = GenLens[State](_.event)
  }


  object Item {

    import event1.Item.Tab
    import event1.Item.Media

    case class Props(
        tab: Tab,
        item: P.Meetup.Event,
        onToggle: Tab => Callback,
        speakers: Speakers,
        modal: Option[Media],
        onOpen: Media => Callback,
        onClose: Media => Callback
    )

    val Item = event1.Item.Item
  }

  import State._

  val Event = {

    final class Backend(
        $ : BackendScope[(RouterCtl[Page.Home.type], String), State]
    ) {

      val toggleSchedule: Boolean => Callback = { b =>
        $.modState(_showSchedule.set(b)).toCallback
      }

      val pattern = DateTimeFormatter.ofPattern("E, dd MMM")

      def render(s: State): VdomNode = {
        <.main(
          ^.cls := "meetup-page",
          event1.welcome.Welcome((s.event, s.speakers)),
          s.event
            .map {
              case event: P.Meetup =>
                <.section(
                  ^.cls := "items",
                  event.events.map { item =>
                      event1.Item.Item.withKey(item.title.show)(
                        event1.Item.Props(
                          item = item,
                          speakers = s.speakers.view
                            .filterKeys(item.speakers.contains(_))
                            .toMap,
                          modal = s.modal.filter(_._1 === item.title.show).map(_._2),
                          onOpen = media =>
                            $.modState(_modal.set((item.title.show, media).some)),
                          onClose = _ => $.modState(_modal.set(none))
                        )
                      )
                  }.toTagMod
                )
            }
            .getOrElse(<.div(^.cls := "placeholder")),
          sidesheet.SideSheet.withChildren(
            panel.Panel.withChildren(
              s.event
                .map {
                  case event: P.Meetup =>
                    React.Fragment(
                      <.div(
                        ^.cls := "date",
                        <.span(^.cls := "material-icons", "event"),
                        <.span(event.setting.time.start.format(pattern))
                      ),
                      panel.Summary.withChildren(
                        event1.plan.Time(event.setting.time.start, event.setting.time.end),
                        <.div(
                          ^.cls := "panel-toggle-icon",
                          MaterialIcon("expand_more")
                        )
                      )((s.showSchedule, toggleSchedule)),
                      panel.Details.withChildren(
                        event1.plan.Schedule(event.schedule)
                      )(s.showSchedule)
                    )
                }
                .getOrElse {
                  <.div(^.cls := "placeholder")
                }
            )(),
            event1.plan.Meetup(_meetup.get(s))
          )()
        )
      }

      def load: Callback = {

        val pass = ().pure[AsyncCallback]
        (for {
          (_, id) <- $.props.async
          eventResource <- Resource[PEvent](s"events/${id}").value
          _ <- eventResource
            .bimap(
              const(pass),
              ev => $.modState(_event.set(ev.some)).async
            )
            .merge
          _ <- eventResource
            .map {
              case meetup: P.Meetup =>
                (meetup.hosts ++ meetup.events.flatMap(_.speakers)).traverse {
                  speakerId =>
                    for {
                      speakerResource <- Resource[P.Speaker](
                        s"speakers/${speakerId.show}"
                      ).value
                      _ <- speakerResource
                        .bimap(
                          const(pass),
                          speaker =>
                            $.modState(_speaker(speakerId).set(speaker.some)).async
                        )
                        .merge
                    } yield ()
                }
            }
            .getOrElse(pass) *> eventResource
            .map(_.setting.id)
            .map { event =>
              for {
                meetupResource <- Resource[P.Meetup.MeetupDotCom.Event](
                  s"events/${event.show}/meetup"
                ).value
                _ <- meetupResource
                  .bimap(
                    const(pass),
                    meetup => $.modState(_meetup.set(meetup.some)).async
                  )
                  .merge
              } yield ()

            }
            .getOrElse(pass)
        } yield ()).toCallback
      }
    }

    ScalaComponent
      .builder[(RouterCtl[Page.Home.type], String)]("Event")
      .initialState[State](State(none, none, false, Map(), Map(), none))
      .renderBackend[Backend]
      .componentDidMount(_.backend.load)
      .build
  }
}
