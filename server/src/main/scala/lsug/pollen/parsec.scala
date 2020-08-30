package lsug
package pollen

import Function.const
import monocle.function.Cons.cons
import monocle.{Iso, Getter}
import monocle.macros.GenLens
import cats.implicits._
import cats._
import cats.data.{State, StateT}
import cats.data.NonEmptyList
import cats.arrow.FunctionK
import cats.data.Const

object Parse {

  case class Error(
      offset: Int,
      token: Option[Error.Item],
      expected: Set[Error.Item]
  )

  object Error {

    val _expected = GenLens[Error](_.expected)

    implicit val errorOrder: Semigroup[Error] = Semigroup.instance { (x, y) =>
      if (x.offset > y.offset) {
        x
      } else {
        y
      }
    }

    sealed trait Item

    object Item {
      case object EndOfInput extends Item
      case class Label(s: String) extends Item
      case class Tokens(tokens: String) extends Item
      case class Token(token: Char) extends Item
      case class Message(s: String) extends Item
    }

  }

  sealed trait Consumption

  object Consumption {
    case object Untouched extends Consumption
    case object Consumed extends Consumption
  }

  case class Reply[+A](
      consumption: Consumption,
      result: Either[Parse.Error, A]
  )

  object Reply {

    def consumed[A](result: Either[Parse.Error, A]): Reply[A] =
      Parse.Reply(Parse.Consumption.Consumed, result)

    def untouched[A](result: Either[Parse.Error, A]): Reply[A] =
      Parse.Reply(Parse.Consumption.Untouched, result)
  }

  case class State(input: String, offset: Int, errors: List[Error])

  object State {

    def apply(s: String): State = State(s, 0, List())

    val _input = GenLens[State](_.input)
    val _offset = GenLens[State](_.offset)

    def _split(f: Char => Boolean): Getter[String, (String, String)] =
      Getter[String, (String, String)] { s =>
        val index = (0 to (s.length - 1)).find(i => !f(s.charAt(i)))
        index
          .map(i => (s.substring(0, i), s.substring(i)))
          .getOrElse((s, ""))
      }

    def _takeN(i: Int) =
      Getter[String, Option[(String, String)]] { s =>
        if (s.length < i) {
          none
        } else {
          s.splitAt(i).some
        }
      }

    implicit val stateOrder: Order[State] = Order.by(_.offset)

  }

  final class Hints(val items: Set[Error.Item]) extends AnyVal

  object Hints {
    implicit val hintsMonoid: Monoid[Hints] =
      Monoid[Set[Error.Item]].imap(new Hints(_))(_.items)

    val empty = hintsMonoid.empty

    def apply(items: Error.Item*): Hints =
      new Hints(Set(items: _*))
  }

  type ContOk[F[_], A, B] = (A, Parse.State, Parse.Hints) => F[B]
  type ContErr[F[_], B] = (Parse.Error, Parse.State) => F[B]
  type Cont[F[_], A] = FunctionK[λ[
    B => (
        Parse.State,
        Parse.ContOk[F, A, B], //consumed ok
        Parse.ContErr[F, B], //consumed error
        Parse.ContOk[F, A, B], //empty ok
        Parse.ContErr[F, B] //empty error
    )
  ], F]

  val listToOption = λ[FunctionK[List, Option]](_.headOption)
}

object ParsecT {

  def pure[F[_], A](a: A): ParsecT[F, A] =
    ParsecT(
      λ[
        FunctionK[λ[
          B => (
              Parse.State,
              Parse.ContOk[F, A, B],
              Parse.ContErr[F, B],
              Parse.ContOk[F, A, B],
              Parse.ContErr[F, B]
          )
        ], F]
      ] {
        case (state, ok, _, _, _) =>
          ok(a, state, Parse.Hints.empty)
      }
    )

  def liftF[F[_]: Monad, A](fa: F[A]): ParsecT[F, A] =
    ParsecT(
      λ[
        FunctionK[λ[
          B => (
              Parse.State,
              Parse.ContOk[F, A, B],
              Parse.ContErr[F, B],
              Parse.ContOk[F, A, B],
              Parse.ContErr[F, B]
          )
        ], F]
      ] {
        case (state, ok, _, _, _) =>
          fa.flatMap(ok(_, state, Parse.Hints.empty))
      }
    )

  def eof[F[_]]: ParsecT[F, Unit] = {
    val _take = Parse.State._input ^<-? cons[String, Char]
    ParsecT(
      λ[
        FunctionK[λ[
          B => (
              Parse.State,
              Parse.ContOk[F, Unit, B],
              Parse.ContErr[F, B],
              Parse.ContOk[F, Unit, B],
              Parse.ContErr[F, B]
          )
        ], F]
      ] {
        case (state, _, _, eok, eerr) =>
          _take
            .getOption(state)
            .map {
              case (c, _) =>
                eerr(
                  Parse.Error(
                    Parse.State._offset.get(state),
                    Parse.Error.Item.Token(c).some,
                    Set(
                      Parse.Error.Item.EndOfInput
                    )
                  ),
                  state
                )
            }
            .getOrElse {
              eok((), state, Parse.Hints.empty)
            }
      }
    )
  }

  def state[F[_]]: ParsecT[F, Parse.State] =
    ParsecT(
      λ[
        FunctionK[λ[
          B => (
              Parse.State,
              Parse.ContOk[F, Parse.State, B],
              Parse.ContErr[F, B],
              Parse.ContOk[F, Parse.State, B],
              Parse.ContErr[F, B]
          )
        ], F]
      ] {
        case (state, _, _, eok, _) =>
          eok(state, state, Parse.Hints.empty)
      }
    )

  def offset[F[_]]: ParsecT[F, Int] =
    state.map(_.offset)

  def never[F[_], A]: ParsecT[F, A] =
    ParsecT(
      λ[
        FunctionK[λ[
          B => (
              Parse.State,
              Parse.ContOk[F, A, B],
              Parse.ContErr[F, B],
              Parse.ContOk[F, A, B],
              Parse.ContErr[F, B]
          )
        ], F]
      ] {
        case (state, _, _, _, eerr) =>
          eerr(Parse.Error(state.offset, none, Set()), state)
      }
    )

  def token[F[_], A](
      expected: Set[Parse.Error.Item]
  )(f: Char => Option[A]): ParsecT[F, A] = {
    val _take = Parse.State._input ^<-? cons[String, Char]
    ParsecT(
      λ[
        FunctionK[λ[
          B => (
              Parse.State,
              Parse.ContOk[F, A, B],
              Parse.ContErr[F, B],
              Parse.ContOk[F, A, B],
              Parse.ContErr[F, B]
          )
        ], F]
      ] {
        case (state, ok, err, _, eerr) =>
          _take
            .getOption(state)
            .map {
              case (c, s) =>
                val g = (Parse.State._input.set(s) andThen Parse.State._offset
                  .modify(_ + 1))
                val eitems = Parse.Error.Item.Token(c)
                f(c)
                  .map {
                    ok(
                      _,
                      g(state),
                      Parse.Hints(
                        eitems
                      )
                    )
                  }
                  .getOrElse {
                    eerr(
                      Parse.Error(
                        Parse.State._offset.get(state),
                        eitems.some,
                        expected
                      ),
                      state
                    )
                  }
            }
            .getOrElse {
              eerr(
                Parse.Error(
                  Parse.State._offset.get(state),
                  Parse.Error.Item.EndOfInput.some,
                  expected
                ),
                state
              )
            }
      }
    )
  }

  def tokens[F[_]](
      tokens: String
  )(f: (String, String) => Boolean): ParsecT[F, String] = {
    val len = tokens.length
    val _tokens = Parse.State._input composeGetter Parse.State._takeN(len)
    ParsecT[F, String](
      λ[
        FunctionK[λ[
          B => (
              Parse.State,
              Parse.ContOk[F, String, B],
              Parse.ContErr[F, B],
              Parse.ContOk[F, String, B],
              Parse.ContErr[F, B]
          )
        ], F]
      ] {
        case (state, ok, err, eok, eerr) =>
          val expected =
            Set[Parse.Error.Item](Parse.Error.Item.Label(tokens))
          _tokens
            .get(state)
            .map {
              case (ts, s) =>
                val g =
                  (Parse.State._input.set(s) andThen Parse.State._offset
                    .modify(
                      _ + len
                    ))
                if (f(tokens, ts)) {
                  if (ts.length === 0) {
                    eok(
                      ts,
                      g(state),
                      Parse.Hints.empty
                    )
                  } else {
                    ok(
                      ts,
                      g(state),
                      Parse.Hints.empty
                    )
                  }
                } else {
                  eerr(
                    Parse
                      .Error(
                        state.offset,
                        Parse.Error.Item
                          .Tokens(ts)
                          .some,
                        expected
                      ),
                    state
                  )
                }
            }
            .getOrElse {
              eerr(
                Parse.Error(
                  state.offset,
                  Parse.Error.Item.EndOfInput.some,
                  expected
                ),
                state
              )
            }
      }
    )
  }

  def takeWhile[F[_]](
      label: Option[String],
      f: Char => Boolean
  ): ParsecT[F, String] = {
    val _split = Parse.State._input composeGetter Parse.State._split(f)
    ParsecT(
      λ[
        FunctionK[λ[
          B => (
              Parse.State,
              Parse.ContOk[F, String, B],
              Parse.ContErr[F, B],
              Parse.ContOk[F, String, B],
              Parse.ContErr[F, B]
          )
        ], F]
      ] {
        case (state, ok, _, eok, _) =>
          val (c, s) = _split.get(state)
          val hs = label
            .map(l => new Parse.Hints(Set(Parse.Error.Item.Label(l))))
            .getOrElse(Parse.Hints.empty)
          val g =
            (Parse.State._input.set(s) andThen Parse.State._offset
              .modify(
                _ + c.length
              ))
          Parse.State._input composeGetter Parse.State._split(f)
          if (c.isEmpty) {
            eok(c, g(state), hs)
          } else {
            ok(c, g(state), hs)
          }
      }
    )
  }

  def takeWhile1[F[_]](
      label: Option[String],
      f: Char => Boolean
  ): ParsecT[F, String] = {
    val _split = Parse.State._input composeGetter Parse.State._split(f)
    ParsecT(
      λ[
        FunctionK[λ[
          B => (
              Parse.State,
              Parse.ContOk[F, String, B],
              Parse.ContErr[F, B],
              Parse.ContOk[F, String, B],
              Parse.ContErr[F, B]
          )
        ], F]
      ] {
        case (state, ok, _, eok, eerr) =>
          val (c, s) = _split.get(state)
          val hs = label
            .map(l => new Parse.Hints(Set(Parse.Error.Item.Label(l))))
            .getOrElse(Parse.Hints.empty)
          val g =
            (Parse.State._input.set(s) andThen Parse.State._offset
              .modify(
                _ + c.length
              ))
          Parse.State._input composeGetter Parse.State._split(f)
          if (c.isEmpty) {
            eerr(
              Parse.Error(
                Parse.State._offset.get(state),
                Parse.Error.Item.EndOfInput.some,
                label
                  .map(l => Set[Parse.Error.Item](Parse.Error.Item.Label(l)))
                  .getOrElse(Set())
              ),
              g(state)
            )
          } else {
            ok(c, g(state), hs)
          }
      }
    )
  }

  implicit def monadParsecT[F[_]]
      : Monad[ParsecT[F, ?]] with Alternative[ParsecT[F, ?]] =
    new Monad[ParsecT[F, ?]] with Alternative[ParsecT[F, ?]] {
      def empty[A]: ParsecT[F, A] = ParsecT.never

      def combineK[A](x: ParsecT[F, A], y: ParsecT[F, A]): ParsecT[F, A] =
        x.combineK(y)

      def pure[A](a: A): ParsecT[F, A] =
        ParsecT.pure(a)

      override def map[A, B](fa: ParsecT[F, A])(f: A => B): ParsecT[F, B] =
        fa.map(f)

      def flatMap[A, B](fa: ParsecT[F, A])(
          f: A => ParsecT[F, B]
      ): ParsecT[F, B] = fa.flatMap(f)
      def tailRecM[A, B](a: A)(
          f: A => ParsecT[F, Either[A, B]]
      ): ParsecT[F, B] = {
        ParsecT[F, B](
          new FunctionK[λ[
            AA => (
                Parse.State,
                Parse.ContOk[F, B, AA],
                Parse.ContErr[F, AA],
                Parse.ContOk[F, B, AA],
                Parse.ContErr[F, AA]
            )
          ], F] {

            def apply[AA](
                fa: (
                    Parse.State,
                    (B, Parse.State, Parse.Hints) => F[AA],
                    (Parse.Error, Parse.State) => F[AA],
                    (B, Parse.State, Parse.Hints) => F[AA],
                    (Parse.Error, Parse.State) => F[AA]
                )
            ): F[AA] = {
              fa match {
                case (state, ok, err, eok, eerr) =>
                  def go(a: A, s: Parse.State): F[AA] =
                    //TODO: Use Cont with depth
                    f(a).unParser(
                      (
                        s,
                        (a, ss, hs) =>
                          a.bimap(
                              aa => go(aa, ss),
                              b => ok(b, ss, hs)
                            )
                            .merge,
                        (e, ss) => err(e, ss),
                        (a, ss, hs) =>
                          a.bimap(
                              aa => go(aa, ss),
                              b => eok(b, ss, hs)
                            )
                            .merge,
                        (e, ss) => eerr(e, ss)
                      )
                    )
                  go(a, state)
              }
            }
          }
        )
      }
    }

  def satisfy[F[_]](f: Char => Boolean): ParsecT[F, Char] =
    ParsecT.token(Set()) { c => if (f(c)) c.some else None }

  def single[F[_]](c: Char): ParsecT[F, Char] =
    ParsecT.token(Set(Parse.Error.Item.Token(c))) { cc =>
      if (c === cc) c.some else none
    }

}

case class ParsecT[F[_], A](
    unParser: Parse.Cont[F, A]
) { self =>

  def optional: ParsecT[F, Option[A]] =
    self.map(_.some).combineK(ParsecT.pure(none))

  def kleene(implicit M: Monoid[A]): ParsecT[F, A] = {
    def go(f: A => A): ParsecT[F, A] =
      self.optional.flatMap {
        _.map(x => go(f(x) |+| _)).getOrElse(ParsecT.pure(f(M.empty)))
      }
    go(identity)
  }

  def kleenePlus(implicit M: Monoid[A]): ParsecT[F, A] = {
    def go(f: A => A): ParsecT[F, A] =
      self.optional.flatMap {
        _.map(x => go(f(x) |+| _)).getOrElse(ParsecT.pure(f(M.empty)))
      }
    self.map2(go(identity))(_ |+| _)
  }

  def repeat(i: Int): ParsecT[F, List[A]] =
    if (i <= 0)
      ParsecT.pure(List())
    else
      self.replicateA(i)

  def list: ParsecT[F, List[A]] =
    self.map(_.pure[List]).kleene

  def nel: ParsecT[F, NonEmptyList[A]] =
    self.map2(self.list)(NonEmptyList(_, _))

  def count1: ParsecT[F, Int] =
    self.map(const(1)).map2(self.map(const(1)).kleenePlus)(_ + _)

  def label(l: String): ParsecT[F, A] =
    ParsecT(
      λ[
        FunctionK[λ[
          AA => (
              Parse.State,
              Parse.ContOk[F, A, AA],
              Parse.ContErr[F, AA],
              Parse.ContOk[F, A, AA],
              Parse.ContErr[F, AA]
          )
        ], F]
      ] {
        case (state, ok, err, eok, eerr) =>
          val eitem = Parse.Error.Item.Label(l)
          unParser(
            state,
            (a, s, h) => ok(a, s, Parse.Hints(eitem)),
            err,
            (a, s, h) => eok(a, s, Parse.Hints(eitem)),
            eerr
          )
      }
    )

  def lookAhead: ParsecT[F, A] =
    ParsecT(
      λ[
        FunctionK[λ[
          AA => (
              Parse.State,
              Parse.ContOk[F, A, AA],
              Parse.ContErr[F, AA],
              Parse.ContOk[F, A, AA],
              Parse.ContErr[F, AA]
          )
        ], F]
      ] {
        case (state, _, err, eok, eerr) =>
          unParser(
            state,
            (a, _, h) => eok(a, state, Parse.Hints.empty),
            err,
            (a, _, h) => eok(a, state, Parse.Hints.empty),
            eerr
          )

      }
    )

  def attemptF(recv: F[Unit])(implicit F: Applicative[F]): ParsecT[F, A] =
    ParsecT(
      λ[
        FunctionK[λ[
          AA => (
              Parse.State,
              Parse.ContOk[F, A, AA],
              Parse.ContErr[F, AA],
              Parse.ContOk[F, A, AA],
              Parse.ContErr[F, AA]
          )
        ], F]
      ] {
        case (state, ok, err, eok, eerr) =>
          unParser(
            (
              state,
              ok,
              (e, _) => recv *> eerr(e, state),
              eok,
              (e, _) => recv *> eerr(e, state)
            )
          )

      }
    )

  def compile(
      implicit F: Applicative[F]
  ): StateT[F, Parse.State, Parse.Reply[A]] =
    StateT[F, Parse.State, Parse.Reply[A]] { initial =>
      unParser(
        (
          initial,
          (a, ss, _) => (ss, Parse.Reply.consumed(a.asRight)).pure[F],
          (err, ss) => (ss, Parse.Reply.untouched(err.asLeft[A])).pure[F],
          (a, ss, _) => (ss, Parse.Reply.untouched(a.asRight)).pure[F],
          (err, ss) => (ss, Parse.Reply.consumed(err.asLeft[A])).pure[F]
        )
      )

    }

  private def accHints[F[_], A, B](
      hints: Parse.Hints,
      f: Parse.ContOk[F, A, B]
  ): Parse.ContOk[F, A, B] =
    (a, s, hs) => f(a, s, hints |+| hs)

  private def withHints[F[_], A, B](
      hints: Parse.Hints,
      f: Parse.ContErr[F, B]
  ): Parse.ContErr[F, B] =
    (err, s) => f(Parse.Error._expected.modify(_ |+| hints.items)(err), s)

  def map[B](f: A => B): ParsecT[F, B] =
    ParsecT(
      λ[
        FunctionK[λ[
          AA => (
              Parse.State,
              Parse.ContOk[F, B, AA],
              Parse.ContErr[F, AA],
              Parse.ContOk[F, B, AA],
              Parse.ContErr[F, AA]
          )
        ], F]
      ] {
        case (state, ok, err, eok, eerr) =>
          unParser(
            (
              state,
              (b, s, h) => ok(f(b), s, h),
              err,
              (b, s, h) => eok(f(b), s, h),
              eerr
            )
          )
      }
    )

  def combineK(y: ParsecT[F, A]): ParsecT[F, A] =
    ParsecT(
      λ[
        FunctionK[λ[
          B => (
              Parse.State,
              Parse.ContOk[F, A, B],
              Parse.ContErr[F, B],
              Parse.ContOk[F, A, B],
              Parse.ContErr[F, B]
          )
        ], F]
      ] {
        case (state, ok, err, eok, eerr) =>
          unParser(
            (
              state,
              ok,
              err,
              eok,
              (e, s) =>
                y.unParser(
                  (
                    state,
                    ok,
                    (ee, ss) => err(e |+| ee, s.max(ss)),
                    (a, ss, h) =>
                      eok(
                        a,
                        ss,
                        if (e.offset === ss.offset)
                          new Parse.Hints(h.items ++ e.expected)
                        else h
                      ),
                    (ee, ss) => eerr(e |+| ee, s.max(ss))
                  )
                )
            )
          )
      }
    )

  def flatMap[B](f: A => ParsecT[F, B]): ParsecT[F, B] =
    ParsecT(
      λ[
        FunctionK[λ[
          AA => (
              Parse.State,
              Parse.ContOk[F, B, AA],
              Parse.ContErr[F, AA],
              Parse.ContOk[F, B, AA],
              Parse.ContErr[F, AA]
          )
        ], F]
      ] {
        case (state, ok, err, eok, eerr) =>
          unParser(
            (
              state,
              (a, s, h) =>
                f(a)
                  .unParser((s, ok, err, accHints(h, eok), withHints(h, eerr))),
              err,
              (a, s, h) =>
                f(a)
                  .unParser((s, ok, err, accHints(h, eok), withHints(h, eerr))),
              eerr
            )
          )
      }
    )

}

object Text {

  type Parser[A] = ParsecT[State[Source, ?], A]

  val state: Parser[Parse.State] = ParsecT.state

  case class Source(line: Int, column: Int)

  object Source {

    def apply(): Source = Source(0, 0)

    val _column = GenLens[Source](_.column)
    val _line = GenLens[Source](_.line)

    def incrN(n: Int): Parser[Unit] =
      ParsecT.liftF(State.modify[Source](Source._column.modify(_ + n)))

    val incr: Parser[Unit] = incrN(1)
    val source = ParsecT.liftF(State.get[Source])
    val column = source.map(_.column)

  }

  val eof = ParsecT.eof[State[Source, ?]]

  def char(c: Char): Parser[Char] = {
    ParsecT.single[State[Source, ?]](c) <* Source.incr
  }

  def tab: Parser[Char] =
    char('\t') <* Source.incrN(4)

  def space: Parser[Char] =
    char(' ')

  def whitespace: Parser[Char] =
    space <+> tab

  def string(s: String): Parser[String] =
    ParsecT.tokens[State[Source, ?]](s)((_, ss) => s === ss) <* Source.incrN(
      s.length
    )

  def integer: Parser[Int] = digits.map(_.toInt)

  def takeWhile(label: Option[String], f: Char => Boolean): Parser[String] =
    for {
      s <- ParsecT.takeWhile[State[Source, ?]](label, f)
      _ <- Source.incrN(s.length)
    } yield s

  def takeWhile1(label: Option[String], f: Char => Boolean): Parser[String] =
    for {
      s <- ParsecT.takeWhile1[State[Source, ?]](label, f)
      _ <- Source.incrN(s.length)
    } yield s

  def digits: Parser[String] =
    takeWhile1("digit".some, c => c >= '0' && c >= '9')

  def attempt[A](parser: Parser[A]): Parser[A] =
    for {
      s <- Source.source
      v <- parser.attemptF(State.set[Source](s))
    } yield v

  def whileNoneOf1(cs: Char*): Parser[String] =
    takeWhile1(none, !cs.toSet.contains(_))

  def whileNoneOf(cs: Char*): Parser[String] =
    takeWhile(none, !cs.toSet.contains(_))

  def between[A](begin: Parser[_], end: Parser[_])(
      parse: Parser[A]
  ): Parser[A] =
    begin *> parse <* end

  def betweenLazy[A](begin: Parser[_], end: Parser[_])(
      parse: => Parser[A]
  ): Parser[A] =
    for {
      _ <- begin
      a <- parse
      _ <- end
    } yield a

  //TODO: Windows?
  def newline: Parser[Unit] =
    ParsecT.single[State[Source, ?]]('\n').void <* ParsecT.liftF(
      State.modify[Source](
        Source._line.modify(_ + 1) andThen Source._column.set(0)
      )
    )

}

object PollenParsers {
  import Text._
  import Pollen._

  private val lozenge = char('◊').void
  private val command = whileNoneOf1('{')
  private val open = char('{').void
  private val close = char('}').void

  private val contents: Parser[Pollen] = whileNoneOf1('◊', '}').map(Contents(_))

  private[pollen] def tag: Parser[Pollen.Tag] = for {
    command <- lozenge *> command
    _ <- open
    contents <- contents
    .combineK(tag.map(identity))
    .list
    _ <- close
    _ <- newline.kleene
  } yield Tag(command, contents.filterNot {
    case Pollen.Contents(c) => c.trim.isEmpty
    case _ => false
  })

  def tags: Parser[NonEmptyList[Pollen.Tag]] = tag.nel
}
