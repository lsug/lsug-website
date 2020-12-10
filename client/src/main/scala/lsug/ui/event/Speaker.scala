package lsug.ui
package event

import lsug.{protocol => P}
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._
import cats.implicits._

object Speaker {

  import common.{markup, ProfilePicture}

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
    blog
      .map { link =>
        <.a(
          ^.href := link.show,
          ^.target := "_blank",
          ^.aria.label := s"Read ${profile.name}'s blog",
          ProfilePicture(profile.some)
        ): TagMod
      }
      .getOrElse(ProfilePicture(profile.some): TagMod)

  // TODO: add styling to class
  private def pronoun(p: Option[P.Speaker.Pronoun]) =
    p.map { pn =>
      <.p(
        ^.cls := "pronoun",
        "Referred to as: ",
        <.b(s"${pn.subjective}/${pn.objective}")
      )
    }

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
                  ),
                  pr
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
                  pronoun(pr),
                  <.div(
                    ^.cls := "bio",
                    bio.zipWithIndex.map {
                      case (bio, index) =>
                        markup.Markup.withKey(index)(bio, markup.Options(true))
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
