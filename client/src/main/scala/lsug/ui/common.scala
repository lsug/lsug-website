package lsug
package ui

import cats._
import cats.implicits._
import lsug.{protocol => P}
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._
import org.scalajs.dom.html
import monocle.Lens
import Function.const
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import lsug.ui.implicits._

object common {

  type Speakers = Map[P.Speaker.Id, P.Speaker]
  type SpeakerProfiles = Map[P.Speaker.Id, P.Speaker.Profile]
  type Venues = Map[P.Venue.Id, P.Venue.Summary]

  val Spinner = ScalaComponent
    .builder[Unit]("Spinner")
    .render_(<.div(^.cls := "spinner"))
    .build

  val MaterialIcon = ScalaComponent
    .builder[String]("MaterialIcon")
    .render_P(<.span(^.cls := "material-icons", ^.aria.hidden := true, _))
    .configure(Reusability.shouldComponentUpdate)
    .build

  object markup {

    case class Options(link: Boolean)

    private def toTagMod(markup: P.Markup.Text, options: Options): TagMod = {
      markup match {
        case P.Markup.Text.Plain(s)         => s
        case P.Markup.Text.Styled.Strong(s) => <.strong(s)
        case P.Markup.Text.Link(text, loc) =>
          if (options.link) {
            <.a(^.cls := "external", ^.href := loc, ^.target := "_blank", text)
          } else {
            text
          }
      }
    }

    val Markup = ScalaComponent
      .builder[(P.Markup, Options)]("Markup")
      .render_P {
        case (P.Markup.Paragraph(text), options) =>
          <.p(text.map(toTagMod(_, options)).toList.toTagMod)
        case _ =>
          throw new Error(
            "Markup rendering for non-paragraph elements has not been implemented"
          )
      }
      .build
  }

  object tabs {

    val Tab = ScalaComponent
      .builder[(String, Boolean, Callback)]("Tab")
      .render_PC {
        case ((label, selected, onSelect), children) =>
          <.button(
            ^.role := "tab",
            ^.aria.selected := selected,
            ^.aria.controls := label,
            ^.onClick --> onSelect,
            children
          )
      }
      .build

    val Tabs = {

      final class Backend($ : BackendScope[Int, Int]) {

        private val ref = Ref[html.Div]

        def render(props: Int, state: Int, children: PropsChildren) = {
          val width = state
          <.div(
            ^.cls := "tabs",
            <.div(
              <.div.withRef(ref)(
                ^.role := "tablist",
                children
              ),
              <.span(
                ^.cls := "tab-indicator",
                ^.width := s"${width.show}px",
                ^.left := s"${props * width}px"
              )
            )
          ),
        }

        def init: Callback =
          ref.get.flatMap { el =>
            val width = el.firstElementChild.domAsHtml.offsetWidth
            $.setState(width.toInt)
          }.void
      }

      ScalaComponent
        .builder[Int]("Tabs")
        .initialState[Int](0)
        .renderBackendWithChildren[Backend]
        .componentDidMount(_.backend.init)
        .build
    }

    val TabPanel = ScalaComponent
      .builder[(String, Boolean)]("TabPanel")
      .render_PC {
        case ((label, selected), children) =>
          <.div(
            ^.id := label,
            ^.role := "tabpanel",
            ^.hidden := !selected,
            children
          )
      }
      .build

    case class TabProps[S, T](
        currentTab: T,
        lens: Lens[S, T],
        modify: (S => S) => Callback
    )

    def makeTabs[S, T: Show: Eq](
        tabProps: TabProps[S, T]
    )(
        tabs: List[T],
        current: T
    ): VdomNode = {
      Tabs.withChildren(tabs.map { tab =>
        Tab
          .withKey(tab.show)
          .withChildren(
            <.span(tab.show)
          )(
            (
              tab.show,
              tabProps.currentTab === tab,
              tabProps.modify(tabProps.lens.set(tab))
            )
          )
      }.toReactFragment)(tabs.indexOf(current))
    }

    def makeTabPanel[S, T: Show: Eq](
        currentTab: T
    )(
        tab: T,
        panel: VdomElement
    ): VdomNode = {
      TabPanel
        .withKey(tab.show)
        .withChildren(panel)(tab.show, currentTab === tab)
    }
  }

  object modal {

    val Overlay = ScalaComponent
      .builder[Boolean]("ModalOverlay")
      .render_P(open =>
        <.div(
          ^.cls := (if (open) "modal-overlay" else "hidden modal-overlay")
        )
      )
      .build

    val Modal = ScalaComponent
      .builder[(Boolean, Callback)]("Modal")
      .render_PC {
        case ((open, onClose), children) =>
          <.div(
            ^.cls := "modal",
            Overlay(open),
            <.div(
              ^.role := "dialog",
              ^.cls := (if (open) "open" else "hidden"),
              <.div(
                ^.cls := "header",
                <.button(
                  ^.cls := "close",
                  MaterialIcon("close"),
                  ^.onClick --> onClose
                )
              ),
              // delay loading of children
              children.when(open)
            )
          )
      }
      .build

    object control {

      case class ModalProps[S, I](
          currentModal: Option[I],
          lens: Lens[S, Option[I]],
          modify: (S => S) => Callback
      )

      case class Props[S, I](
          modal: ModalProps[S, I],
          id: I,
          label: String,
          icon: String,
          src: String
      )

      def apply[S, I: Eq] =
        ScalaComponent
          .builder[Props[S, I]]("ModalControl")
          .render_P {
            case Props(props, id, label, icon, src) =>
              <.div(
                ^.cls := "modal-control",
                <.button(
                  ^.cls := "open-media",
                  ^.onClick --> props.modify(props.lens.set(id.some)),
                  MaterialIcon(icon),
                  <.span(
                    ^.cls := "modal-control-label",
                    label
                  )
                ),
                Modal.withChildren(
                  <.div(
                    ^.cls := label,
                    <.iframe(
                      ^.src := src,
                      ^.frameBorder := "0",
                      ^.allowFullScreen := true
                    )
                  )
                )(
                  props.currentModal.map(_ === id).getOrElse(false),
                  props.modify(props.lens.set(none))
                )
              )

          }
          .build
    }
  }

  val ProfilePicture =
    ScalaComponent
      .builder[Option[P.Speaker.Profile]]("ProfilePicture")
      .render_P { profile =>
        <.div(
          ^.cls := "profile-picture",
          (for {
            P.Speaker.Profile(_, _, asset, _) <- profile
          } yield asset
            .map { pic => <.img(^.src := pic.show, ^.alt := "") }
            .getOrElse[TagMod](MaterialIcon("person"))).getOrElse(
            <.div(^.cls := "placeholder")
          )
        )
      }
      .configure(Reusability.shouldComponentUpdate)
      .build

  val TimeRange = {

    def pattern(s: String) = DateTimeFormatter.ofPattern(s)

    def display(
        now: LocalDateTime,
        start: LocalDateTime,
        end: LocalDateTime
    ): String = {
      val endStr = end.format(pattern("HH:mm"))
      lazy val default =
        s"""${start.format(pattern("dd MMM, HH:mm"))} - $endStr"""
      val isPast = now.isAfter(start)
      if (isPast) {
        default
      } else {
        val nd = now.toLocalDate
        val td = start.toLocalDate
        val isToday = nd === td
        val isTomorrow = nd.plusDays(1) === td
        val isThisWeek = nd.plusDays(6).isAfter(td)

        if (isToday) {
          s"Today, ${start.format(pattern("HH:mm"))} - $endStr"
        } else if (isTomorrow) {
          s"Tomorrow, ${start.format(pattern("HH:mm"))} - $endStr"
        } else if (isThisWeek) {
          s"""${start.format(pattern("E  HH:mm"))} - $endStr"""
        } else {
          default
        }
      }

    }

    ScalaComponent
      .builder[(LocalDateTime, LocalDateTime, LocalDateTime)](
        "TimeRange"
      )
      .render_P {
        case (now, start, end) =>
          <.div(
            ^.cls := "event-time",
            MaterialIcon("event"),
            <.span(
              display(now, start, end)
            )
          )
      }
      .configure(Reusability.shouldComponentUpdate)
      .build
  }

  val Location = {
    ScalaComponent
      .builder[(P.Meetup.Location, Option[P.Venue.Summary])]("EventLocation")
      .render_P {
        case (P.Meetup.Location.Virtual, _) =>
          <.div(
            ^.cls := "virtual",
            MaterialIcon("cloud"),
            <.span("Virtual only")
          )
        case (P.Meetup.Location.Physical(_), venue) =>
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

  val SpeakerProfiles = ScalaComponent
    .builder[(List[P.Speaker.Id], SpeakerProfiles)]("Speakers")
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

  val EventDescription = ScalaComponent
    .builder[List[P.Markup]]("EventDescription")
    .render_P { description =>
      <.div(
        ^.cls := "event-description",
        description.headOption.map { m =>
          React.Fragment(
            markup.Markup(m, markup.Options(false)),
            description.tail.headOption
              .map(const(<.p("…")))
              .getOrElse(None)
          )
        }.toTagMod
      )
    }
    .build

  val TagBadge =
    ScalaComponent
      .builder[String]("TagBadge")
      .render_P(tag =>
        <.div(
          ^.cls := "tag-badge",
          <.span(tag)
        )
      )
      .build
}
