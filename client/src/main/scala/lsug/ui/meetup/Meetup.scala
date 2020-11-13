package lsug.ui
package meetup

import io.circe.Decoder
import monocle.Lens
import lsug.{protocol => P}
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.CatsReact._
import cats.implicits._
import cats._
import java.time.format.DateTimeFormatter
import monocle.macros.{GenLens}
import monocle.function.At.{at => _at}

object Meetup {

  import event.Event.Media
  import event.{Event => UIEvent}
  import common.{Speakers, MaterialIcon}
  import common.modal.control.ModalProps
  import common.tabs.TabProps

  type PMeetup = P.Meetup

  case class State(
      meetup: Option[PMeetup],
      meetupDotCom: Option[P.Meetup.MeetupDotCom.Event],
      showSchedule: Boolean,
      speakers: Speakers,
      modal: Option[ModalId],
      tab: UIEvent.Tab
  )

  object State {
    val _showSchedule = GenLens[State](_.showSchedule)
    private val _speakers = GenLens[State](_.speakers)
    def _speaker(s: P.Speaker.Id) = _speakers ^|-> _at(s)
    val _modal = GenLens[State](_.modal)
    val _meetupDotCom = GenLens[State](_.meetupDotCom)
    val _meetup = GenLens[State](_.meetup)
    val _tab = GenLens[State](_.tab)
  }

  import State._

  case class ModalId(id: P.Meetup.Event.Id, media: Media)

  object ModalId {
    implicit val eq: Eq[ModalId] = Eq.fromUniversalEquals[ModalId]
  }

  val Meetup = {

    final class Backend(
        $ : BackendScope[(RouterCtl[Page.Home.type], P.Meetup.Id), State]
    ) {

      val toggleSchedule: Boolean => Callback = { b =>
        $.modState(_showSchedule.set(b)).toCallback
      }

      val pattern = DateTimeFormatter.ofPattern("E, dd MMM")

      def render(s: State): VdomNode = {
        <.main(
          ^.cls := "meetup-page",
          Welcome.Welcome((s.meetup, s.speakers)),
          s.meetup
            .map { meetup =>
              <.section(
                ^.cls := "events",
                meetup.events.zipWithIndex.map {
                  case (event, i) =>
                    val eventId = new P.Meetup.Event.Id(i)
                    UIEvent
                      .Event[State, ModalId]
                      .withKey(s"event-${eventId.show}")(
                        UIEvent.Props(
                          event = event,
                          speakers = s.speakers.view
                            .filterKeys(event.speakers.contains(_))
                            .toMap,
                          modalId = ModalId(new P.Meetup.Event.Id(i), _),
                          modalProps = ModalProps(
                            currentModal = s.modal,
                            lens = State._modal,
                            modify = $.modState
                          ),
                          tabProps = TabProps(
                            currentTab = s.tab,
                            lens = State._tab,
                            modify = $.modState
                          )
                        )
                      )
                }.toTagMod
              )
            }
            .getOrElse(<.div(^.cls := "placeholder")),
          SideSheet.SideSheet.withChildren(
            SideSheet.Panel.Panel.withChildren(
              s.meetup
                .map { meetup =>
                  React.Fragment(
                    <.div(
                      ^.cls := "date",
                      <.span(^.cls := "material-icons", "event"),
                      <.span(meetup.setting.time.start.format(pattern))
                    ),
                    SideSheet.Panel.Summary.withChildren(
                      plan.Time(
                        meetup.setting.time.start,
                        meetup.setting.time.end
                      ),
                      <.div(
                        ^.cls := "panel-toggle-icon",
                        MaterialIcon("expand_more")
                      )
                    )((s.showSchedule, toggleSchedule)),
                    SideSheet.Panel.Details.withChildren(
                      plan.Schedule(meetup.schedule)
                    )(s.showSchedule)
                  )
                }
                .getOrElse {
                  <.div(^.cls := "placeholder")
                }
            )(),
            plan.Meetup(_meetupDotCom.get(s))
          )()
        )
      }

      def load: Callback = {

        def resource[R: Decoder](
            lens: Lens[State, Option[R]],
            path: String
        ): AsyncCallback[R] = {
          Resource.load[R, State]($.modState)(lens, path)
        }

        (for {
          (_, id) <- $.props.async
          meetup <- resource(_meetup, s"meetups/${id.show}")
          _ <- {
            val speakerIds = (meetup.hosts ++ meetup.events.flatMap(_.speakers))
            Resource.speakers[State]($.modState, State._speaker)(
              speakerIds.toList
            )
          }
          _ <- resource(
            _meetupDotCom,
            s"meetups/${meetup.setting.id.show}/meetup-dot-com"
          )
        } yield ()).toCallback
      }
    }

    ScalaComponent
      .builder[(RouterCtl[Page.Home.type], P.Meetup.Id)]("Meetup")
      .initialState[State](
        State(none, none, false, Map(), none, UIEvent.Tab.about)
      )
      .renderBackend[Backend]
      .componentDidMount(_.backend.load)
      .build
  }
}
