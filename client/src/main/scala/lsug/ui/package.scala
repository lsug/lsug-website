package lsug

import lsug.{protocol => P}
import java.time.LocalDateTime
import java.util.Locale
import java.time.format.{DateTimeFormatter, TextStyle}

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.extra.Ajax
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.CatsReact._
import japgolly.scalajs.react.component.Generic

import Function.const
import cats._
import cats.data.EitherT
import cats.implicits._

import lsug.ui.implicits._

package object ui {

  implicit val speakerIdReusability = Reusability.byEq[P.Speaker.Id]
  implicit val profileReusability = Reusability.byEq[P.Speaker.Profile]
  implicit val localDateTime = Reusability.byEq[LocalDateTime]

  import common.{Spinner, Banner, Markup, MaterialIcon}

  val EventLocation = {
    ScalaComponent
      .builder[P.Event.Location]("EventLocation")
      .render_P {
        case P.Event.Location.Virtual =>
          <.div(
            ^.cls := "event-location",
            MaterialIcon("cloud"),
            <.span("online")
          )
        case P.Event.Location.Physical(id) =>
          <.div(
            MaterialIcon("place"),
            <.span(id.value)
          )
      }
      .build
  }

  val EventTime = {

    def pattern(s: String) = DateTimeFormatter.ofPattern(s)

    def display(
        now: LocalDateTime,
        start: LocalDateTime,
        end: LocalDateTime
    ): String = {
      val endStr = end.format(pattern("HH:mm"))
      lazy val default =
        s"""${start.format(pattern("dd MMM, HH:mm"))} - $endStr"""
      if (now.isAfter(start)) {
        default
      } else {

        val nd = now.toLocalDate
        val td = start.toLocalDate

        if (nd === td) {
          s"Today, ${start.format(pattern("HH:mm"))} - $endStr"
        } else if (nd.plusDays(1) === td) {
          s"Tomorrow, ${start.format(pattern("HH:mm"))} - $endStr"
        } else if (nd.plusDays(6).isAfter(td)) {
          s"""${start.format(pattern("E  HH:mm"))} - $endStr"""
        } else {
          default
        }
      }

    }

    ScalaComponent
      .builder[(LocalDateTime, LocalDateTime, LocalDateTime)](
        "EventSummaryTime"
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

  val PersonBadge =
    ScalaComponent
      .builder[P.Speaker.Profile]("PersonBadge")
      .render_P {
        case P.Speaker.Profile(_, name, asset) =>
          <.div(
            ^.cls := "person-badge",
            <.span(name),
            asset
              .map(a => <.img(^.src := a.show))
              .getOrElse(
                MaterialIcon("person")
              )
          )
      }
      .configure(Reusability.shouldComponentUpdate)
      .build

  val EventSummarySpeakers = {

    final class Backend(
        $ : BackendScope[List[P.Speaker.Id], Option[List[P.Speaker.Profile]]]
    ) {
      def render(
          s: Option[List[P.Speaker.Profile]],
          p: List[P.Speaker.Id]
      ): VdomNode = {
        s.map {
            case profile :: Nil =>
              <.div(
                ^.cls := "speakers",
                PersonBadge(profile)
              )
            case profiles =>
              <.div(
                ^.cls := "speakers",
                <.ul(
                  profiles.map(p => <.li(PersonBadge.withKey(p.name)(p))): _*
                )
              )
          }
          .getOrElse {
            Spinner()
          }
      }

      def load: Callback = {
        val state = (for {
          speakers <- $.props.async
          resources <- speakers
            .map(s => s"speakers/${s.show}/profile")
            .traverse(Resource[P.Speaker.Profile](_))
            .value
        } yield resources.bimap(const(none), _.some).merge)
        state.flatMap($.setState(_).async).toCallback
      }
    }

    ScalaComponent
      .builder[List[P.Speaker.Id]]("EventSpeakers")
      .initialState[Option[List[P.Speaker.Profile]]](none)
      .renderBackend[Backend]
      .componentDidMount(_.backend.load)
      .configure(Reusability.shouldComponentUpdate)
      .build
  }

  val EventSummary = {

    val Blurb = ScalaComponent
      .builder[protocol.Event.Blurb]("EventBlurb")
      .render_P {
        case protocol.Event.Blurb(event, desc, speakers, tags) =>
          <.section(
            ^.cls := "event-blurb",
            <.h2(event),
            <.div(
              ^.cls := "text-content",
              desc.zipWithIndex.map {
                case (d, i) =>
                  Markup.withKey(i)(d)
              }.toTagMod
            ),
            EventSummarySpeakers(speakers)
          )
      }
      .build

    val format = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    ScalaComponent
      .builder[
        (RouterCtl[Page.Event], LocalDateTime, P.Event.Summary[P.Event.Blurb])
      ](
        "EventSummary"
      )
      .render_P {
        case (
            router,
            now,
            P.Event.Summary(id, P.Event.Time(start, end), location, events)
            ) =>
          <.a(
            ^.cls := "event",
            ^.href := s"/events/${start.format(format)}",
            <.section(
              ^.cls := "event-summary",
              <.h1(
                EventTime(now, start, end),
                EventLocation(location)
              ),
              <.div(
                ^.cls := "event-content",
                events.map {
                  case b @ protocol.Event.Blurb(e, _, _, _) =>
                    Blurb.withKey(e)(b)
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
              // ^.onClick --> router.set(Page.Event(start.format(format)))
            )
          )
      }
      .build
  }

}
