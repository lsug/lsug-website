package lsug.ui
package event

import monocle.{Lens, Iso}
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
  import common.tabs.TabProps
  import common.Speakers

  case class State(
      event: Option[P.Meetup.EventWithSetting],
      meetup: Option[P.Meetup.MeetupDotCom.Event],
      speakers: Speakers,
      modal: Option[Event.Media],
      tabs: Map[P.Meetup.Event.Id, Event.Tab]
  )

  object State {
    val _event = GenLens[State](_.event)
    val _meetup = GenLens[State](_.meetup)
    val _speakers = GenLens[State](_.speakers)
    def _speaker(s: P.Speaker.Id) = _speakers ^|-> _at(s)
    val _modal = GenLens[State](_.modal)
    val _tabs = GenLens[State](_.tabs)

    // Technically, this is an unlawful `Iso` under the default `Eq` — but we define an `Eq[Option[A]]` where
    // ```None == default```
    def _non[A](a: A) =
      Iso[Option[A], A] {
        case Some(aa) => aa
        case None     => a
      }(Some(_))

    def _tab(e: P.Meetup.Event.Id) =
      _tabs ^|-> _at(e) ^<-> _non(Event.Tab.about)
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
            case P.Meetup.EventWithSetting(_, event, eventId) =>
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
                        currentModal = s.modal,
                        lens = State._modal,
                        modify = $.modState
                      ),
                      modalId = identity,
                      tabProps = TabProps(
                        currentTab = State
                          ._tab(eventId)
                          .get(s),
                        lens = State._tab(eventId),
                        modify = $.modState
                      )
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
    .initialState[State](State(none, none, Map(), none, Map()))
    .renderBackend[Backend]
    .componentDidMount(_.backend.load)
    .build
}
