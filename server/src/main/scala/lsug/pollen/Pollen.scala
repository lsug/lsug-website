package lsug
package pollen

import scala.util.Try
import java.time._
import cats._
import cats.data._
import cats.implicits._
import monocle._
import monocle.macros.{GenLens, GenPrism}
import monocle.function.At.at
import monocle.std.option.{some => _some}

sealed trait Pollen

object Pollen {
  case class Contents(value: String) extends Pollen
  case class Tag(name: String, children: List[Pollen]) extends Pollen

  val _contents = GenPrism[Pollen, Contents]
  val _tag = GenPrism[Pollen, Tag]
}
