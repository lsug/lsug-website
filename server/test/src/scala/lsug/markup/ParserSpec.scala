package lsug
package markup

import scala.util.matching.Regex

import cats._
import cats.data._
import cats.implicits._
import cats.laws.discipline.FunctorTests
import munit.DisciplineSuite
import org.scalacheck.{Arbitrary, Gen}

class ParserSpec extends ParserChecks with DisciplineSuite {

  import Parser.{fail => pfail, _}

  checkFailure[Unit](pfail, "any text").build("fail")

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
  checkFailure(either[Unit](pfail, pfail), "any text").build("either")

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

  check(map(pure(1))(identity), "any text", 1, "any text")
    .label("map")
    .build("does not take characters")

  checkZeroOrMore("a single character", "a", List("a"), "")
  checkZeroOrMore("two characters", "aa", List("a", "a"), "")
  checkZeroOrMore("an empty string", "", Nil, "")
  checkZeroOrMore("the first characters", "aab", List("a", "a"), "b")

  checkOneOrMore("a single character", "a", NonEmptyList.of("a"), "")
  checkOneOrMore("two characters", "aa", NonEmptyList.of("a", "a"), "")
  checkOneOrMoreFails("an empty string", "")
  checkOneOrMoreFails("a mismatching string", "b")

  checkAll("Result.FunctorLaws", FunctorTests[Result].functor[Int, Int, String])
  checkAll("Parser.FunctorLaws", FunctorTests[Parser].functor[Int, Int, String])
}

trait ParserChecks extends LsugSuite {

  import Parser._

  def assertRest(rest: String, expected: String)(
      implicit loc: munit.Location
  ): Unit = {
    assert(clue(rest) === clue(expected), "The rest of the text was incorrect")
  }

  def check[A](
      pa: Parser[A],
      text: String,
      expected: A,
      expectedRest: String
  )(implicit loc: munit.Location): TestBuilder =
    (builder {

      val result = pa(text) match {
        case Result.Fail                 => None
        case Result.Success(value, rest) => Some((value, rest))
      }

      assert(result.isDefined, "parsing failed")

      result.foreach {
        case (value, rest) =>
          assertRest(rest, expectedRest)
          assertEquals(value, expected)
      }
    }).label("success")

  def checkFailure[A: Eq](pa: Parser[A], text: String)(
      implicit loc: munit.Location
  ): TestBuilder =
    (builder {
      val result = pa(text)
      assert(clue(result) === Result.Fail, "parsing succeeded")
    }).label("fail")

  def checkPure[A](name: String, value: A, text: String)(
      implicit loc: munit.Location
  ): Unit =
    check(pure(value), text, value, text)
      .label("pure")
      .build(name)

  def checkPattern(name: String, regex: Regex, text: String)(
      implicit loc: munit.Location
  ): Unit =
    check(
      pattern(regex),
      text,
      text.head.toString,
      text.tail
    ).label("pattern").build(name)

  def checkPatternFails(name: String, regex: Regex, text: String)(
      implicit loc: munit.Location
  ): Unit =
    checkFailure(pattern(regex), text)
      .label("pattern")
      .build(name)

  def checkZeroOrMore(
      name: String,
      text: String,
      value: List[String],
      rest: String
  )(implicit loc: munit.Location): Unit =
    check(zeroOrMore(pattern("a".r)), text, value, rest)
      .label("zeroOrMore")
      .build(name)

  def checkOneOrMore(
      name: String,
      text: String,
      value: NonEmptyList[String],
      rest: String
  )(implicit loc: munit.Location): Unit =
    check(oneOrMore(pattern("a".r)), text, value, rest)
      .label("oneOrMore")
      .build(name)

  def checkOneOrMoreFails(name: String, text: String)(
      implicit loc: munit.Location
  ): Unit =
    checkFailure(oneOrMore(pattern("a".r)), text)
      .label("oneOrMore")
      .build(name)

  def checkEither[A](
      name: String,
      left: Parser[A],
      right: Parser[A],
      text: String,
      value: A,
      rest: String
  )(implicit loc: munit.Location): Unit =
    check(either(left, right), text, value, rest)
      .label("either")
      .build(name)

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
      product(left, right),
      text,
      (leftValue, rightValue),
      rest
    ).label("product")
      .build(name)

  def checkProductFails[A: Eq, B: Eq](
      name: String,
      left: Parser[A],
      right: Parser[B],
      text: String
  )(implicit loc: munit.Location): Unit =
    checkFailure(product(left, right), text)
      .label("product")
      .build(name)

  implicit def arbResult[T](implicit a: Arbitrary[T]): Arbitrary[Result[T]] =
    Arbitrary(
      Gen.oneOf(
        Gen.const(Result.Fail),
        for {
          e <- Arbitrary.arbitrary[T]
          s <- Arbitrary.arbitrary[String]
        } yield Result.Success[T](e, s)
      )
    )

  implicit def parserEq[A: Eq]: Eq[Parser[A]] =
    Eq.fromUniversalEquals[Parser[A]]

  implicit lazy val arbRegex: Arbitrary[Regex] = Arbitrary {
    val lsugRegex: List[Regex] =
      List("◊".r, "\\{".r, "\\}".r, "[a-z]+".r, "[^◊}]+".r)
    Gen.oneOf(lsugRegex)
  }

  implicit def arbParser[T](implicit a: Arbitrary[T]): Arbitrary[Parser[T]] =
    Arbitrary {
      Gen.oneOf(
        Gen.const(Parser.Fail),
        for {
          e <- Arbitrary.arbitrary[T]
        } yield Parser.Pure(e),
        for {
          r <- Arbitrary.arbitrary[Regex]
        } yield Parser.Pattern(r)
      )
    }
}
