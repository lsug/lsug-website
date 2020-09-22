package lsug
package markup

import monocle.macros.GenPrism

private sealed trait Pollen

private object Pollen {
  case class Contents(value: String) extends Pollen
  case class Tag(name: String, children: List[Pollen]) extends Pollen

  val _contents = GenPrism[Pollen, Contents]
  val _tag = GenPrism[Pollen, Tag]
}
