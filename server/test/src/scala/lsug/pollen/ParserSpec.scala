package lsug
package pollen

import munit.{Tag => _, _}
import cats.implicits._
import cats.data._

class ParserSpec extends FunSuite {

  import Text._
  import Pollen._
  implicit val parser: Parser[NonEmptyList[Pollen.Tag]] = PollenParsers.tags


  def assertParseResult[A](s: String, a: A)(
      implicit parser: Text.Parser[A],
      loc: Location
  ): Unit = {
    val reply = parser.compile
      .runA(
        Parse.State(
          s
        )
      )
      .run(
        Text.Source()
      )
      .value
    assert(clue(reply)._2.result == Right(a))
  }

  def assertParseResultF[A](s: String, a: A)(
      implicit parser: Text.Parser[NonEmptyList[A]],
      loc: Location
  ): Unit = {
    assertParseResult(s, NonEmptyList.of(a))
  }

  test("empty command") {
    assertParseResultF("◊foo{}", Tag("foo", Nil))
  }

  test("command with contents") {
    assertParseResultF("◊foo{bar}", Tag("foo", List(Contents("bar"))))
  }

  test("command with tags") {
    assertParseResultF("◊foo{◊bar{baz}}",
      Tag("foo", List(Tag("bar", List(Contents("baz"))))))
  }

  test("command with tags and content") {
    assertParseResultF("◊foo{qux◊bar{baz}}",
      Tag("foo", List(Contents("qux"), Tag("bar", List(Contents("baz"))))))
  }
  test("command with multiple tags and content") {
    assertParseResultF("◊foo{◊qux{baz}◊bar{baz}}",
      Tag("foo", List(Tag("qux", List(Contents("baz"))),
        Tag("bar", List(Contents("baz"))))))
  }

  test("multiple tags") {
    assertParseResult("◊foo{}◊bar{}",
      NonEmptyList.of(Tag("foo", Nil), Tag("bar", Nil))
    )
  }

  test("newlines") {
    assertParseResult("◊foo{}\n◊bar{}",
      NonEmptyList.of(Tag("foo", Nil), Tag("bar", Nil))
    )
  }
}
