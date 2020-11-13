package lsug
package markup

class PollenParserSpec extends LsugSuite {

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
    List(Tag("foo", Nil), Tag("bar", Nil))
  )
  checkTags(
    "spaces between",
    "◊foo{}\n ◊bar{}",
    List(Tag("foo", Nil), Tag("bar", Nil))
  )

  checkTags(
    "spaces before",
    "\n ◊foo{}◊bar{}",
    List(Tag("foo", Nil), Tag("bar", Nil))
  )

  checkTags(
    "spaces after",
    "◊foo{}◊bar{}\n ",
    List(Tag("foo", Nil), Tag("bar", Nil))
  )

  checkTags(
    "spaces inside",
    "◊foo{\n}◊bar{}\n ",
    List(Tag("foo", Nil), Tag("bar", Nil))
  )

  checkTags(
    "spaces around",
    "\n ◊foo{}\n ◊bar{}\n ",
    List(Tag("foo", Nil), Tag("bar", Nil))
  )

  def checkTags(name: String, text: String, expected: List[Pollen]): Unit = {
    (builder {
      val result = PollenParser.pollens(text).toEither
      assert(result.isRight, "parsing failed")
      result.foreach { tags => assert(clue(tags) == expected) }
    }).label("success").build(name)
  }

  def check(name: String, text: String, expected: Tag): Unit = {
    checkTags(name, text, List(expected))
  }
}
