package lsug
package markup

import munit.{Tag => _, _}
import cats.data._

class PollenParserSpec extends FunSuite {

  import Pollen._

  def assertParseResult[A](s: String, a: NonEmptyList[Tag]): Unit = {
    val reply = PollenParser.tags(s).toEither
    assert(clue(reply) == Right(a))
  }

  def assertParseResult[A](s: String, a: Tag): Unit = {
    assertParseResult(s, NonEmptyList.of(a))
  }

  test("empty command") {
    assertParseResult("◊foo{}", Tag("foo", Nil))
  }

  test("command with contents") {
    assertParseResult("◊foo{bar}", Tag("foo", List(Contents("bar"))))
  }

  test("command with tags") {
    assertParseResult("◊foo{◊bar{baz}}",
      Tag("foo", List(Tag("bar", List(Contents("baz"))))))
  }

  test("command with tags and content") {
    assertParseResult("◊foo{qux◊bar{baz}}",
      Tag("foo", List(Contents("qux"), Tag("bar", List(Contents("baz"))))))
  }
  test("command with multiple tags and content") {
    assertParseResult("◊foo{◊qux{baz}◊bar{baz}}",
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
