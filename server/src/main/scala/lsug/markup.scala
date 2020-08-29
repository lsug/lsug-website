package lsug
package markup

sealed trait Pollen

object Pollen {
  case class Contents(value: String) extends Pollen
  case class Tag(name: String, children: List[Pollen]) extends Pollen
}
