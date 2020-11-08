package lsug
package markup
package context

// A context free decoder

sealed trait Error

object Error {
  final case object EmptyTagFound extends Error
  final case class NestedTagsFound(elements: List[Markup]) extends Error
}

sealed trait Tree

object Tree {
  final case class NotFound(name: String, contents: List[Tree]) extends Tree
  final case class Markup(element: PMarkup) extends Tree
  final case class Text(text: String) extends Tree
}

object ContextFree {
  type TagName = String
  type Decoder = List[Tree] => Either[Error, Markup]
  type Context = Map[TagName, Decoder]

  def singleText(elements: List[Tree]): Either[Error, String] =
    elements match {
      case Tree.Text(text) :: Nil => Right(text)
      case Nil => Left(Error.EmptyTagFound)
      case _ => Left(Error.NestedTagsFound(elements))
    }

  def strong: Decoder = { elements =>
    singleText(elements).map(Markup.Styled.Strong)
  }

  def url: Decoder = { elements =>
    singleText(elements).map(Markup.Text.Plain(_))
  }


}
