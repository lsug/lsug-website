package lsug
package ui

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._
import lsug.ui.implicits._

object Footer {

  val Disclaimer = {

    val format = DateTimeFormatter.ofPattern("yyyy")

    ScalaComponent
      .builder[(String, LocalDate)]("Disclaimer")
      .render_P {
        case (id, now) =>
          <.div(
            ^.cls := "disclaimer",
            <.p(
              s"© ${now.format(format)}.",
              s"London Scala User Group is a registered community interest group in England and Wales (",
              <.a(
                id,
                ^.href := s"https://beta.companieshouse.gov.uk/company/${id}"
              ),
              ")"
            )
          )
      }
      .build
  }

  val Footer = ScalaComponent
    .builder[LocalDate]("Footer")
    .render_P(now =>
      <.footer(
        ^.cls := "footer",
        Disclaimer(("12325025", now))
      )
    )
    .configure(Reusability.shouldComponentUpdate)
    .build

}
