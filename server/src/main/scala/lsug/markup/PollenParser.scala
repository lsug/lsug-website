package lsug
package markup

import cats.data._
import cats.implicits._

import Parser._
import Pollen._

private object PollenParser {

  private val lozenge: Parser[Unit] = symbol("◊".r).void
  private val open: Parser[Unit] = symbol("{".r).void
  private val close: Parser[Unit] = symbol("}".r).void
  private val nameLetter: Parser[Char] = symbol("[a-z]".r)
  private val name: Parser[String] = oneOrMore(nameLetter).map(_.toList.mkString(""))

  private val letter: Parser[Char] = symbol("[^{}◊]".r)
  private val contents: Parser[Contents] = zeroOrMore(letter).map(_.mkString(""))
    .map(Contents)

  private def tag: Parser[Tag] = (
    lozenge *> name <* open,
    zeroOrMore(either(contents.map(a => a: Pollen), tag
      .map(a => a: Pollen)
    )) <* close
  ).mapN {
    case (name, children) => Tag(name, children)
  }

  def tags: Parser[NonEmptyList[Tag]] = oneOrMore(tag)
}
