package lsug.ui
package event1

import lsug.{protocol => P}
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._
import cats.implicits._
import Function.const
import monocle.macros.{GenLens}
import java.time.format.DateTimeFormatter
import japgolly.scalajs.react.CatsReact._
import japgolly.scalajs.react.extra.router.RouterCtl
import monocle.function.At.{at => _at}

object eventPage {

  type PEvent = P.Meetup

  case class EventState(
      event: Option[P.Meetup.EventWithSetting]
  )

  type Speakers = Map[P.Speaker.Id, P.Speaker]

  case class State(
      event: Option[P.Meetup.EventWithSetting],
      meetup: Option[P.Meetup.MeetupDotCom.Event],
      speakers: Speakers,
      tabs: Map[String, event1.Item.Tab],
      modal: Option[(String, event1.Item.Media)]
  )

  object State {
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
        $ : BackendScope[(RouterCtl[Page.Home.type], P.Meetup.Id, P.Meetup.Event.Id), State]
    ) {

      val pattern = DateTimeFormatter.ofPattern("E, dd MMM")

      def render(s: State): VdomNode = {
        <.main(
          ^.cls := "event-page meetup-page",
          s.event
            .map {
              case P.Meetup.EventWithSetting(_, event, _) =>
                <.section(
                  ^.cls := "items",
                  event1.Item.Item.withKey(event.title.show)(
                        event1.Item.Props(
                          tab = s.tabs.get(event.title.show).getOrElse(event1.Item.Tab.About),
                          item = event,
                          onToggle = tab => $.modState(_tab(event.title.show).set(tab.some)),
                          speakers = s.speakers.view
                            .filterKeys(event.speakers.contains(_))
                            .toMap,
                          modal = s.modal.filter(_._1 === event.title.show).map(_._2),
                          onOpen = media =>
                            $.modState(_modal.set((event.title.show, media).some)),
                          onClose = _ => $.modState(_modal.set(none))
                        )
                      )
                )
            }
            .getOrElse(<.div(^.cls := "placeholder")),
        )
      }

      def load: Callback = {

        val pass = ().pure[AsyncCallback]
        (for {
          (_, id, eventId) <- $.props.async
          eventResource <- Resource[P.Meetup.EventWithSetting](s"meetups/${id.show}/events/${eventId.show}").value
          _ <- eventResource
            .bimap(
              const(pass),
              ev => $.modState(_event.set(ev.some)).async
            )
            .merge
          _ <- eventResource
            .map {
              case event: P.Meetup.EventWithSetting =>
                event.event.speakers.traverse {
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
                  s"events/${event.show}"
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
      .builder[(RouterCtl[Page.Home.type], P.Meetup.Id, P.Meetup.Event.Id)]("Event")
      .initialState[State](State(none, none, Map(), Map(), none))
      .renderBackend[Backend]
      .componentDidMount(_.backend.load)
      .build
  }
}
