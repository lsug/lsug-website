package lsug
package markup

import cats._
import cats.implicits._

class DecoderSpec extends LsugSuite {

  import Pollen._
  import Decoder._

  def assertOutput[A](result: A, expected: A)(implicit loc: munit.Location): Unit = {
    assert(clue(result) == clue(expected), "Result of decoding is incorrect")
  }

  def assertEither[A](
    decoder: Decoder[A],
    input: List[Pollen],
    expected: Either[DecoderError, A])(implicit loc: munit.Location): TestBuilder = {
    val result = decoder(input)
    expected match {
      case Right(expected) => (builder {
          assert(clue(result.isRight), "Failed to decode")
          result.foreach(assertOutput(_, expected))
        }).label("success")
      case Left(expected) => builder({
        assert(clue(result.isLeft), "Decoding succeeded")
        result.swap.foreach(assertOutput(_, expected))
      }).label("fail")
    }
  }

  def checkText(name: String)(input: List[Pollen], output: String)(implicit loc: munit.Location): Unit = {
    assertEither(text, input, Right(output))
      .label("text")
      .build(name)
  }

  def checkTextFails(name: String)(input: List[Pollen], expected: DecoderError)(implicit loc: munit.Location): Unit = {
      assertEither(text, input, Left(expected))
        .label("text")
    .build(name)
  }

  def checkChildren(name: String)(tagname: String, input: List[Pollen], output: List[Tag]): Unit = {
    assertEither(children(tagname), input, Right(output))
      .label("children")
      .build(name)
  }

  def checkChild(name: String)(tagname: String, input: List[Pollen], output: Tag): Unit = {
    assertEither(child(tagname), input, Right(output))
      .label("child")
    .build(name)
  }

  def checkChildFails(name: String)(tagname: String, input: List[Pollen], expected: DecoderError)(implicit loc: munit.Location): Unit = {
      assertEither(child(tagname),
        input,
        Left(expected))
        .label("child")
    .build(name)
  }

  def checkAndThen[A](name: String)(first: Decoder[Tag], second: Decoder[A],
    input: List[Pollen],
    output: A): Unit = {
    assertEither(first.andThen(second), input, Right(output))
      .label("andThen")
      .build(name)
  }

  def checkAndThenTraverse[F[_]: Traverse, A](name: String)(first: Decoder[F[Tag]],
    second: Decoder[A],
    input: List[Pollen],
    output: F[A]): Unit = {
    assertEither(first.andThenTraverse(second), input, Right(output))
      .label("andThenTraverse")
      .build(name)
  }

  def checkAndThenTraverseFails[F[_]: Traverse, A](name: String)(first: Decoder[F[Tag]],
    second: Decoder[A],
    input: List[Pollen],
    output: DecoderError): Unit = {
    assertEither(first.andThenTraverse(second), input, Left(output))
      .label("andThenTraverse")
      .build(name)
  }

  checkText("single contents")(List(Contents("hello")), "hello")
  checkText("multiple contents")(List(Contents("hello"), Contents("world")), "helloworld")
  checkText("no contents")(Nil, "")
  checkTextFails("tag")(List(Tag("anything", Nil)), DecoderError.UnexpectedTag("anything"))
  checkTextFails("tag and contents")(List(Tag("anything", Nil), Contents("hello")), DecoderError.UnexpectedTag("anything"))

  checkChildren("tag")("hello", List(Tag("hello", Nil)), List(Tag("hello", Nil)))
  checkChildren("no tags")("hello", Nil, Nil)
  checkChildren("other tags")("hello", List(Tag("hello", Nil), Tag("world", Nil)), List(Tag("hello", Nil)))
  checkChildren("other tags")("hello", List(Tag("hello", Nil), Tag("world", Nil)), List(Tag("hello", Nil)))
  checkChildren("contents ignored")("hello", List(Tag("hello", Nil), Contents("world")), List(Tag("hello", Nil)))

  checkChild("tag")("hello", List(Tag("hello", Nil)), Tag("hello", Nil))
  checkChildFails("multiple tags")("hello", List(Tag("hello", Nil), Tag("hello", Nil)), DecoderError.MultipleTagsFound("hello", 2))
  checkChildFails("no tags")("hello", Nil, DecoderError.TagNotFound("hello"))

  // // a nested tag
  checkAndThen("tag containing text")(
      child("hello"), text,
      List(Tag("hello", List(Contents("bar")))),
      "bar"
    )

  checkAndThenTraverse("children containing text")(
    children("hello"), text,
    List(Tag("hello", List(Contents("bar"))),
      Tag("hello", List(Contents("baz")))
    ),
    List("bar", "baz")
  )

  checkAndThenTraverseFails("unexpected tag")(
    children("hello"),
    text,
    List(Tag("hello", List(Tag("bar", Nil))),
      Tag("world", List(Contents("baz")))
    ),
    DecoderError.UnexpectedTag("bar")
  )
}
