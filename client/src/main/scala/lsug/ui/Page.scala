package lsug
package ui

sealed trait Page

object Page {
  case object Home extends Page
  case class Event(id: String) extends Page
}
