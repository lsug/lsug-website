package lsug
package markup

import cats._
import cats.implicits._

private class DecoderSpec extends LsugSuite with DecoderAssertions {

  import magnolify.cats.auto._

  import Pollen._
  import Decoder._
  import DecoderError._

  val line: String = "A truth that's told with bad intent"
  val lineP: Pollen = Contents(line)
  val secondLine: String = "\nBeats all the lies you can invent"
  val secondLineP: Pollen = Contents(secondLine)
  val verse: String = line |+| secondLine
  val verseName: String = "verse"
  val verseP: Tag = Tag(verseName, List(lineP, secondLineP))
  val unexpectedName: String = "unexpected"
  val unexpectedP: Tag = Tag(unexpectedName, Nil)
  val unexpectedVerseP: Tag = Tag(verseName, List(unexpectedP))

  checkText("single contents")(List(lineP), line)
  checkText("multiple contents")(List(lineP, secondLineP), verse)
  checkText("no contents")(Nil, "")
  checkTextFails("tag")(List(verseP), UnexpectedTag(verseName))
  checkTextFails("tag and contents")(
    List(verseP, lineP),
    UnexpectedTag(verseName)
  )

  checkChildren("tag")(verseName, List(verseP), List(verseP))
  checkChildren("no tags")("hello", Nil, Nil)
  checkChildren("other tags")(
    verseName,
    List(verseP, unexpectedP),
    List(verseP)
  )
  checkChildren("other contents")(verseName, List(verseP, lineP), List(verseP))

  checkChild("tag")(verseName, List(verseP), verseP)
  checkChildFails("multiple tags")(
    verseName,
    List(verseP, verseP),
    MultipleTagsFound(verseName, 2)
  )
  checkChildFails("no tags")(verseName, Nil, TagNotFound(verseName))

  checkAndThen("nested text")(child(verseName), text, List(verseP), verse)

  checkAndThenTraverse("nested texts")(
    children(verseName),
    text,
    List(verseP, verseP),
    List(verse, verse)
  )
  checkAndThenTraverseFails("unexpected tag")(
    children(verseName),
    text,
    List(unexpectedVerseP),
    UnexpectedTag(unexpectedName)
  )

  def checkText(
      name: String
  )(input: List[Pollen], output: String)(implicit loc: munit.Location): Unit = {
    assertEither(text, input, Right(output))
      .label("text")
      .build(name)
  }

  def checkTextFails(name: String)(input: List[Pollen], expected: DecoderError)(
      implicit loc: munit.Location
  ): Unit = {
    assertEither(text, input, Left(expected))
      .label("text")
      .build(name)
  }

  def checkChildren(
      name: String
  )(tagname: String, input: List[Pollen], output: List[Tag])(
      implicit loc: munit.Location
  ): Unit = {
    assertEither(children(tagname), input, Right(output))
      .label("children")
      .build(name)
  }

  def checkChild(
      name: String
  )(tagname: String, input: List[Pollen], output: Tag)(
      implicit loc: munit.Location
  ): Unit = {
    assertEither(child(tagname), input, Right(output))
      .label("child")
      .build(name)
  }

  def checkChildFails(name: String)(
      tagname: String,
      input: List[Pollen],
      expected: DecoderError
  )(implicit loc: munit.Location): Unit = {
    assertEither(child(tagname), input, Left(expected))
      .label("child")
      .build(name)
  }

  def checkAndThen[A: Eq: Show](name: String)(
      first: Decoder[Tag],
      second: Decoder[A],
      input: List[Pollen],
      output: A
  )(implicit loc: munit.Location): Unit = {
    assertEither(first.andThen(second), input, Right(output))
      .label("andThen")
      .build(name)
  }

  def checkAndThenTraverse[F[_]: Traverse, A](name: String)(
      first: Decoder[F[Tag]],
      second: Decoder[A],
      input: List[Pollen],
      output: F[A]
  )(implicit loc: munit.Location, eq: Eq[F[A]], show: Show[F[A]]): Unit = {
    assertEither(first.andThenTraverse(second), input, Right(output))
      .label("andThenTraverse")
      .build(name)
  }

  def checkAndThenTraverseFails[F[_]: Traverse, A](name: String)(
      first: Decoder[F[Tag]],
      second: Decoder[A],
      input: List[Pollen],
      output: DecoderError
  )(implicit loc: munit.Location, eq: Eq[F[A]], show: Show[F[A]]): Unit = {
    assertEither(first.andThenTraverse(second), input, Left(output))
      .label("andThenTraverse")
      .build(name)
  }

}

trait DecoderAssertions { self: LsugSuite =>

  import magnolify.cats.auto._

  def assertEither[A: Eq: Show](
      decoder: Decoder[A],
      input: List[Pollen],
      expected: Either[DecoderError, A]
  )(implicit loc: munit.Location): TestBuilder = {
    val result = decoder(input)
    expected match {
      case Right(expected) =>
        (builder {
          assert(clue(result.isRight), "Failed to decode")
          result.foreach(assertOutput(_, expected))
        }).label("success")
      case Left(expected) =>
        builder({
          assert(clue(result.isLeft), "Decoding succeeded")
          result.swap.foreach(assertOutput(_, expected))
        }).label("fail")
    }
  }

  private def assertOutput[A: Eq: Show](result: A, expected: A)(
      implicit loc: munit.Location
  ): Unit = {
    if (result =!= expected) {
      fail(
        "Result of decoding is incorrect",
        clues(
          result.show,
          expected.show
        )
      )
    }
  }

}
