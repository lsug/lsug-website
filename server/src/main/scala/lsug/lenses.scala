package lsug

import cats._
import cats.data._
import cats.implicits._
import java.time.{LocalDateTime, LocalTime}

import monocle.{Lens, Prism, Traversal, Getter, Optional}
import monocle.macros.{GenLens, GenPrism}
import monocle.std.option.{some => _some}
import monocle.function.Index.{index => _index}
import monocle.function.At.{at => _at}
import monocle.function.Cons.{headOption => _headOption}

import java.time.format.DateTimeParseException
import java.time.format.DateTimeFormatter

object lenses {

  import protocol._

  val _paragraph = GenPrism[Markup, Markup.Paragraph]

  object paragraph {
    val _text = GenLens[Markup.Paragraph](_.text)
  }

  val _localDateTime = Optional[String, LocalDateTime] { s =>
    try {
      LocalDateTime.parse(s).some
    } catch {
      case _: DateTimeParseException => none
    }
  }(t => s => t.format(DateTimeFormatter.ISO_DATE_TIME))

  val _localTime = Optional[String, LocalTime] { s =>
    try {
      //TODO: fix
      LocalTime.parse(s.trim()).some
    } catch {
      case _: DateTimeParseException => none
    }
  }(t => s => t.format(DateTimeFormatter.ISO_TIME))

  val _section = GenPrism[Markup, Markup.Section]

  def _eachl[A] = Traversal.fromTraverse[List, A]
  def _eachNel[A] = Traversal.fromTraverse[NonEmptyList, A]
  def _headl[A] = _headOption[List[A], A]
  def _indexl[A](i: Int) = _index[List[A], Int, A](i)
  def _indexNel[A](i: Int) = _index[NonEmptyList[A], Int, A](i)

  def _nsection(header: String) =
    Getter(
      (_eachl[Markup] ^<-? _section)
        .find(section._heading.find(_.string.trim === header).andThen(_.isDefined))
    ) ^<-? _some

  object section {
    val _heading = GenLens[Markup.Section](_.heading)
    val _content = GenLens[Markup.Section](_.content)
  }

  object text {
    val _plain = GenPrism[Markup.Text, Markup.Text.Plain]
    val _plainValue = _plain ^|-> plain._value

    object plain {
      val _value = GenLens[Markup.Text.Plain](_.value)
    }
  }

  val _table = GenPrism[Markup, Markup.Table]

  object table {
    val _headings = GenLens[Markup.Table](_.headings) ^|->> _eachNel[Markup.Text]
    def _columnIndex(name: String): Getter[Markup.Table, Option[Int]] = Getter { t =>
      val headings = _eachNel[Markup.Text] ^<-? text._plain ^|-> text.plain._value
      headings.getAll(t.headings).zipWithIndex.collectFirst {
        case (c, i) if c.trim === name => i
      }
    }
    val _rows = GenLens[Markup.Table](_.rows) ^|->> _eachl[Markup.Table.Row]
  }

}
