package lsug

import cats.Monad
import protocol.Markup
import yaml.Yaml
import cats.implicits._

object Decoder {

  sealed trait Result[+A] {
    def toOption: Option[A] = this match {
      case Result.Success(a) => a.some
      case _                 => none
    }

    def toEither: Either[String, A] = this match {
      case Result.Success(a) => a.asRight
      case Result.Failure(err) => err.asLeft
    }
  }

  object Result {

    case class Success[A](a: A) extends Result[A]
    case class Failure(err: String) extends Result[Nothing]

    def fromEither[A](e: Either[String, A]): Result[A] =
      e.bimap(Result.Failure(_), Result.Success(_)).merge

    implicit val resultMonad: Monad[Result] = new Monad[Result] {
      def pure[A](x: A): Result[A] = Result.Success(x)
      def flatMap[A, B](fa: Result[A])(f: A => Result[B]): Result[B] =
        fa match {
          case Success(a)   => f(a)
          case err: Failure => err
        }

      @annotation.tailrec
      def tailRecM[A, B](a: A)(f: A => Result[Either[A, B]]): Result[B] =
        f(a) match {
          case Success(Left(a))  => tailRecM(a)(f)
          case Success(Right(b)) => Success(b)
          case err: Failure      => err
        }
    }

  }

  def decoder[A](f: (Option[Yaml.Obj], List[Markup]) => Result[A]): Decoder[A] =
    (meta, markup) => f(meta, markup)

  def meta[A](f: Yaml.Obj => Result[A]): Decoder[A] =
    (yaml, _) => yaml.map(f).getOrElse(Result.Failure("missing metadata"))

  def markup[A](f: List[Markup] => Result[A]): Decoder[A] =
    (_, markup) => f(markup)

  def instance[A](f: (Yaml.Obj, List[Markup]) => Result[A]): Decoder[A] =
    (yaml, markup) =>
      yaml.map(f(_, markup)).getOrElse(Result.Failure("missing metadata"))
}

trait Decoder[A] {
  def apply(meta: Option[Yaml.Obj], m: List[Markup]): Decoder.Result[A]
}
