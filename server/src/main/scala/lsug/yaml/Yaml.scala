package lsug
package yaml

import cats.implicits._
import monocle._
import monocle.macros.{GenLens, GenPrism}
import monocle.function.At.at
import monocle.std.option.{some => _some}

sealed trait Yaml

object Yaml {

  case class Str(value: String) extends Yaml

  object Str {
    val _value = GenLens[Str](_.value)
  }

  case class Obj(values: Map[String, Yaml]) extends Yaml

  object Obj {
    val _values = GenLens[Yaml.Obj](_.values)
    val empty: Obj = Obj(Map.empty)
  }


  case class Arr(items: List[Yaml]) extends Yaml

  object Arr {
    val _items = GenLens[Yaml.Arr](_.items)
  }

  val _str = GenPrism[Yaml, Yaml.Str]
  val _obj = GenPrism[Yaml, Yaml.Obj]
  val _arr = GenPrism[Yaml, Yaml.Arr]

  def _eachl[A] = Traversal.fromTraverse[List, A]

  val _arrItems = _arr ^|-> Yaml.Arr._items ^|->> _eachl[Yaml]
  val _strValue = _str ^|-> Yaml.Str._value
  def _objKey(k: String) = Yaml.Obj._values ^|-> at(k) ^<-? _some


}
