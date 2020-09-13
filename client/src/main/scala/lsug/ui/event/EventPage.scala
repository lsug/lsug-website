package lsug.ui
package event1

import monocle.Lens
import io.circe.Decoder
import lsug.{protocol => P}
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._
import cats.implicits._
import monocle.macros.{GenLens}
import java.time.format.DateTimeFormatter
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
                          item = event,
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

        def resource[R: Decoder](
            lens: Lens[State, Option[R]],
            path: String
        ): AsyncCallback[R] = {
          Resource.load[R, State]($.modState)(lens, path)
        }

        def speakers(ids: List[P.Speaker.Id]): AsyncCallback[Unit] =
          Resource.speakers[State]($.modState, State._speaker)(ids)

        def event(meetupId: P.Meetup.Id, eventId: P.Meetup.Event.Id): AsyncCallback[P.Meetup.EventWithSetting] =
          resource(_event, s"meetups/${meetupId.show}/events/${eventId.show}")

        def meetupDotCom(meetupId: P.Meetup.Id): AsyncCallback[P.Meetup.MeetupDotCom.Event] =
          resource(_meetup, s"meetups/${meetupId.show}/meetup-dot-com")

        (for {
          (_, id, eventId) <- $.props.async
          eventWithSetting <- event(id, eventId)
          _ <- speakers(eventWithSetting.event.speakers)
          _ <- meetupDotCom(eventWithSetting.setting.id)
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
