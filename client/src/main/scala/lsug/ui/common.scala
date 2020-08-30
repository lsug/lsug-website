package lsug
package ui

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import cats._
import cats.data._
import cats.implicits._
import lsug.{protocol => P}
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._
import org.scalajs.dom.html
import lsug.ui.implicits._
import japgolly.scalajs.react.CatsReact._

object common {

  val Spinner = ScalaComponent
    .builder[Unit]("Spinner")
    .render_(<.div(^.cls := "spinner"))
    .build

  val Banner = ScalaComponent
    .builder[String]("Banner")
    .render_P { asset =>
      <.div(
        ^.cls := "banner",
        <.img(^.src := s"/assets/${asset}", ^.alt := "")
      )
    }
    .configure(Reusability.shouldComponentUpdate)
    .build

  val PersonBadge = ScalaComponent
    .builder[Option[P.Asset]]("PersonBadge")
    .render_P(pic =>
      <.div(
        ^.cls := "person-badge",
        pic
          .map(asset => <.img(^.src := asset.show))
          .getOrElse(
            MaterialIcon("person")
          )
      )
    )
    .build

  val MaterialIcon = ScalaComponent
    .builder[String]("MaterialIcon")
    .render_P(<.span(^.cls := "material-icons", ^.aria.hidden := true, _))
    .configure(Reusability.shouldComponentUpdate)
    .build

  object markup {

    case class Options(link: Boolean)

    private def toTagMod(markup: P.Markup.Text, options: Options): TagMod = {
      markup match {
        case P.Markup.Text.Plain(s) => s
        case P.Markup.Text.Styled.Strong(text) =>
          <.strong(text.map((toTagMod(_, options))).toList.toTagMod)
        case P.Markup.Text.Styled.Code(code) =>
          <.pre(<.code(code))
        case P.Markup.Text.Link(text, loc) =>
          if (options.link) {
            <.a(^.href := loc, text)
          } else {
            <.em(^.cls := "link", text)
          }
      }
    }

    val Markup = ScalaComponent
      .builder[(P.Markup, Options)]("Markup")
      .render_P {
        case (P.Markup.Paragraph(text), options) =>
          <.p(text.map(toTagMod(_, options)).toList.toTagMod)
        case m =>
          println(m)
          ???
      }
      .build
  }

  //TODO: Need props for header
  val Markup = {

    def renderText(markup: P.Markup.Text): TagMod = {
      markup match {
        case P.Markup.Text.Plain(s) => s
        case P.Markup.Text.Styled.Strong(text) =>
          <.strong(text.map(renderText).toList.toTagMod)
        case P.Markup.Text.Styled.Code(code) =>
          <.code(code)
        case P.Markup.Text.Link(text, loc) =>
          <.a(^.href := loc, text)
      }
    }

    ScalaComponent
      .builder[P.Markup]("Markup")
      .render_P {
        case P.Markup.Paragraph(text) =>
          <.p(text.map(renderText).toList.toTagMod)
        case m =>
          println(m)
          ???
      }
      .build

  }

  object panel {

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

  object sidesheet {

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

  object tabs {

    case class TabProps(
        label: String,
        selected: Boolean,
        onSelect: Callback
    )

    val Tab = ScalaComponent
      .builder[(String, Boolean, Callback)]("Tab")
      .render_PC {
        case ((label, selected, onSelect), children) =>
          <.button(
            ^.role := "tab",
            ^.aria.selected := selected,
            ^.aria.controls := label,
            ^.onClick --> onSelect,
            children
          )
      }
      .build

    case class TabsProps(
        minWidth: Int,
        tabs: NonEmptyList[Tab.type]
    )

    val Tabs = {

      final class Backend($ : BackendScope[Int, Int]) {

        private val ref = Ref[html.Div]

        def render(props: Int, state: Int, children: PropsChildren) = {
          val width = state
          <.div(
            ^.cls := "tabs",
            <.div(
              <.div.withRef(ref)(
                ^.role := "tablist",
                children
              ),
              <.span(
                ^.cls := "tab-indicator",
                ^.width := s"${width.show}px",
                ^.left := s"${props * width}px"
              )
            )
          ),
        }

        def init: Callback =
          ref.get.flatMap { el =>
            val width = el.firstElementChild.domAsHtml.offsetWidth
            $.setState(width.toInt)
          }.void
      }

      ScalaComponent
        .builder[Int]("Tabs")
        .initialState[Int](0)
        .renderBackendWithChildren[Backend]
        .componentDidMount(_.backend.init)
        .build
    }

    val TabPanel = ScalaComponent
      .builder[(String, Boolean)]("TabPanel")
      .render_PC {
        case ((label, selected), children) =>
          <.div(
            ^.id := label,
            ^.role := "tabpanel",
            ^.hidden := !selected,
            children
          )
      }
      .build

  }

  object modal {

    val Overlay = ScalaComponent
      .builder[Boolean]("ModalOverlay")
      .render_P(open =>
        <.div(
          ^.cls := (if (open) "modal-overlay" else "hidden modal-overlay")
        )
      )
      .build

    val Modal = ScalaComponent
      .builder[(Boolean, Callback)]("Modal")
      .render_PC {
        case ((open, onClose), children) =>
          <.div(
            ^.cls := "modal",
            Overlay(open),
            <.div(
              ^.role := "dialog",
              ^.cls := (if (open) "open" else "hidden"),
              <.div(
                ^.cls := "header",
                <.button(
                  ^.cls := "close",
                  MaterialIcon("close"),
                  ^.onClick --> onClose
                )
              ),
              // delay loading of children
              children.when(open)
            )
          )
      }
      .build
  }

  val NavBar = ScalaComponent
    .builder[Unit]("NavBar")
    .renderStatic(
      <.nav(
        <.div(
          <.a(
            ^.cls := "abbrev-name",
            MaterialIcon("home"),
            <.span("LSUG"),
            ^.href := "/")
        ),
        <.div(
          <.a("About"),
          <.a("Sponsors", ^.href :="/sponsors")
        )
      )
    )
    .build

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

  val ProfilePicture =
    ScalaComponent
      .builder[Option[P.Speaker.Profile]]("ProfilePicture")
      .render_P { profile =>
        <.div(
          ^.cls := "profile-picture",
          (for {
            P.Speaker.Profile(_, _, asset) <- profile
          } yield asset
            .map { pic => <.img(^.src := pic.show) }
            .getOrElse[TagMod](MaterialIcon("person"))).getOrElse(
            <.div(^.cls := "placeholder")
          )
        )
      }
      .configure(Reusability.shouldComponentUpdate)
      .build

  val Footer = ScalaComponent
    .builder[LocalDate]("Footer")
    .render_P(now =>
      <.div(
        ^.cls := "footer",
        Disclaimer(("12325025", now))
      )
    )
    .configure(Reusability.shouldComponentUpdate)
    .build

}
