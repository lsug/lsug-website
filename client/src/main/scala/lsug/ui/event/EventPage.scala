package lsug.ui
package event

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

object EventPage {

  import common.modal.control.ModalProps
  import common.Speakers

  case class State(
      event: Option[P.Meetup.EventWithSetting],
      meetup: Option[P.Meetup.MeetupDotCom.Event],
      speakers: Speakers,
      modal: Option[Event.Media]
  )

  object State {
    val _event = GenLens[State](_.event)
    val _meetup = GenLens[State](_.meetup)
    val _speakers = GenLens[State](_.speakers)
    def _speaker(s: P.Speaker.Id) = _speakers ^|-> _at(s)
    val _modal = GenLens[State](_.modal)
  }

  final class Backend(
      $ : BackendScope[
        (RouterCtl[Page.Home.type], P.Meetup.Id, P.Meetup.Event.Id),
        State
      ]
  ) {

    val pattern = DateTimeFormatter.ofPattern("E, dd MMM")

    def render(s: State): VdomNode = {
      <.main(
        ^.cls := "event-page",
        s.event
          .map {
            case P.Meetup.EventWithSetting(_, event, _) =>
              <.section(
                Event
                  .Event[State, Event.Media]
                  .apply(
                    Event.Props(
                      event = event,
                      speakers = s.speakers.view
                        .filterKeys(event.speakers.contains(_))
                        .toMap,
                      modalProps = ModalProps(
                        currentModal = None,
                        lens = State._modal,
                        modify = $.modState
                      ),
                      modalId = identity
                    )
                  )
              )
          }
          .getOrElse(<.div(^.cls := "placeholder"))
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

      def event(
          meetupId: P.Meetup.Id,
          eventId: P.Meetup.Event.Id
      ): AsyncCallback[P.Meetup.EventWithSetting] =
        resource(
          State._event,
          s"meetups/${meetupId.show}/events/${eventId.show}"
        )

      def meetupDotCom(
          meetupId: P.Meetup.Id
      ): AsyncCallback[P.Meetup.MeetupDotCom.Event] =
        resource(State._meetup, s"meetups/${meetupId.show}/meetup-dot-com")

      (for {
        (_, id, eventId) <- $.props.async
        eventWithSetting <- event(id, eventId)
        _ <- speakers(eventWithSetting.event.speakers)
        _ <- meetupDotCom(eventWithSetting.setting.id)
      } yield ()).toCallback
    }
  }

  val EventPage = ScalaComponent
    .builder[(RouterCtl[Page.Home.type], P.Meetup.Id, P.Meetup.Event.Id)](
      "Event"
    )
    .initialState[State](State(none, none, Map(), none))
    .renderBackend[Backend]
    .componentDidMount(_.backend.load)
    .build
}
