package lsug
package markup

import munit.{Tag => _, _}
import cats.data._

class PollenParserSpec extends FunSuite {

  import Pollen._

  check("no contents", "◊foo{}", Tag("foo", Nil))
  check("contents", "◊foo{bar}", Tag("foo", List(Contents("bar"))))
  check(
    "containing tags",
    "◊foo{◊bar{baz}}",
    Tag("foo", List(Tag("bar", List(Contents("baz")))))
  )
  check(
    "containing tags and contents",
    "◊foo{qux◊bar{baz}}",
    Tag("foo", List(Contents("qux"), Tag("bar", List(Contents("baz")))))
  )
  check(
    "containing multiple tags and content",
    "◊foo{◊qux{baz}◊bar{baz}}",
    Tag(
      "foo",
      List(Tag("qux", List(Contents("baz"))), Tag("bar", List(Contents("baz"))))
    )
  )
  checkTags(
    "multiple tags",
    "◊foo{}◊bar{}",
    NonEmptyList.of(Tag("foo", Nil), Tag("bar", Nil))
  )
  checkTags(
    "spaces between",
    "◊foo{}\n ◊bar{}",
    NonEmptyList.of(Tag("foo", Nil), Tag("bar", Nil))
  )

  checkTags(
    "spaces before",
    "\n ◊foo{}◊bar{}",
    NonEmptyList.of(Tag("foo", Nil), Tag("bar", Nil))
  )

  checkTags(
    "spaces after",
    "◊foo{}◊bar{}\n ",
    NonEmptyList.of(Tag("foo", Nil), Tag("bar", Nil))
  )

  checkTags(
    "spaces inside",
    "◊foo{\n}◊bar{}\n ",
    NonEmptyList.of(Tag("foo", Nil), Tag("bar", Nil))
  )

  checkTags(
    "spaces around",
    "\n ◊foo{}\n ◊bar{}\n ",
    NonEmptyList.of(Tag("foo", Nil), Tag("bar", Nil))
  )

  def checkTags(
      name: String,
      text: String,
      expected: NonEmptyList[Tag]
  ): Unit = {
    val result = PollenParser.tags(text).toEither
    test(s"$name - success") {
      assertEquals(result.isRight, true)
    }

    result.foreach { tags =>
      test(s"$name - correct value") {
        assert(clue(tags) == expected)
      }
    }
  }

  def check(name: String, text: String, expected: Tag): Unit = {
    checkTags(name, text, NonEmptyList.of(expected))
  }

}
