package lsug.ui
package meetup

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._
import cats.implicits._
import lsug.ui.implicits._

object SideSheet {

  object Panel {

    val Summary = ScalaComponent
      .builder[(Boolean, Boolean => Callback)]("PanelSummary")
      .render_PC {
        case ((expanded, onToggle), children) =>
          <.div(
            ^.cls := ("panel-summary".cls |+| (if (expanded)
                                                 "panel-toggle-on".cls
                                               else
                                                 "panel-toggle-off".cls)).show,
            ^.onClick --> onToggle(!expanded),
            children
          )
      }
      .build

    val Details = ScalaComponent
      .builder[Boolean]("PanelDetails")
      .render_PC {
        case (expanded, children) =>
          <.div(
            ^.cls := (if (expanded) "panel-details"
                      else "panel-details hidden"),
            children
          )
      }
      .build

    val Panel = ScalaComponent
      .builder[Unit]("Panel")
      .render_C(cs =>
        <.div(
          ^.cls := "panel",
          cs
        )
      )
      .build
  }


  val SideSheet = ScalaComponent
    .builder[Unit]("SideSheet")
    .render_C(cs =>
      <.div(
        ^.cls := "side-sheet",
        <.div(
          ^.cls := "side-sheet-content",
          cs
        )
      )
    )
    .build
}
