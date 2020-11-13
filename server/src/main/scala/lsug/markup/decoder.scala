package lsug
package markup

import cats._
import cats.data._
import cats.implicits._

import Pollen._

private class Decoder[A](
    private val run: Kleisli[Either[DecoderError, ?], List[Pollen], A]
) {

  def apply(children: List[Pollen]): Either[DecoderError, A] = run.run(children)

  def map[B](f: A => B): Decoder[B] = new Decoder(run.map(f))

  def mapError[B](f: A => Either[DecoderError, B]): Decoder[B] =
    new Decoder(run.flatMapF(f))

  def product[B](next: Decoder[B]): Decoder[(A, B)] =
    new Decoder(run.product(next.run))

  def optional: Decoder[Option[A]] =
    new Decoder(run.redeem(_ => None, Some(_)))
}

private object Decoder {

  import markup.{DecoderError => Error}

  implicit def decoderSemigroupal: Semigroupal[Decoder] =
    new Semigroupal[Decoder] {
      def product[A, B](fa: Decoder[A], fb: Decoder[B]): Decoder[(A, B)] =
        fa.product(fb)
    }

  implicit def decoderFunctor: Functor[Decoder] = new Functor[Decoder] {
    def map[A, B](da: Decoder[A])(f: A => B): Decoder[B] =
      da.map(f)
  }

  implicit final class DecoderTagOps(val decoder: Decoder[Tag]) {
    def andThen[A](next: Decoder[A]): Decoder[A] =
      new Decoder(decoder.run.map(_.children).andThen(next.run))
  }

  implicit final class DecoderTagCollectionOps[F[_]: Traverse](
      val decoder: Decoder[F[Tag]]
  ) {
    def andThenTraverse[A](next: Decoder[A]): Decoder[F[A]] =
      decoder.mapError(_.traverse(tag => next(tag.children)))
  }

  def apply[A](f: List[Pollen] => Either[Error, A]): Decoder[A] =
    new Decoder[A](Kleisli(f))

  def children(name: String): Decoder[List[Tag]] =
    apply(children =>
      Right(children.mapFilter {
        case t: Tag      => Option.when(t.name === name)(t)
        case _: Contents => None
      })
    )

  def text: Decoder[String] =
    apply(_.traverse {
      case Contents(text) => Right(text)
      case Tag(name, _)   => Left(Error.UnexpectedTag(name))
    }.map(_.combineAll))

  def child(name: String): Decoder[Tag] =
    children(name).mapError {
      case tag :: Nil => Right(tag)
      case Nil        => Left(Error.TagNotFound(name))
      case multipleTags =>
        Left(Error.MultipleTagsFound(name, multipleTags.length))
    }

  def oneOrMoreChildren(name: String): Decoder[NonEmptyList[Tag]] = {
    children(name)
      .mapError(tags =>
        NonEmptyList.fromList(tags).toRight(Error.TagNotFound(name))
      )
  }
}

trait DecoderError

object DecoderError {
  final case class TagNotFound(name: String) extends DecoderError
  final case class UnexpectedTag(name: String) extends DecoderError
  final case class MultipleTagsFound(name: String, number: Int)
      extends DecoderError
  final case class EvaluatorFailed(error: EvaluatorError) extends DecoderError

  final case class InvalidContents(
      name: String,
      contents: String,
      message: String
  ) extends DecoderError
  final case class EmptyContents(name: String) extends DecoderError

}
