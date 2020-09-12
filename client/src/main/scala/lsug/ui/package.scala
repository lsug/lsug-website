package lsug

import lsug.{protocol => P}
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.CatsReact._

import Function.const
import cats.implicits._

import lsug.ui.implicits._
import lsug.ui.common.{MaterialIcon, Spinner}

package object ui {

  implicit val speakerIdReusability = Reusability.byEq[P.Speaker.Id]
  implicit val profileReusability = Reusability.byEq[P.Speaker.Profile]
  implicit val localDateTime = Reusability.byEq[LocalDateTime]


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
          s: Option[List[P.Speaker.Profile]]
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

}
