package lsug
package markup

import cats.data._
import cats.implicits._

import Parser._
import Pollen._

private object PollenParser {

  private val lozenge: Parser[Unit] = pattern("◊".r).void
  private val open: Parser[Unit] = pattern("\\{".r).void
  private val close: Parser[Unit] = pattern("\\}".r).void
  private val name: Parser[String] = pattern("[a-z]+".r)
  private val tagname: Parser[String] = lozenge *> name <* open
  private val whitespace: Parser[Unit] = pattern("\\s".r).void
  private val contents: Parser[Contents] = pattern("[^◊}]+".r).map(Contents)

  def tag: Parser[Tag] =
    map(product(
    tagname,
     children <* close
    )) { case (name, children) => Tag(name, children)}

  private def pollen: Parser[Pollen] = either(contents, tag)
  private def children: Parser[List[Pollen]] = zeroOrMore(pollen)
    .map {_.filter {
      case Contents(text) => !text.trim.isEmpty
      case _ => true
   }}

  def tags: Parser[NonEmptyList[Tag]] =
    oneOrMore(zeroOrMore(whitespace) *> tag <* zeroOrMore(whitespace))
}
