package lsug.ui
package event1

import lsug.{protocol => P}
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._
import cats._
import monocle.Lens
import cats.implicits._
import Function.const

object Item {

  import common.{Markup, MaterialIcon, modal}

  import common.tabs

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

  val Youtube =
    ScalaComponent
      .builder[P.Link]("Youtube")
      .render_P {
        case link =>
          <.iframe(
            ^.src := s"https://www.youtube.com/embed/${link.show}?modestbranding=1",
            ^.frameBorder := "0",
            ^.allowFullScreen := true
          )
      }
      .build

  val Item = {

    final class Backend($ : BackendScope[Props, Tab]) {

      def render(currentTab: Tab, props: Props): VdomNode = {
        props match {
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
              openModal,
              onOpen,
              onClose
              ) =>
            val existingTabs =
              List(
                Tab.about.some,
                setup.headOption.map(const(Tab.setup)),
                slides.orElse(recording).map(const(Tab.media)),
                material.headOption.map(const(Tab.material))
              ).mapFilter(identity)

            val makePanel = tabs.makeTabPanel(Lens.id[Tab], currentTab) _
            val aboutPanel = makePanel(
              Tab.About,
              <.div(
                <.div(
                  ^.cls := "abstract",
                  desc.zipWithIndex.map {
                    case (d, i) =>
                      Markup.withKey(i)(d)
                  }.toTagMod
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
                setup.zipWithIndex.map {
                  case (d, i) =>
                    Markup.withKey(i)(d)
                }.toTagMod
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

            // We should use props to control the modal
            // Problem: The id is different for events and for meetups
            // We should have a function in the item props from Media => I
            val mediaPanel = makePanel(
              Tab.Media,
              <.ol(
                ^.cls := Tab.media.show.toLowerCase,
                slides.map { link =>
                  if (!link.openInNew) {
                    <.li(
                      <.button(
                        ^.cls := "open-media",
                        ^.onClick --> onOpen(Media.slides),
                        MaterialIcon("description"),
                        <.span("slides")
                      ),
                      modal.Modal.withChildren(
                        <.div(
                          ^.cls := openModal.show.toLowerCase,
                          <.iframe(
                            ^.src := link.link.show,
                            ^.allowFullScreen := true
                          )
                        )
                      )(
                        openModal.map(_ === Media.slides).getOrElse(false),
                        onClose(Media.slides)
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
                  <.li(
                    <.button(
                      ^.cls := "open-media",
                      ^.onClick --> onOpen(Media.video),
                      MaterialIcon("video_library"),
                      <.span("video")
                    ),
                    modal.Modal.withChildren(
                      <.div(
                        ^.cls := openModal.show.toLowerCase,
                        Youtube(recording)
                      )
                    )(
                      openModal.map(_ === Media.video).getOrElse(false),
                      onClose(Media.video)
                    )
                  )
                }
              )
            )

            <.article(
              ^.cls := "item",
              <.header(
                <.h2(^.cls := "item-header", event)
              ),
              tabs.makeTabs($.modState, Lens.id[Tab], currentTab)(
                existingTabs,
                currentTab
              ),
              aboutPanel,
              mediaPanel,
              setupPanel,
              materialPanel,
              <.div(
                ^.cls := "speakers",
                speakerIds.map { id =>
                  Speaker.Speaker.withKey(id.show)(
                    speakers.get(id)
                  )
                }.toTagMod
              )
            )
        }
      }
    }

    ScalaComponent
      .builder[Props]("Item")
      .initialState[Tab](Tab.About)
      .renderBackend[Backend]
      .build
  }

  type Speakers = Map[P.Speaker.Id, P.Speaker]

  case class Props(
      item: P.Meetup.Event,
      speakers: Speakers,
      modal: Option[Media],
      onOpen: Media => Callback,
      onClose: Media => Callback
  )
}
