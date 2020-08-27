package lsug
package ui

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.extra.Ajax
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.CatsReact._

object sponsors {

  val Sponsors = ScalaComponent.builder
    .static("Sponsors") {
      <.div(^.cls := "sponsors-page",
        <.p("The London Scala User Group website is sponsored by ",
        <.a("Oxford Knight", ^.href :="https://oxfordknight.co.uk/")
      ))
    }.build
}
