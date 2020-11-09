package lsug
package markup
package context

// A context free decoder

sealed trait Error

object Error {
  // We don't need all of these
  final case object EmptyTagFound extends Error
  final case class NestedTagsFound(elements: List[Markup]) extends Error

  final case class TagFailedToDecode(error: Decoder.Error) extends Error
}

sealed trait Foo

object Foo {
  final case class NotFound(name: String, contents: List[Foo]) extends Foo
  final case class Markup(element: PMarkup) extends Foo
  final case class Text(text: String) extends Foo

  final case class Pair(original: Pollen, next: Foo)
}

object ContextFree {
  type TagName = String
  type Decoder = List[Pair] => Either[Error, Markup]
  type Context = Map[TagName, Decoder]

  def decode(ctx: Context)(pollen: Pollen): Either[Error, Foo] = {
    pollen match {
      case Pollen.Contents(text) => Right(Foo.Text(text))
      case Pollen.Tag(name, children) =>
        children.traverse(decode(ctx))
          .map { childFoos =>
            ctx.get(name)
            .fold(Right(Foo.NotFound(name, childFoos)))(_.apply(childFoos))
          }
    }
  }

  def from[A <: Markup](name: String, decoder: TagDecoder[A]): Decoder = { pairs =>
    decoder(Pollen.Tag(name, pairs.map(_.original))).leftMap(Error.TagFailedToDecode(_))
  }

  def paragraph: Decoder = { pairs =>
    val contents = pairs.traverse {
      case NotFound(name, contents) => error
      case Markup(element : Markup.Text) => element
      case Text(text) => Text.Plain(text)
    }.flatMap(els => NonEmptyList.fromList(els).toRight(EmptyTag))
      .map(Markup.Paragraph)
  }
}
