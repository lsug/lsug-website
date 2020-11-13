package lsug
package markup

import cats.implicits._

import lsug.protocol.{Markup => PMarkup}

sealed trait EvaluatorError
object EvaluatorError {
  final case object EmptyMarkupElement extends EvaluatorError
  final case class DecoderFailed(error: DecoderError) extends EvaluatorError
  final case class UnevaluatedChild(name: String) extends EvaluatorError
  final case class UnrecognisedMarkup(markup: PMarkup) extends EvaluatorError
}

sealed trait EvaluatorOutput

object EvaluatorOutput {
  final case class Markup(name: String, markup: PMarkup) extends EvaluatorOutput
  final case class Text(text: String) extends EvaluatorOutput
}

private object Evaluator {

  import lsug.markup.{EvaluatorError => Error}

  sealed trait Output

  object Output {
    final case class NotEvaluated(name: String, contents: List[Output])
        extends Output
    final case class Evaluated(output: EvaluatorOutput) extends Output
  }

  final case class Pair(input: Pollen, output: Output)

  import Output._
  import EvaluatorOutput._
  type TagName = String
  type Function = List[Pair] => Either[Error, PMarkup]

  type Context = Map[TagName, Function]

  private def eval(ctx: Context)(pollen: Pollen): Either[Error, Pair] = {
    (pollen match {
      case Pollen.Contents(text) => Right(Evaluated(Text(text)))
      case Pollen.Tag(name, children) =>
        children
          .traverse(eval(ctx))
          .flatMap { pairs =>
            ctx
              .get(name)
              .fold[Either[Error, Output]](
                Right(NotEvaluated(name, pairs.map(_.output)))
              )(_.apply(pairs).map(markup => Evaluated(Markup(name, markup))))
          }
    }).map(Pair(pollen, _))
  }

  def from[A <: protocol.Markup](
      name: String,
      decoder: Decoder[A]
  ): (String, Function) =
    (name, { pairs =>
      decoder(pairs.map(_.input)).leftMap(Error.DecoderFailed(_))
    })

  def to[A](
      ctx: Context,
      convert: List[EvaluatorOutput] => Either[DecoderError, A]
  ): Decoder[A] = Decoder[A] { children =>
    children
      .traverse(eval(ctx))
      .flatMap(_.traverse(_.output match {
        case Evaluated(output)     => Right(output)
        case NotEvaluated(name, _) => Left(Error.UnevaluatedChild(name))
      }))
      .leftMap(DecoderError.EvaluatorFailed(_))
      .flatMap(convert)
  }
}
