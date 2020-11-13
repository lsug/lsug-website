package lsug
package markup

import lsug.protocol._
import cats.data._

private class DecodersSpec extends LsugSuite with DecoderAssertions {

  import Pollen._
  import Decoders._
  import DecoderError._
  import Markup.{Text => MText}

  val text = "text"
  val textP = Contents(text)
  val strongP = Tag("em", List(textP))
  val strongEl = MText.Styled.Strong(text)
  val urlText = "http://this.is.a.url"
  val urlTextP = Contents(urlText)
  val linkP =
    Tag("link", List(Tag("text", List(textP)), Tag("url", List(urlTextP))))
  val linkEl = MText.Link(text, urlText)
  val linkMissingTextP = Tag("link", List(Tag("url", List(urlTextP))))
  val linkMissingUrlP = Tag("link", List(Tag("text", List(textP))))
  val unexpectedName = "unexpected"
  val unexpectedP = Tag(unexpectedName, Nil)
  val paragraphP = Tag("p", List(textP, strongP, linkP, textP))
  val paragraphEl = Markup.Paragraph(
      NonEmptyList.of(
        MText.Plain(text),
        MText.Styled.Strong(text),
        MText.Link(text, urlText),
        MText.Plain(text)
      )
  )

  check("strong", "contains text")(strong, strongP, strongEl)
  check("link", "contains text and url")(link, linkP, linkEl)
  checkFailed("link", "no text")(link, linkMissingTextP, TagNotFound("text"))
  checkFailed("link", "no text")(link, linkMissingUrlP, TagNotFound("url"))

  check("paragraph", "contains text markup")(paragraph, paragraphP, paragraphEl)

  checkFailed("paragraph", "unexpected tag")(
    paragraph,
    Tag("p", List(textP, strongP, unexpectedP, linkP)),
    EvaluatorFailed(EvaluatorError.UnevaluatedChild(unexpectedName))
  )
  checkFailed("paragraph", "empty")(paragraph, Tag("p", Nil), EmptyContents("p"))
  //check("markup", "contains paragraphs")

  def check[A](
      name: String,
      description: String
  )(decoder: Decoder[A], input: Tag, value: A): Unit =
    assertEither(decoder, input.children, Right(value))
      .label(name)
      .build(description)

  def checkFailed[A](
      name: String,
      description: String
  )(decoder: Decoder[A], input: Tag, error: DecoderError): Unit =
    assertEither(decoder, input.children, Left(error))
      .label(name)
      .build(description)
}
