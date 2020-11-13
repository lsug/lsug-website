package lsug
package markup

import cats._
import monocle.macros.GenPrism

private sealed trait Pollen

private object Pollen {
  case class Contents(value: String) extends Pollen
  case class Tag(name: String, children: List[Pollen]) extends Pollen

  val _contents = GenPrism[Pollen, Contents]
  val _tag = GenPrism[Pollen, Tag]

  implicit val pollenEq: Eq[Pollen] = Eq.fromUniversalEquals
  implicit val pollenTagEq: Eq[Tag] = Eq.fromUniversalEquals
  implicit val pollenTagShow: Show[Tag] = Show.fromToString
}
