package lsug
package ui

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._

object NavBar {

  import common.MaterialIcon
  
  val NavBar = ScalaComponent
    .builder[Unit]("NavBar")
    .renderStatic(
      <.nav(
        <.div(
          <.a(
            ^.cls := "abbrev-name",
            MaterialIcon("home"),
            <.span("LSUG"),
            ^.href := "/"
          )
        ),
        <.div(
          <.a("About"),
          <.a("Sponsors", ^.href := "/sponsors")
        )
      )
    )
    .build
}
