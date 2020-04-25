package lsug

import yaml.Yaml
import cats.data.NonEmptyList
import cats.implicits._

import monocle.std.option.{some => _some}

import Function.const

object decoders {

  import protocol._

  import lenses._
  import monocle.function.At.at
  import monocle.function.Cons.{headOption => _headOption}
  import monocle.Optional

  import Decoder.Result

  final class DecoderOptionOps[A](val a: Option[A]) extends AnyVal {
    def result(err: => String): Result[A] =
      a.map(Result.Success(_)).getOrElse(Result.Failure(err))
  }

  implicit def optionToDecoderOptionOps[A](a: Option[A]): DecoderOptionOps[A] =
    new DecoderOptionOps(a)

  object venue {
    val summary: Decoder[Venue.Id => Venue.Summary] = {
      val _name =
        Yaml._objKey("name") ^|-? Yaml._strValue
      val _address =
        Yaml._objKey("address") ^|->> Yaml._arrItems ^|-? Yaml._strValue

      Decoder.meta { yaml =>
        for {
          name <- _name.getOption(yaml).result("missing name")
          address <- _address.getAll(yaml).toNel.result("missing address")
        } yield id => Venue.Summary(id, name, address)
      }
    }

  }

  object speaker {
    val profile: Decoder[Speaker.Id => Speaker.Profile] = {

      val _name =
        Yaml._objKey("name") ^|-? Yaml._strValue

      val _pic = Yaml._objKey("photo") ^|-? Yaml._strValue

      Decoder.meta { yaml =>
        for {
          name <- _name.getOption(yaml).result("missing name")
          pic <- _pic
            .getOption(yaml)
            .map(Asset.fromPath)
            .map(Result.fromEither)
            .map(_.map(_.some))
            .getOrElse(none.pure[Result])
        } yield (id: Speaker.Id) => Speaker.Profile(id, name, pic)
      }
    }

    val speaker: Decoder[Speaker.Id => Speaker] = {

      def _social(media: String) =
        Yaml._objKey("social") ^<-? Yaml._obj ^|-? Yaml._objKey(media) ^|-? Yaml._strValue

      Decoder.instance { (yaml, markup) =>
        for {
          prof <- profile(yaml.some, markup)
        } yield (
            (id: Speaker.Id) =>
              Speaker(
                prof(id),
                (_nsection("Bio") ^|-> section._content ^|->> _eachl)
                  .getAll(markup),
                Speaker.SocialMedia(
                  _social("blog").getOption(yaml).map(new Link(_)),
                  _social("twitter").getOption(yaml).map(new Twitter.Handle(_)),
                  _social("github").getOption(yaml).map(new Github.User(_))
                )
              )
        )
      }
    }

  }

  object event {

    val blurb: Decoder[String => Event.Blurb] = {
      val _speakers =
        Yaml.Obj._values ^|-> at("speakers") ^<-? _some ^<-? Yaml._arr ^|-> Yaml.Arr._items ^|->> _eachl[
          Yaml
        ] ^<-? Yaml._str ^|-> Yaml.Str._value
      val _speaker =
        (Yaml.Obj._values ^|-> at("speaker") ^<-? _some ^|-? Yaml._strValue).asTraversal

      val _blurb = _nsection("Blurb") ^|-> section._content

      val _tags = Yaml._objKey("tags") ^|->> Yaml._arrItems ^|-? Yaml._strValue
      Decoder.instance { (yaml, markup) =>
        (
            (n: String) =>
              Event.Blurb(
                n,
                _blurb.getAll(markup).flatten,
                (_speakers.getAll(yaml) |+| _speaker.getAll(yaml))
                  .map(new Speaker.Id(_)),
                _tags.getAll(yaml)
              )
        ).pure[Result]

      }
    }

    val item: Decoder[String => Event.Item] = {

      val _slides = Yaml._objKey("slides") ^|-? Yaml._strValue
      val _recording = Yaml._objKey("recording") ^|-? Yaml._strValue
      val _photos =
        Yaml._objKey("photos") ^|->> Yaml._arrItems ^|-? Yaml._strValue

      Decoder.decoder { (meta, m) =>
        for {
          b <- blurb(meta, m)
        } yield (s: String) =>
          Event.Item(
            b(s),
            (_nsection("Setup") ^|-> section._content ^|->> _eachl).getAll(m),
            meta.flatMap(_slides.getOption).map(new Link(_)),
            meta.flatMap(_recording.getOption).map(new Link(_)),
            meta
              .flatMap(
                _photos.getAll(_).map(Asset.fromPath(_)).traverse(_.toOption)
              )
              .getOrElse(List())
          )
      }
    }

    def summary[A](
        each: Decoder[String => A]
    ): Decoder[Event.Id => Event.Summary[A]] = {

      val _time = Yaml._objKey("time") ^<-? Yaml._obj
      val _startTime =
        Yaml._objKey("start") ^|-? Yaml._strValue ^|-? _localDateTime
      val _endTime = Yaml._objKey("end") ^|-? Yaml._strValue ^|-? _localDateTime
      val _location = Yaml._objKey("location") ^|-? Yaml._strValue
      val _events = Yaml._objKey("events") ^|->> Yaml._arrItems ^<-? Yaml._obj
      val _name = Yaml._objKey("name") ^|-? Yaml._strValue

      Decoder.instance { (yaml, markup) =>
        val events = _events.getAll(yaml)
        for {
          loc <- _location
            .getOption(yaml)
            .map { l =>
              (if (l === "virtual")
                 Event.Location.Virtual
               else
                 Event.Location.Physical(new Venue.Id(l))),

            }
            .result("missing location")
          t <- _time.getOption(yaml).result("missing time")
          st <- _startTime.getOption(t).result("missing start time")
          et <- _endTime.getOption(t).result("missing end time")
          blurbs <- events.traverse { ev =>
            for {
              n <- _name.getOption(ev).result(s"missing metadata for $ev")
              a <- each(
                ev.some,
                (_nsection(n) ^|-> section._content).getAll(markup).flatten
              )
            } yield a(n)
          }
        } yield { (id: Event.Id) =>
          Event.Summary(
            id,
            Event.Time(st, et),
            loc,
            blurbs
          )
        }
      }
    }

    val schedule: Decoder[Event.Schedule] = {

      val _tbl = _eachl[Markup] ^<-? _table

      Decoder.markup { markup =>
        for {
          tbl <- _tbl.getAll(markup).headOption.result("no table found")
          _start <- table
            ._columnIndex("Start")
            .get(tbl)
            .map(_indexNel[Markup.Text])
            .map(_ ^|-? text._plainValue ^|-? _localTime)
            .result("no start column")
          _item <- table
            ._columnIndex("Item")
            .get(tbl)
            .map(_indexNel[Markup.Text])
            .map(_ ^|-? text._plainValue)
            .result("no item column")
          _end <- table
            ._columnIndex("End")
            .get(tbl)
            .map(_indexNel[Markup.Text])
            .map(_ ^|-? text._plainValue ^|-? _localTime)
            .result("no end column")
          items <- tbl.rows.map(_.columns).traverse { tr =>
            (
              _start.getOption(tr).result("row has no start column"),
              _end.getOption(tr).result("row has no end column"),
              _item.getOption(tr).result("row has no item column")
            ).mapN { (st, et, ev) =>
              Event.Schedule.Item(
                ev,
                st,
                et
              )
            }
          }
          itemsNel <- items.headOption
            .map(NonEmptyList(_, items.tail))
            .result("schedule needs at least a single entry")
        } yield Event.Schedule(itemsNel)
      }
    }

    val virtual: Decoder[Event.Virtual] = {

      val _open = Yaml._objKey("open") ^|-? Yaml._strValue ^|-? _localTime
      val _close = Yaml._objKey("close") ^|-? Yaml._strValue ^|-? _localTime
      val _blackboard = Yaml._objKey("blackboard") ^|-? Yaml._strValue
      val _gitter = Yaml._objKey("gitter") ^|-? Yaml._strValue

      Decoder.meta { yaml =>
        for {
          open <- _open.getOption(yaml).result("missing opening")
          close <- _open.getOption(yaml).result("missing closed")
          providers <- List(
            _blackboard
              .getOption(yaml)
              .map(new Link(_))
              .map(Event.Virtual.Provider.Blackboard(_)),
            _gitter
              .getOption(yaml)
              .map(new Link(_))
              .map(Event.Virtual.Provider.Gitter(_))
          ).collect {
              case Some(e) => e
            }
            .toNel
            .result("no known providers")
        } yield Event.Virtual(
          open,
          close,
          providers
        )
      }
    }

    val event: Decoder[Event.Id => Event[Event.Item]] = {

      val _schedule =
        _nsection("Schedule") ^|-> section._content ^|-? _headl[Markup]

      val _welcome =
        _nsection("Welcome") ^|-> section._content ^|-? _headl[Markup]

      val _virtual =
        Yaml._objKey("virtual") ^<-? Yaml._obj

      val _host =
        Yaml._objKey("host") ^|-? Yaml._strValue

      val _hosts =
        Yaml._objKey("hosts") ^|->> Yaml._arrItems ^|-? Yaml._strValue

      Decoder.instance { (yaml, markup) =>
        for {
          summ <- summary(item)(yaml.some, markup)
          sched <- schedule(none, _schedule.getAll(markup))
          hosts <- (_host.getOption(yaml).map(_.pure[NonEmptyList]) |+| _hosts
            .getAll(yaml)
            .toNel).result("missing host")
          virt <- _virtual
            .getOption(yaml)
            .map(_.some)
            .map(virtual(_, List.empty).map(_.some))
            .getOrElse(none[Event.Virtual].pure[Result])
        } yield (id: Event.Id) =>
          Event(
            hosts.map(new Speaker.Id(_)),
            _welcome.getAll(markup),
            virt,
            summ(id),
            sched
          )
      }
    }
  }

}
