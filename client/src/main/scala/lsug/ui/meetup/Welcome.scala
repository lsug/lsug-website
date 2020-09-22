package lsug.ui
package meetup

import lsug.{protocol => P}
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._
import cats.implicits._
import cats.data.NonEmptyList
import java.time.format.DateTimeFormatter

object Welcome {

  type PEvent = P.Meetup

  type Speakers = Map[P.Speaker.Id, P.Speaker]

  import common.{ProfilePicture, markup}

  val EventHosts =
    ScalaComponent
      .builder[Option[NonEmptyList[P.Speaker]]]("EventHosts")
      .render_P {
        _.map { speakers =>
          <.ul(
            ^.cls := "hosts",
            speakers
              .map { host =>
                val name = host.profile.name.split(" ")
                <.li(
                  ^.cls := "host",
                  name.headOption.map(n =>
                    React.Fragment(
                      <.span(
                        n
                      ),
                      <.span(
                        name.tail.mkString(" ")
                      )
                    )
                  ),
                  ProfilePicture(host.profile.some)
                )
              }
              .toList
              .toTagMod
          )
        }.getOrElse(<.ul("hosts placeholder"))
      }
      .build

  val Welcome = {

    val pattern = DateTimeFormatter.ofPattern("E, dd MMM")

    ScalaComponent
      .builder[(Option[PEvent], Speakers)]("EventWelcome")
      .render_P {
        case (event, speakers) =>
          event
            .map {
              case P.Meetup(
                  hosts,
                  welcome,
                  _,
                  P.Meetup.Setting(_, time, _),
                  _,
                  _
                  ) =>
                <.header(
                  ^.cls := "welcome",
                  <.h1(
                    ^.cls := "screenreader-only",
                    time.start.format(pattern)
                  ),
                  <.section(
                    ^.cls := "message",
                    welcome.zipWithIndex.map {
                      case (m, i) =>
                        markup.Markup.withKey(i.toLong)(m, markup.Options(true))
                    }.toTagMod
                  ),
                  EventHosts(hosts.traverse(speakers.get))
                ),
            }
            .getOrElse(
              <.header(^.cls := "event-header placeholder")
            )
      }
      .build
  }
}
