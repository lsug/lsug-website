package lsug
package data

import cats.data.{NonEmptyList => Nel}

import protocol._
import protocol.Venue.Id

object venues {
  val JustEat = Venue.Summary(
    id = new Id("justeat"),
    name = "Just Eat",
    address = Nel.of("Fleet Place House", "Fleet Place")
  )

  val Deliveroo = Venue.Summary(
    id = new Id("deliveroo"),
    name = "Deliveroo",
    address = Nel.of("1 Cousin Lane")
  )

  val all: Nel[Venue.Summary] = Nel.of(
    JustEat,
    Deliveroo
  )
}
