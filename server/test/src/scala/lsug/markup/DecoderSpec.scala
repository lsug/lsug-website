package lsug
package markup

import java.time._
import munit.{Tag => _, _}
import cats.implicits._
import cats.data._

class DecoderSpec extends FunSuite {

  import Pollen._
  import PollenDecoders._

  def assertSuccess[A, B](
    decoder: Decoder[A, B],
    input: A,
    expected: B): Unit = {
    assert(clue(decoder(input)) == Right(expected))
  }

  def assertFailure[A, B](
    decoder: Decoder[A, B],
    input: A,
    expected: Decoder.Failure): Unit = {
    assert(clue(decoder(input)) == Left(expected))
  }

  test("root tag") {
    assertSuccess(tag("root"),
      NonEmptyList.of(Tag("root", Nil)),
      Tag("root", Nil)
    )
  }

  test("wrong root tag") {
    assertFailure(tag("root"),
      NonEmptyList.of(Tag("foo", Nil)),
      Decoder.Failure(Decoder.Error.TagNotFound("root"), Nil)
    )
  }


  test("child tag") {
    assertSuccess(child("bar"),
      Tag("foo", List(Tag("bar", Nil))),
      Tag("bar", Nil)
    )
  }

  test("wrong child tag") {
    assertFailure(child("qux"),
      Tag("foo", List(Tag("bar", Nil))),
      Decoder.Failure(Decoder.Error.TagNotFound("qux"), Nil)
    )
  }

  test("nested child tag") {
    assertSuccess(child("bar") >>> child("baz") ,
      Tag("foo", List(Tag("bar", List(Tag("baz", Nil))))),
      Tag("baz", Nil)
    )
  }

  test("wrong nested child tag") {
    assertFailure(child("bar") >>> child("qux") ,
      Tag("foo", List(Tag("bar", List(Tag("baz", Nil))))),
      Decoder.Failure(Decoder.Error.TagNotFound("qux"), List("bar"))
    )
  }

  test("wrong parent of nested child tag") {
    assertFailure(child("bar") >>> child("qux") ,
      Tag("foo", List(Tag("cow", List(Tag("baz", Nil))))),
      Decoder.Failure(Decoder.Error.TagNotFound("bar"), Nil)
    )
  }

  test("contents") {
    assertSuccess(contents,
      Tag("foo", List(Contents("bar"))),
      "bar"
    )
  }

  test("nel contents") {
    assertSuccess(contents >>> nel,
      Tag("foo", List(Contents("bar,baz"))),
      NonEmptyList.of("bar", "baz")
    )
  }

  test("date contents") {
    assertSuccess(contents >>> date,
      Tag("foo", List(Contents("2000-11-10"))),
      LocalDate.of(2000, 11, 10)
    )
  }

  test("time range") {
    assertSuccess(contents >>> timeRange,
      Tag("foo", List(Contents("19:30-20:45"))),
      LocalTime.of(19, 30) -> LocalTime.of(20, 45)
    )
  }

  test("optional tag") {
    assertSuccess(child("bar").optional.composeF(contents),
      Tag("foo", Nil),
      None
    )
  }
}
