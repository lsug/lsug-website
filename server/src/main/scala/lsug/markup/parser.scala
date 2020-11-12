package lsug
package markup

import scala.util.matching.Regex

import cats._
import cats.data._
import cats.implicits._

sealed trait Parser[+A] {
  def apply(text: String): Result[A]
}

object Parser {

  private case object Fail extends Parser[Nothing] {
    def apply(text: String): Result[Nothing] = Result.Fail
  }

  private final case class Pure[A](value: A) extends Parser[A] {
    def apply(text: String): Result[A] = Result.Success(value, text)
  }

  private final case class Pattern(regex: Regex) extends Parser[String] {
    def apply(text: String): Result[String] =
      regex
        .findPrefixOf(text)
        .fold[Result[String]](Result.Fail)(prefix =>
          Result.Success(prefix, text.stripPrefix(prefix))
        )

  }

  private final class Map[A, B](f: A => B, pa: => Parser[A]) extends Parser[B] {
    def apply(text: String): Result[B] = pa(text).map(f)
  }

  private final class Product[A, B](pa: => Parser[A], pb: => Parser[B])
      extends Parser[(A, B)] {
    def apply(text: String): Result[(A, B)] = pa(text) match {
      case Result.Fail              => Result.Fail
      case Result.Success(va, rest) => pb(rest).map(vb => (va, vb))
    }
  }

  private final class Either[A](left: => Parser[A], right: => Parser[A])
      extends Parser[A] {
    def apply(text: String): Result[A] = left(text) match {
      case Result.Fail          => right(text)
      case s: Result.Success[A] => s
    }
  }

  def fail: Parser[Nothing] = Fail
  def pure[A](value: => A): Parser[A] = Pure(value)
  def pattern(regex: Regex): Parser[String] = Pattern(regex)
  def either[A](left: => Parser[A], right: => Parser[A]): Parser[A] =
    new Either(left, right)
  def product[A, B](pa: => Parser[A], pb: => Parser[B]): Parser[(A, B)] =
    new Product(pa, pb)
  def map[A, B](pa: => Parser[A])(f: A => B): Parser[B] = new Map(f, pa)

  def zeroOrMore[A](pa: => Parser[A]): Parser[List[A]] =
    either(
      product(pa, zeroOrMore(pa)).map { case (head, tail) => head :: tail },
      pure(List.empty[A])
    )

  def oneOrMore[A](pa: => Parser[A]): Parser[NonEmptyList[A]] =
    product(pa, zeroOrMore(pa)).map {
      case (head, tail) =>
        NonEmptyList(head, tail)
    }

  implicit val parserFunctor: Functor[Parser] = new Functor[Parser] {
    def map[A, B](pa: Parser[A])(f: A => B): Parser[B] = Parser.map(pa)(f)
  }

  implicit val parserAlternative: Alternative[Parser] =
    new Alternative[Parser] {
      def pure[A](x: A): Parser[A] = new Pure(x)
      def ap[A, B](pf: Parser[A => B])(pa: Parser[A]): Parser[B] =
        Parser.map(Parser.product(pf, pa)) {
          case (f, a) => f(a)
        }

      def empty[A]: Parser[A] = Parser.Fail

      def combineK[A](x: Parser[A], y: Parser[A]): Parser[A] = new Either(x, y)
    }
}

sealed trait Result[+A] {
  def toEither: Either[Unit, A] = this match {
    case Result.Fail              => Left(())
    case Result.Success(value, _) => Right(value)
  }
}

object Result {
  case object Fail extends Result[Nothing]
  case class Success[A](value: A, rest: String) extends Result[A]

  implicit val resultFunctor: Functor[Result] = new Functor[Result] {
    def map[A, B](ra: Result[A])(f: A => B): Result[B] = ra match {
      case Fail                 => Fail
      case Success(value, rest) => Success(f(value), rest)
    }
  }
}
