package lsug.ui
package event1

import lsug.{protocol => P}
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._
import cats._
import cats.implicits._
import Function.const

object Item {

  import common.{
    Markup,
    MaterialIcon,
    modal,
  }

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

      def tabId(event: String, tab: Tab): String = {
        val eventId = event.replaceAll("\\s", "-")
        s"${eventId}-${tab.show}"
      }

      def MediaTab(
          event: String,
          onOpen: Media => Callback,
          openModal: Option[Media],
          onClose: Media => Callback,
          recording: Option[P.Link],
          slides: Option[P.Meetup.Media]
      )(currTab: Tab) =
        tabs.TabPanel.withChildren(
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
        )(tabId(event, Tab.media), Tab.media === currTab)

      ScalaComponent
        .builder[Props]("Item")
        .render_P {
          case Props(
              tab,
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
              onToggle,
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

            <.article(
              ^.cls := "item",
              <.header(
                <.h2(^.cls := "item-header", event)
              ),
              tabs.Tabs.withChildren(
                existingTabs.map { t =>
                  tabs.Tab
                    .withKey(t.show)
                    .withChildren(
                      <.span(t.show)
                    )((tabId(event, t), tab === t, onToggle(t)))
                    .vdomElement
                }: _*
              )(existingTabs.indexOf(tab)),
              tabs.TabPanel.withChildren(
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
              )(tabId(event, Tab.about), tab === Tab.about),
              tabs.TabPanel.withChildren(
                <.div(
                  ^.cls := "setup",
                  setup.zipWithIndex.map {
                    case (d, i) =>
                      Markup.withKey(i)(d)
                  }.toTagMod
                )
              )(tabId(event, Tab.setup), tab === Tab.setup),
              tabs.TabPanel.withChildren(
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
              )(tabId(event, Tab.material), tab === Tab.material),
              MediaTab(
                event,
                onOpen,
                openModal,
                onClose,
                recording,
                slides
              )(tab),
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
        .build
    }

  type Speakers = Map[P.Speaker.Id, P.Speaker]

    case class Props(
        tab: Tab,
        item: P.Meetup.Event,
        onToggle: Tab => Callback,
        speakers: Speakers,
        modal: Option[Media],
        onOpen: Media => Callback,
        onClose: Media => Callback
    )
}
