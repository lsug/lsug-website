package lsug
package markup

import cats._
import cats.implicits._

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
