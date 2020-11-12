package lsug

import munit.{Tag => _, _}
import cats._
import cats.data._
import cats.implicits._

trait LsugSuite extends FunSuite { self =>
  final class TestBuilder(body: => Unit, messages: Chain[String]) {
    def build(message: String): Unit =
      test(
        (messages :+ message).toList
          .reduce(Semigroup[String].intercalate(" - ").combine)
      )(body)
    def label(message: String): TestBuilder =
      new TestBuilder(body, message +: messages)
  }

  def builder(body: => Unit): TestBuilder =
    new TestBuilder(body, Chain.empty)
}
