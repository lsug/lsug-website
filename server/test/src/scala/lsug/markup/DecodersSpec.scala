package lsug
package markup

import lsug.protocol._
import cats.data._
import cats._

import magnolify.cats.auto._

private class DecodersSpec extends LsugSuite with DecoderAssertions {

  import Pollen._
  import Decoders._
  import DecoderError._
  import Markup.{Text => MText}
  import lsug.protocol.{Meetup => PMeetup}

  def tagWithText(name: String, contents: String): Tag =
    Tag(name, List(Contents(contents)))

  def emptyTag(name: String): Tag = Tag(name, Nil)

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
  val unexpectedP = emptyTag(unexpectedName)
  val paragraphP = Tag("p", List(textP, strongP, linkP, textP))
  val paragraphEl = Markup.Paragraph(
    NonEmptyList.of(
      MText.Plain(text),
      MText.Styled.Strong(text),
      MText.Link(text, urlText),
      MText.Plain(text)
    )
  )
  val bioP = Tag("bio", List(paragraphP, paragraphP))
  val bioWithCommentsP = Tag("bio", List(paragraphP, paragraphP, textP))
  val markupEl = List(paragraphEl, paragraphEl)
  val pronounSub = "they"
  val pronounOb = "them"
  val pronoun = s"$pronounSub/$pronounOb"

  val speakerP = Tag(
    "speaker",
    List(
      Tag(
        "social",
        List(
          tagWithText("blog", text),
          tagWithText("twitter", text),
          tagWithText("github", text)
        )
      ),
      tagWithText("name", text),
      tagWithText("photo", text),
      tagWithText("pronoun", pronoun),
      bioP
    )
  )

  val socialMediaEl = Speaker.SocialMedia(
    blog = Some(new Link(text)),
    twitter = Some(new Twitter.Handle(text)),
    github = Some(new Github.User(text))
  )
  val somewhatSocialSpeakerP = Tag(
    "speaker",
    List(
      tagWithText("name", text),
      Tag(
        "social",
        List(tagWithText("blog", text), tagWithText("github", text))
      )
    )
  )

  val somewhatSocialMediaEl = Speaker.SocialMedia(
    blog = Some(new Link(text)),
    twitter = None,
    github = Some(new Github.User(text))
  )
  val nameOnlySpeakerP = Tag("speaker", List(tagWithText("name", text)))
  val unsocialMediaEl = Speaker.SocialMedia.empty
  val speakerEl = (id: Speaker.Id) =>
    Speaker(
      bio = markupEl,
      socialMedia = socialMediaEl,
      profile = Speaker.Profile(
        id = id,
        name = text,
        photo = Some(new Asset(text))
      ),
      pronoun = Some(new Speaker.Pronoun(pronounSub, pronounOb))
    )

  val nameOnlySpeakerEl = (id: Speaker.Id) =>
    Speaker(
      bio = Nil,
      socialMedia = unsocialMediaEl,
      profile = Speaker.Profile(id = id, name = text, photo = None),
      pronoun = None
    )
  val unnamedSpeakerP = emptyTag("speaker")
  val csvList = NonEmptyList.of("a", "csv", "value")
  val csv = csvList.toList.mkString(",")
  val venueP = Tag(
    "venue",
    List(
      tagWithText("address", csv),
      tagWithText("name", text)
    )
  )
  val venueEl = (id: Venue.Id) =>
    Venue.Summary(id = id, name = text, address = csvList)

  val materialP = Tag("material", List(linkP, linkP))
  val materialEl = List(
    PMeetup.Material(text = text, location = urlText),
    PMeetup.Material(text = text, location = urlText)
  )
  val slidesP = Tag(
    "event",
    List(Tag("slides", List(tagWithText("url", urlText), emptyTag("external"))))
  )
  val slidesEl = Some(PMeetup.Media(new Link(urlText), true))

  val internalSlidesP =
    Tag("event", List(Tag("slides", List(tagWithText("url", urlText)))))
  val internalSlidesEl = Some(PMeetup.Media(new Link(urlText), false))
  val noSlidesP = emptyTag("event")

  implicit val speakerFnEq: Eq[Speaker.Id => Speaker] =
    Eq.by(_.apply(new Speaker.Id("anyId")))

  implicit val speakerFnShow: Show[Speaker.Id => Speaker] =
    Show(_.apply(new Speaker.Id("anyId")).toString)

  implicit val venueFnEq: Eq[Venue.Id => Venue.Summary] =
    Eq.by(_.apply(new Venue.Id("anyId")))

  implicit val venueFnShow: Show[Venue.Id => Venue.Summary] =
    Show(_.apply(new Venue.Id("anyId")).toString)

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

  checkFailed("paragraph", "empty")(
    paragraph,
    emptyTag("p"),
    EmptyContents("p")
  )
  check("markup", "contains paragraphs")(markup, bioP, markupEl)
  check("markup", "can have comments")(markup, bioWithCommentsP, markupEl)
  check("social media", "full profile")(socialMedia, speakerP, socialMediaEl)
  check("social media", "empty")(socialMedia, nameOnlySpeakerP, unsocialMediaEl)
  check("social media", "optional parts")(
    socialMedia,
    somewhatSocialSpeakerP,
    somewhatSocialMediaEl
  )
  check("speaker", "has name, photo, profile and pronoun")(
    speaker,
    speakerP,
    speakerEl
  )
  check("speaker", "name only")(speaker, nameOnlySpeakerP, nameOnlySpeakerEl)
  checkFailed("speaker", "missing name")(
    speaker,
    unnamedSpeakerP,
    TagNotFound("name")
  )

  check("venue", "has name and address")(venue, venueP, venueEl)
  check("material", "has link and text")(materials, materialP, materialEl)
  check("slides", "external")(slides, slidesP, slidesEl)
  check("slides", "internal")(slides, internalSlidesP, internalSlidesEl)
  check("slides", "none")(slides, noSlidesP, None)

  def check[A: Eq: Show](
      name: String,
      description: String
  )(decoder: Decoder[A], input: Tag, value: A): Unit =
    assertEither(decoder, input.children, Right(value))
      .label(name)
      .build(description)

  def checkFailed[A: Eq: Show](
      name: String,
      description: String
  )(decoder: Decoder[A], input: Tag, error: DecoderError): Unit =
    assertEither(decoder, input.children, Left(error))
      .label(name)
      .build(description)
}
