package lsug
package ui

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._

object about {

  private val SubHeading = ScalaComponent
    .builder[String]("SubHeading")
    .render_P { text =>
      <.h2(
        ^.cls := "small-heading",
        text
      )
    }
    .build

  private val Action = ScalaComponent
    .builder[(String, String)]("Action")
    .render_P {
      case (text, location) =>
        <.div(^.cls := "button-container", <.a(^.href := location, text))
    }
    .build

  val About = ScalaComponent.builder
    .static("About") {
      <.main(
        ^.cls := "about-page",
        <.div(
          <.h1(
            ^.cls := "small-heading",
            "Welcome to the London Scala User Group"
          ),
          <.p(
            "We are a community interested in the Scala programming language.  Whether you’re a Scala enthusiast or completely new to it; if you’re interested in Scala, this is ",
            <.span(^.cls := "em", "your"),
            " user group."
          ),
          <.p(
            "We host talks, workshops and open source hackdays.  Join us on ",
            <.a(
              "Meetup",
              ^.href := "https://www.meetup.com/london-scala",
              ^.cls := "external"
            ),
            " to hear about future events."
          ),
          Action(
            ("Join us", "https://www.meetup.com/london-scala/?action=join")
          ),
          SubHeading("Find your bridge into Scala"),
          <.p(
            "We partner with ",
            <.a(
              "ScalaBridge London",
              ^.cls := "external",
              ^.href := "https://www.scalabridgelondon.org/"
            ),
            ", a community for people who are underrepresented in technology to learn Scala or improve their skill in the language."
          ),
          Action(
            ("Find out more", "https://www.scalabridgelondon.org/students/")
          ),
          SubHeading("Speak"),
          <.p(
            "Do you have a talk you want to give, but are not sure how to give it? There’s no friendlier place to present than with us.  If it’s you’re first time speaking, we’re hear to help.  We’ll give informal coaching from a seasoned toastmaster, or casual chats from other experienced speakers."
          ),
          Action(("Submit a proposal", "https://forms.gle/Auyyodhp4h7mXLseA")),
          SubHeading("Get involved"),
          <.p(
            "We welcome volunteers.  Help us organize our upcoming meetups, find new speakers and guide new contributors."
          ),
          Action(("Volunteer", "https://forms.gle/J1pJQCD7AeVzYZhJ8")),
          SubHeading("Code of Conduct"),
          <.p(
            "Our organizers are committed to providing a friendly, safe and welcoming environment to everyone. We ask that the community adheres to the ",
            <.a(
              ^.cls := "external",
              ^.href := "https://www.scala-lang.org/conduct/",
              "Scala Code of Conduct"
            ),
            "."
          )
        )
      )
    }
    .build
}
