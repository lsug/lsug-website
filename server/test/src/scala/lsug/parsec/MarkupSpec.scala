package lsug
package parsec

import munit._
import cats.implicits._
import cats.data._

class MarkupSpec extends FunSuite {

  implicit val parser = Markup.markup

  import lsug.protocol.{Markup => M}

  def assertParseResult[A](s: String, a: A)(
      implicit parser: Text.Parser[A],
      loc: Location
  ): Unit = {
    val reply = parser.compile
      .runA(
        Parse.State(
          s
        )
      )
      .run(
        Text.Source()
      )
      .value
    assertEquals(reply._2.result, Right(a))
  }

  test("code block") {

    val code = "val x  = 1"
    val lang = "scala"
    assertParseResult[M](
      s"""```${lang}
            |${code}
            |```
            |""".stripMargin,
      M.CodeBlock(lang, code.pure[NonEmptyList])
    )
  }

  val nl = M.Text.Plain("\n")

  test("link") {
    val text = "link text"
    val link = "https://somelink.org"
    assertParseResult[M](
      s"[${text}](${link})\n",
      M.Paragraph(NonEmptyList(M.Text.Link(text, link), nl.pure[List]))
    )
  }

  test("pre") {
    val code = "val x = 1"
    assertParseResult[M](
      s"`${code}`\n",
      M.Paragraph(
        NonEmptyList(M.Text.Styled.Code(code), nl.pure[List])
      )
    )
  }

  test("styled") {
    val bold = "moo "
    val plain = "plain "
    val italicBold = "baz"
    assertParseResult[M](
      s"${plain}**${bold}__${italicBold}__**\n",
      M.Paragraph(
        NonEmptyList(
          M.Text.Plain(plain),
          List(
            M.Text.Styled.Italic(
              NonEmptyList(
                M.Text.Plain(bold),
                List(
                  M.Text.Styled
                    .Strong(M.Text.Plain(italicBold).pure[NonEmptyList])
                )
              )
            ),
            nl
          )
        )
      )
    )
  }

  test("headings") {
    val heading = "moo"
    val content = "lorem ipsum"
    assertParseResult[M](
      s"# ${heading}\n${content}\n",
      M.Section(
        M.Text.Plain(heading),
        List(M.Paragraph(NonEmptyList(M.Text.Plain(content), nl.pure[List])))
      )
    )
  }

  test("table") {
    val md = "# Agenda\nfoo\n| foo | bar |\n| --- | --- |\n| 32  | 41  |\n"

    assertParseResult[M](
      md,
      M.Section(
        M.Text.Plain("Agenda"),
        List(
          M.Text.Plain("foo"),
          M.Table(
            NonEmptyList(M.Text.Plain("foo"), M.Text.Plain("bar").pure[List]),
            List(
              M.Table.Row(
                NonEmptyList(
                  M.Text.Plain("32"),
                  M.Text.Plain("41").pure[List]
                )
              )
            )
          )
        )
      )
    )

  }

}
