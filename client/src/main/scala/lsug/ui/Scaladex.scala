package lsug.ui
package event

import lsug.protocol.Github.{Org, Repo}
import lsug.protocol.Scaladex.Project
import lsug.protocol.Asset
import cats.implicits._
import monocle.Iso
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._

object Scaladex {

  val url = "https://index.scala-lang.org"

  val Logo = ScalaComponent
    .builder[Unit]("ScaladexLogo")
    .renderStatic(<.img(^.cls := "scaladex-logo", ^.src := Asset.scaladex.show))
    .build

  val Badge = {
    final class Backend(
        $ : BackendScope[(Org, Repo), Option[Project]]
    ) {

      def render(props: (Org, Repo)): VdomNode = {
        val (org, repo) = props
        <.div(
          ^.cls := "scaladex-badge",
          Logo(),
          <.a(
            ^.href := s"https://index.scala-lang.org/${org.show}/${repo.show}",
            <.span(org.show),
            <.span("/"),
            <.span(repo.show)
          )
        )
      }

      def load: Callback = {
        (for {
          (org, repo) <- $.props.async
          _ <- Resource.load($.modState)(
            Iso.id.asLens,
            s"scaladex/${org.show}/${repo.show}"
          )
        } yield ()).toCallback
      }
    }

    ScalaComponent
      .builder[(Org, Repo)]("ScaladexBadge")
      .initialState[Option[Project]](
        None
      )
      .renderBackend[Backend]
      .componentDidMount(_.backend.load)
      .build
  }

}
