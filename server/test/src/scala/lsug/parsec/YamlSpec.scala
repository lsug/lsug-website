package lsug
package parsec

import munit._
import cats.implicits._

class YamlSpec extends FunSuite {

  implicit val parser = Yaml.yaml

  import lsug.yaml.Yaml._

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
    assertEquals(reply._1, Text.Source(s.count(_ === '\n'), 0))
  }

  test("empty yaml") {
    assertParseResult("---\n---\n", Obj.empty)
  }

  test("single key-value pair") {
    assertParseResult("---\nfoo: bar\n---\n", Obj(Map("foo" -> Str("bar"))))
  }

  test("multiple key-value pairs") {
    assertParseResult(
      """---
        |foo: bar
        |baz: fub
        |---
        |""".stripMargin,
      Obj(Map("foo" -> Str("bar"), "baz" -> Str("fub")))
    )
  }

  test("nested object") {
    assertParseResult(
      """---
        |foo:
        |  bar: baz
        |---
        |""".stripMargin,
      Obj(Map("foo" -> Obj(Map("bar" -> Str("baz")))))
    )
  }

  test("object list") {
    assertParseResult(
      """---
        |foo:
        |  - baz: 1
        |    bar: 2
        |---
        |""".stripMargin,
      Obj(
        Map(
          "foo" -> Arr(
            List(Obj(Map("baz" -> Str("1"), "bar" -> Str("2"))))
          )
        )
      )
    )
  }

  test("list") {
    assertParseResult(
      """---
        |foo:
        |  - baz
        |---
        |""".stripMargin,
      Obj(Map("foo" -> Arr(List(Str("baz")))))
    )
  }

  test("nested list") {
    val time = "2020-03-29T17:02:13"
    assertParseResult(
      s"""---
           |time: ${time}
           |cats:
           |  speakers:
           |    - kaiang
           |    - zainabali
           |---
           |""".stripMargin,
      Obj(
        Map(
          "time" -> Str(time),
          "cats" ->
            Obj(
              Map(
                "speakers" -> Arr(
                  List(Str("kaiang"), Str("zainabali"))
                )
              )
            )
        )
      )
    )
  }

}
