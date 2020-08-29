package lsug
package parsec

import munit.{Tag => _, _}
import cats.implicits._

class PollenSpec extends FunSuite {

  import Text._
  import lsug.markup.Pollen._
  import lsug.markup.Pollen
  implicit val parser: Parser[Pollen] = lsug.parsec.Pollen.tag


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

  test("empty command") {
    assertParseResult("◊foo{}", Tag("foo", Nil): Pollen)
  }

  test("command with contents") {
    assertParseResult("◊foo{bar}", Tag("foo", List(Contents("bar"))): Pollen)
  }

  test("command with tags") {
    assertParseResult("◊foo{◊bar{baz}}",
      Tag("foo", List(Tag("bar", List(Contents("baz"))))): Pollen)
  }

  test("command with tags and content") {
    assertParseResult("◊foo{qux◊bar{baz}}",
      Tag("foo", List(Contents("qux"), Tag("bar", List(Contents("baz"))))): Pollen)
  }
  test("command with multiple tags and content") {
    assertParseResult("◊foo{◊qux{baz}◊bar{baz}}",
      Tag("foo", List(Tag("qux", List(Contents("baz"))),
        Tag("bar", List(Contents("baz"))))): Pollen)
  }
}
