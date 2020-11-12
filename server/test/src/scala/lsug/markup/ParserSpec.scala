package lsug
package markup

import scala.util.matching.Regex

import cats.data._
import munit._

class ParserSpec extends ParserChecks {

  import Parser.{fail => pfail, _}

  checkFailure("fail", pfail, "any text")

  checkPure("return the value", 1, "any text")

  checkPattern("parse a single character", "[a-z]".r, "b")
  checkPatternFails("not parse an empty string", "[a-z]".r, "")

  checkEither("left succeeds", pure(1), pfail, "any text", 1, "any text")
  checkEither("right succeeds", pfail, pure(1), "any text", 1, "any text")
  checkEither(
    "prioritize the left result",
    pure(1),
    pure(2),
    "any text",
    1,
    "any text"
  )
  checkFailure("either", either(pfail, pfail), "any text")

  checkProduct(
    "two characters",
    pattern("a".r),
    pattern("b".r),
    "abc",
    "a",
    "b",
    "c"
  )
  checkProductFails("first character", pattern("a".r), pattern("b".r), "bb")
  checkProductFails("second character", pattern("a".r), pattern("b".r), "aa")

  check(
    "map - does not take characters",
    map(pure(1))(identity),
    "any text",
    1,
    "any text"
  )

  checkZeroOrMore("a single character", "a", List("a"), "")
  checkZeroOrMore("two characters", "aa", List("a", "a"), "")
  checkZeroOrMore("an empty string", "", Nil, "")
  checkZeroOrMore("the first characters", "aab", List("a", "a"), "b")

  checkOneOrMore("a single character", "a", NonEmptyList.of("a"), "")
  checkOneOrMore("two characters", "aa", NonEmptyList.of("a", "a"), "")
  checkOneOrMoreFails("an empty string", "")
  checkOneOrMoreFails("a mismatching string", "b")
}

trait ParserChecks extends FunSuite {

  import Parser._

  def check[A](
      name: String,
      pa: Parser[A],
      text: String,
      expected: A,
      expectedRest: String
  )(implicit loc: munit.Location): Unit = {
    val result = pa(text)

    test(s"$name - parsing succeeds") {
      result match {
        case Result.Fail => fail("parsing failed")
        case _           => ()
      }
    }

    val option = result match {
      case Result.Fail                 => None
      case Result.Success(value, rest) => Some((value, rest))
    }

    option.foreach {
      case (value, rest) =>
        test(s"$name - remaining text") {
          assertEquals(rest, expectedRest)
        }

        test(s"$name - value is correct") {
          assertEquals(value, expected)
        }
    }
  }

  def checkFailure[A](name: String, pa: Parser[A], text: String)(
      implicit loc: munit.Location
  ): Unit =
    test(s"$name - fails") {
      val result = pa(text)
      result match {
        case Result.Success(_, _) => fail("parsing succeeded")
        case Result.Fail          => ()
      }
    }

  def checkPure[A](name: String, value: A, text: String)(
      implicit loc: munit.Location
  ): Unit =
    check(s"pure - $name", pure(value), text, value, text)

  def checkPattern(name: String, regex: Regex, text: String)(
      implicit loc: munit.Location
  ): Unit =
    check(
      s"pattern - $name",
      pattern(regex),
      text,
      text.head.toString,
      text.tail
    )

  def checkPatternFails(name: String, regex: Regex, text: String)(
      implicit loc: munit.Location
  ): Unit =
    checkFailure(s"pattern - $name", pattern(regex), text)

  def checkZeroOrMore(
      name: String,
      text: String,
      value: List[String],
      rest: String
  )(implicit loc: munit.Location): Unit =
    check(s"zeroOrMore - $name", zeroOrMore(pattern("a".r)), text, value, rest)

  def checkOneOrMore(
      name: String,
      text: String,
      value: NonEmptyList[String],
      rest: String
  )(implicit loc: munit.Location): Unit =
    check(s"oneOrMore - $name", oneOrMore(pattern("a".r)), text, value, rest)

  def checkOneOrMoreFails(name: String, text: String)(
      implicit loc: munit.Location
  ): Unit =
    checkFailure(s"oneOrMore - $name", oneOrMore(pattern("a".r)), text)

  def checkEither[A](
      name: String,
      left: Parser[A],
      right: Parser[A],
      text: String,
      value: A,
      rest: String
  )(implicit loc: munit.Location): Unit =
    check(s"either - $name", either(left, right), text, value, rest)

  def checkProduct[A, B](
      name: String,
      left: Parser[A],
      right: Parser[B],
      text: String,
      leftValue: A,
      rightValue: B,
      rest: String
  )(implicit loc: munit.Location): Unit =
    check(
      s"product - $name",
      product(left, right),
      text,
      (leftValue, rightValue),
      rest
    )

  def checkProductFails[A, B](
      name: String,
      left: Parser[A],
      right: Parser[B],
      text: String
  )(implicit loc: munit.Location): Unit =
    checkFailure(s"product - $name", product(left, right), text)
}
