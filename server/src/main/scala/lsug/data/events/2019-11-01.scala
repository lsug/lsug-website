package lsug
package data
package events

import java.time.{LocalDate, LocalDateTime}
import protocol.Markup
import lsug.{protocol => p}
import cats.implicits._
import cats.data.{NonEmptyList => Nel}
import java.time.LocalTime

object E2019_11_01 {

  val vanLaarhoven = Item(
    name = "Lessons learnt with van Laarhoven lenses",
    speakers = Nel.of(people.JoeWarren),
    tags = List(tags.optics, tags.functional),
    start = LocalTime.of(19, 0),
    end = LocalTime.of(20, 0),
    description = Nel.of(
      Markup.Paragraph(
        Nel.of(
          Markup.Text.Plain(
            """Sometimes called “The JQuery of DataTypes”; Lenses and other Optics
   | provide a clean functional interface to the common task of modifying 
   | complex data structures. Brushing over any practical use cases, this talk
   | takes a magnifying glass to one particular Optics representation; van
   | Laarhoven Lenses.""".stripMargin
          )
        )
      )
    )
  )

  val fp = Item(
    name = "FP; The Good, the Bad and the Ugly",
    speakers = Nel.of(people.DanielaSfregola),
    tags = List(tags.functional),
    start = LocalTime.of(20, 0),
    end = LocalTime.of(21, 0),
    description = Nel.of(
      Markup.Paragraph(
        Nel.of(
          Markup.Text.Plain(
            """You are about to fall in love with Functional Programming, if
    | not already. You are going to learn the good parts that are going to make
    | your daily life easier.""".stripMargin
          )
        )
      ),
      Markup.Paragraph(
        Nel.of(
          Markup.Text.Plain("""But since nobody is perfect """),
          Markup.Text.Styled.Strong(Nel.of(Markup.Text.Plain("not even FP"))),
          Markup.Text.Plain(
            """, you are also going to see its bad and ugly
    | parts, and you'll discover how to deal with them: from learning challenges
    | to performance issues on the JVM.""".stripMargin
          )
        )
      )
    )
  )

  val event = Event(
    id = new p.Event.Id("2019-11-01"),
    meetup = new p.Event.Meetup.Event.Id("260846910"),
    location = venues.Deliveroo.leftIor,
    hosts = Nel.of(people.ZainabAli),
    date = LocalDate.of(2019, 11, 1),
    start = LocalTime.of(19, 0),
    end = LocalTime.of(21, 0),
    items = Nel.of(vanLaarhoven, fp)
  )
}
