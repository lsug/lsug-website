package lsug
package ui

import io.circe._
import io.circe.parser._

import cats.data.EitherT
import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.Ajax

object Resource {
  def apply[A : Decoder](path: String): EitherT[AsyncCallback, Error, A] =
    EitherT(
      Ajax("GET", s"/api/${path}").send.asAsyncCallback
        .map { xhr =>
          parse(xhr.responseText)
            .flatMap(
              _.as[A]
            )
        }
    )
}
