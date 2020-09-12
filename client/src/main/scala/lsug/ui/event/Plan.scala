package lsug.ui
package event1

import lsug.{protocol => P}
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._
import cats.implicits._
import java.time.format.DateTimeFormatter
import java.time.LocalDateTime

object plan {

  import common.{
    MaterialIcon,
    Spinner
  }

  val Time = {

    val pattern = DateTimeFormatter.ofPattern("HH:mm")

    ScalaComponent
      .builder[(LocalDateTime, LocalDateTime)]("EventTime")
      .render_P {
        case (start, end) =>
          <.div(
            ^.cls := "event-time",
            MaterialIcon("schedule"),
            <.span(start.format(pattern)),
            <.span("-"),
            <.span(end.format(pattern))
          )
      }
      .build
  }


  val Meetup = {

    val Logo = ScalaComponent.builder
      .static("MeetupLogo") {
        <.img(
          ^.cls := "logo",
          ^.src := "https://secure.meetup.com/s/img/0/logo/svg/logo--mSwarm.svg",
          ^.alt := ""
        )
      }
      .build

    ScalaComponent
      .builder[Option[P.Meetup.MeetupDotCom.Event]]("Meetup")
      .render_P { props =>
        <.section(
          ^.cls := "meetup",
          props
            .map {
              case P.Meetup.MeetupDotCom.Event(link, attendees) =>
                <.a(
                  ^.href := link.show,
                  ^.target := "_blank",
                  <.h2(Logo(), <.span(s"${attendees} attendees")),
                  <.div(<.span(^.cls := "sign-up", "view"))
                )
            }
            .getOrElse {
              Spinner()
            }
        )

      }
      .build
  }


  val Schedule = {
    //TODO colour current
    val format = DateTimeFormatter.ofPattern("HH:mm")

    ScalaComponent
      .builder[P.Meetup.Schedule]("EventSchedule")
      .render_P {
        case P.Meetup.Schedule(
            items
            ) =>
          <.ol(
            ^.cls := "event-schedule",
            items
              .map {
                case P.Meetup.Schedule.Item(name, start, end) =>
                  <.li(
                    <.span(
                      ^.cls := "event-time",
                      start.format(format),
                      "-",
                      end.format(format)
                    ),
                    <.span(
                      ^.cls := "event-name",
                      name
                    )
                  )
              }
              .toList
              .toTagMod
          )
      }
      .build
  }
}
