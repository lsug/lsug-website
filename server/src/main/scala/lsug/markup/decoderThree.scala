package lsug
package markup
package decoder2

import cats.data._
import cats.implicits._

import lsug.protocol.{Markup => PMarkup}
import lsug.markup.decoder2.{Error => DecoderError}

private object Evaluator {

  sealed trait Error
  object Error {
    final case object EmptyMarkupElement extends Error
    final case class DecoderFailed(error: DecoderError) extends Error
    final case class UnevaluatedChild(name: String) extends Error
    final case class UnrecognisedMarkup(markup: PMarkup) extends Error
}

  sealed trait Output

  object Output {
    final case class NotEvaluated(name: String, contents: List[Output]) extends Output
    final case class Markup(element: PMarkup) extends Output
    final case class Text(text: String) extends Output
  }

  final case class Pair(input: Pollen, output: Output)

  import Output._
  type TagName = String
  type Function = List[Pair] => Either[Error, PMarkup]

  type Context = Map[TagName, Function]

  def eval(ctx: Context)(pollen: Pollen): Either[Error, Pair] = {
    (pollen match {
      case Pollen.Contents(text) => Right(Output.Text(text))
      case Pollen.Tag(name, children) =>
        children.traverse(eval(ctx))
          .flatMap { childOutputs =>
            ctx.get(name)
              .fold[Either[Error, Output]](Right(Output.NotEvaluated(name, childOutputs.map(_.output))))(_.apply(childOutputs).map(Output.Markup(_)))
          }
    }).map(Pair(pollen, _))
  }

  def paragraph: Function = { pairs =>
    pairs.map(_.output).traverse {
      case NotEvaluated(name, _) => Left(Error.UnevaluatedChild(name))
      case Markup(element : PMarkup.Text) => Right(element)
      case Text(text) => Right(PMarkup.Text.Plain(text))
      case Markup(element) => Left(Error.UnrecognisedMarkup(element))
    }.flatMap(els => NonEmptyList.fromList(els).toRight(Error.EmptyMarkupElement))
      .map(PMarkup.Paragraph(_))
  }

  def from[A <: PMarkup](name: String, decoder: Decoder[A]): Function = { pairs =>
    decoder(Pollen.Tag(name, pairs.map(_.input))).leftMap(Error.DecoderFailed(_))
  }

  // createf = create a tag from children
  def to[A](ctx: Context, createf: List[PMarkup] => Either[DecoderError, A]): Decoder[PMarkup] = Decoder[A] { tag =>
    tag.children.traverse(evaluate(ctx, _))
      .map(_.output match {
        case Markup(element) => Right(element)
        case Text(text) => Right(P.Markup.Text.Plain(text))
        case NotEvaluated(name, _) => Left(Error.UnevaluatedChild(name))
      }).leftMap(errorToDecoderError)
      .flatMap(createf)
  }

  // TODO: Hook in decoder to evaluator
}
