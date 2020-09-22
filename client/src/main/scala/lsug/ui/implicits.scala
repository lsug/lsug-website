package lsug
package ui

import lsug.{protocol => P}
import java.time.LocalDateTime

import cats._
import cats.implicits._
import japgolly.scalajs.react._
import japgolly.scalajs.react.CatsReact._
import java.time._

final class StyleClass(val value: List[String]) extends AnyVal

object StyleClass {

  implicit val styleClassMonoid: Monoid[StyleClass] = new Monoid[StyleClass] {
    val empty: StyleClass = new StyleClass(List.empty)
    def combine(x: StyleClass, y: StyleClass): StyleClass =
      new StyleClass(x.value |+| y.value)
  }

  implicit val styleClassShow: Show[StyleClass] = new Show[StyleClass] {
    def show(x: StyleClass): String =
      x.value.mkString(" ")
  }

  val none = styleClassMonoid.empty

}

final class StringStyleOps(val s: String) extends AnyVal {
  def cls: StyleClass = new StyleClass(List(s))
}

object implicits {

  implicit def stringToStyleClsOps(s: String): StringStyleOps =
    new StringStyleOps(s)

  implicit val eqLocalDateTime: Eq[LocalDateTime] =
    Eq.fromUniversalEquals[LocalDateTime]

  implicit val eqLocalDate: Eq[LocalDate] =
    Eq.fromUniversalEquals[LocalDate]

  implicit val eqMonth: Eq[Month] =
    Eq.fromUniversalEquals[Month]

  implicit val localDate: Reusability[LocalDate] = Reusability.byEq[LocalDate]

  implicit val speakerIdReusability: Reusability[P.Speaker.Id] =
    Reusability.byEq[P.Speaker.Id]
  implicit val profileReusability: Reusability[P.Speaker.Profile] =
    Reusability.byEq[P.Speaker.Profile]
  implicit val localDateTime: Reusability[LocalDateTime] =
    Reusability.byEq[LocalDateTime]
}
