package lsug.ui
package event1

import java.time.Clock
import java.time.{LocalDateTime, LocalDate}
import lsug.{protocol => P}
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.extra.Ajax
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.CatsReact._
import cats._
import cats.implicits._
import cats.data.NonEmptyList
import java.time.format.DateTimeFormatter
import monocle.macros.{GenLens}
import monocle.std.option.{some => _some}
import monocle.function.At.{at => _at}
import Function.const

object Speaker {

  import common.{Markup, ProfilePicture}

  private def icon[A](
      name: String,
      image: String,
      link: A => String,
      label: A => String
  ) =
    ScalaComponent
      .builder[A](name)
      .render_P { a =>
        <.a(
          ^.cls := "social-media-icon",
          ^.href := link(a),
          ^.target := "_blank",
          ^.aria.label := label(a),
          <.img(
            ^.src := image,
            ^.alt := ""
          )
        )
      }
      .build

  private val Twitter = icon[P.Twitter.Handle](
    "TwitterHandle",
    P.Asset.twitter.show,
    handle => s"https://www.twitter.com/${handle.show}",
    handle => s"See tweets by ${handle.show}"
  )

  private val Github = icon[P.Github.User](
    "GithubUser",
    P.Asset.github.show,
    user => s"https://www.github.com/${user.show}",
    user => s"See GitHub profile of ${user.show}"
  )

  private def picture(profile: P.Speaker.Profile, blog: Option[P.Link]) =
    blog.map { link =>
        <.a(
          ^.href := link.show,
          ^.target := "_blank",
          ^.aria.label := s"Read ${profile.name}'s blog",
          ProfilePicture(profile.some)
        ): TagMod
      }
      .getOrElse(ProfilePicture(profile.some): TagMod)

  val Speaker =
    ScalaComponent
      .builder[Option[P.Speaker]]("Speaker")
      .render_P { speaker =>
        <.section(
          ^.cls := "speaker",
          speaker
            .map {
              case P.Speaker(
                  p @ P.Speaker.Profile(_, name, _),
                  bio,
                  P.Speaker.SocialMedia(
                    blog,
                    twitter,
                    github
                  )
                  ) =>
                React.Fragment(
                  <.header(
                    <.h3(name),
                    <.div(
                      ^.cls := "social-media",
                      picture(p, blog),
                      github.map(Github(_)),
                      twitter.map(Twitter(_))
                    )
                  ),
                  <.div(
                    ^.cls := "bio",
                    bio.zipWithIndex.map {
                      case (markup, index) => Markup.withKey(index)(markup)
                    }.toTagMod
                  )
                )
            }
            .getOrElse {
              <.div(^.cls := "placeholder")
            }
        )
      }
      .build
}
