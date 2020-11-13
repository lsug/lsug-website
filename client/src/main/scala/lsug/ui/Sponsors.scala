package lsug
package ui

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._

object sponsors {

  val Sponsors = ScalaComponent.builder
    .static("Sponsors") {
      <.main(
        ^.cls := "sponsors-page",
        <.h1(
          ^.cls := "screenreader-only",
          "Sponsors of the London Scala User Group"
        ),
        <.p(
          "The London Scala User Group website is sponsored by ",
          <.a(
            "Oxford Knight",
            ^.cls := "external",
            ^.href := "https://oxfordknight.co.uk/"
          )
        )
      )
    }
    .build
}
