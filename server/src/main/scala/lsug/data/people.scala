package lsug
package data

import protocol._
import protocol.Speaker.Id
import cats.data.NonEmptyList

object people {
  val DanielaSfregola = Speaker(
    profile = Speaker.Profile(
      id = new Id("danielasfregola"),
      name = "Daniela Sfregola",
      photo = Some(new Asset("danielasfregola.png"))
    ),
    bio = Nil,
    socialMedia = Speaker.SocialMedia(
      blog = None,
      twitter = Some(new Twitter.Handle("danielasfregola")),
      github = None
    )
  )

  val JoeWarren = Speaker(
    profile = Speaker.Profile(
      id = new Id("joewarren"),
      name = "Joe Warren",
      photo = Some(new Asset("joewarren.png"))
    ),
    bio = List(
      Markup.Paragraph(
        NonEmptyList.of(
          Markup.Text.Plain(
        """Joe Warren is a software developer at Deliveroo. He likes
    | functional programming in Scala and Haskell, learning about new types of
    | Functors, and makes his own shirts.""".stripMargin
        )
        )
      )
    ),
    socialMedia = Speaker.SocialMedia(
      blog = None,
      twitter = Some(new Twitter.Handle("hungryjoewarren")),
      github = None
    )
  )

  val YilinWei = Speaker(
    profile = Speaker.Profile(
      id = new Id("yilinwei"),
      name = "Yilin Wei",
      photo = Some(new Asset("yilinwei.jpg"))
    ),
    bio = List(
      Markup.Paragraph(
        NonEmptyList.of(
          Markup.Text.Plain("Yilin is a software developer")
        )
      )),
    socialMedia = Speaker.SocialMedia(
      blog = None,
      twitter = Some(new Twitter.Handle("_YilinWei_")),
      github = Some(new Github.User("yilinwei"))
    )
  )

  val ZainabAli = Speaker(
    profile = Speaker.Profile(
      id = new Id("zainabali"),
      name = "Zainab Ali",
      photo = Some(new Asset("zainabali.jpg"))
    ),
    bio = Nil,
    socialMedia = Speaker.SocialMedia(
      blog = None,
      twitter = None,
      github = None
    )
  )

  val BrunoBonnano = Speaker(
    profile = Speaker.Profile(
      id = new Id("brunobonnano"),
      name = "Bruno Bonnano",
      photo = None
    ),
    bio = Nil,
    socialMedia = Speaker.SocialMedia(
      blog = None,
      twitter = None,
      github = None
    )
  )

  val TamerAbdulradi = Speaker(
    profile = Speaker.Profile(
      id = new Id("tamerabdulradi"),
      name = "Tamer Abdulradi",
      photo = None
    ),
    bio = Nil,
    socialMedia = Speaker.SocialMedia(
      blog = None,
      twitter = None,
      github = None
    )
  )

  val all: NonEmptyList[Speaker] = NonEmptyList.of(
    DanielaSfregola,
    JoeWarren,
    ZainabAli,
    YilinWei,
    BrunoBonnano,
    TamerAbdulradi
  )
}