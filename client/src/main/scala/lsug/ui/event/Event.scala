package lsug.ui
package event

import lsug.{protocol => P}
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._
import cats._
import cats.implicits._
import Function.const

object Event {

  import common.{Speakers, MaterialIcon, modal, tabs, markup, TagBadge}
  import common.modal.control.ModalProps
  import common.tabs.TabProps

  val markupOpts = markup.Options(true)

  sealed trait Tab

  object Tab {
    case object About extends Tab
    case object Setup extends Tab
    case object Media extends Tab
    case object Material extends Tab

    val about: Tab = About
    val setup: Tab = Setup
    val media: Tab = Media
    val material: Tab = Material

    implicit val tabShow: Show[Tab] = Show.fromToString[Tab]
    implicit val tabEq: Eq[Tab] = Eq.fromUniversalEquals[Tab]

  }

  sealed trait Media

  object Media {
    case object Slides extends Media
    case object Video extends Media

    val slides: Media = Slides
    val video: Media = Video

    implicit val mediaShow: Show[Media] = Show.fromToString[Media]
    implicit val mediaEq: Eq[Media] = Eq.fromUniversalEquals[Media]
  }

  case class Props[S, I](
      event: P.Meetup.Event,
      speakers: Speakers,
      modalProps: ModalProps[S, I],
      modalId: Media => I,
      tabProps: TabProps[S, Tab]
  )

  def Event[S, I: Eq] = {

    ScalaComponent
      .builder[Props[S, I]]("Event")
      .render_P {
        case Props(
            P.Meetup.Event(
              event,
              desc,
              speakerIds,
              tags,
              material,
              setup,
              slides,
              recording,
              _
            ),
            speakers,
            modalProps,
            modalId,
            tabProps
            ) =>
          val existingTabs =
            List(
              Tab.about.some,
              setup.headOption.map(const(Tab.setup)),
              slides.orElse(recording).map(const(Tab.media)),
              material.headOption.map(const(Tab.material))
            ).mapFilter(identity)

          val makePanel = tabs.makeTabPanel[S, Tab](tabProps.currentTab) _
          val aboutPanel = makePanel(
            Tab.About,
            <.div(
              <.div(
                ^.cls := "abstract",
                desc.map(markup.Markup(_, markupOpts)).toTagMod
              ),
              <.ul(
                ^.cls := "tags",
                tags.map(t => <.li(TagBadge(t))).toTagMod
              )
            )
          )

          val setupPanel = makePanel(
            Tab.Setup,
            <.div(
              ^.cls := "setup",
              setup.map(markup.Markup(_, markupOpts)).toTagMod
            )
          )

          val materialPanel = makePanel(
            Tab.Material,
            <.div(
              ^.cls := "material",
              <.ol(material.map { m =>
                <.li(
                  <.a(
                    ^.href := m.location,
                    ^.target := "_blank",
                    MaterialIcon("unfold_more"),
                    <.span(m.text)
                  )
                )
              }.toTagMod)
            )
          )

          val mediaPanel = makePanel(
            Tab.Media,
            <.ol(
              ^.cls := Tab.media.show.toLowerCase,
              slides.map { link =>
                if (!link.openInNew) {
                  val id = modalId(Media.slides)
                  <.li(
                    modal
                      .control[S, I]
                      .apply(
                        modal.control.Props(
                          modalProps,
                          id,
                          "slides",
                          "description",
                          link.link.show
                        )
                      )
                  )
                } else {
                  <.li(
                    <.a(
                      ^.cls := "open-media",
                      ^.href := link.link.show,
                      ^.target := "_blank",
                      MaterialIcon("description"),
                      <.div(
                        <.span("slides"),
                        MaterialIcon("open_in_new")
                      )
                    )
                  )
                }
              },
              recording.map { recording =>
                val id = modalId(Media.video)
                <.li(
                  modal
                    .control[S, I]
                    .apply(
                      modal.control.Props(
                        modalProps,
                        id,
                        "video",
                        "video_library",
                        s"https://www.youtube.com/embed/${recording.show}?modestbranding=1"
                      )
                    )
                )
              }
            )
          )

          <.article(
            ^.cls := "event",
            <.header(
              <.h2(^.cls := "event-header small-heading", event)
            ),
            <.div(
              ^.cls := "speakers",
              speakerIds.map { id =>
                Speaker.Speaker(
                  speakers.get(id)
                )
              }.toTagMod
            ),
            tabs.makeTabs[S, Tab](tabProps)(
              existingTabs,
              tabProps.currentTab
            ),
            aboutPanel,
            mediaPanel,
            setupPanel,
            materialPanel
          )

      }
      .build
  }
}
