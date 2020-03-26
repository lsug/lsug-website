import mill._
import scalalib._

case class NpmDependency(name: String, version: String, global: String)

object NpmDependency {
  implicit def rw: upickle.default.ReadWriter[NpmDependency] =
    upickle.default.macroRW
}

case class Yarn(nodeModules: PathRef, packageJson: PathRef, lockFile: PathRef) {

  import ammonite.ops._
  import ammonite.ops.ImplicitWd._

  def %(cmd: Shellable*)(wd: Path) = {
    os.copy.over(packageJson.path, wd / "package.json")
    os.copy.over(lockFile.path, wd / "yarn.lock")
    val exec = Command(
      (Vector[Shellable]("yarn", "--modules-folder", nodeModules.path) ++ cmd.toVector)
        .map(_.s.toVector)
        .reduce(_ ++ _),
      Map.empty,
      Shellout.executeInteractive
    )
    exec()(wd)
  }

}

object Yarn {
  implicit def rw: upickle.default.ReadWriter[Yarn] = upickle.default.macroRW
}

trait WebpackModule extends Module {

  import ammonite.ops._
  import ammonite.ops.ImplicitWd._

  def webpackVersion: T[String]
  def webpackCliVersion: T[String]
  def webpackDevServerVersion: T[String]
  def development: T[Boolean]
  def sassVersion: T[String]
  def npmDeps: T[Agg[NpmDependency]]
  def devDependencies: T[Agg[(String, String)]] = T { Agg() }
  def mainJS: T[PathRef]

  def entryPoint = T {
    val path = T.ctx.dest / "entrypoint.js"

    val require = npmDeps()
      .map {
        case NpmDependency(n, _, g) => s"global['$g'] = require('$n')"
      }
      .mkString("\n")

    os.write.over(
      path,
      s"""
         |$require
         |""".stripMargin.trim
    )
    PathRef(path)
  }

  def yarn = T.persistent {
    val path = T.ctx.dest / "package.json"
    val lock = T.ctx.dest / "yarn.lock"
    os.write.over(
      path,
      ujson
        .Obj(
          "dependencies" -> ujson.Obj
            .from(npmDeps().map(d => (d.name -> ujson.Str(d.version)))),
          "devDependencies" -> (
            Seq(
              "webpack" -> webpackVersion(),
              "webpack-cli" -> webpackCliVersion(),
              "webpack-dev-server" -> webpackDevServerVersion(),
              "node-sass" -> sassVersion(),
              "source-map-loader" -> "0.2.3"
            ) ++ devDependencies()
          )
        )
        .render(2)
    )
    Yarn(
      PathRef(T.ctx.dest / "node_modules"),
      PathRef(path),
      PathRef(lock)
    )
    %('yarn, "install")(wd = T.ctx.dest)
    Yarn(
      PathRef(T.ctx.dest / "node_modules"),
      PathRef(path),
      PathRef(lock)
    )
  }

  def stylesheets = T.sources {
    os.walk(millSourcePath).filter(_.ext == "scss").map(PathRef(_))
  }

  def assets = T.source {
    PathRef(millSourcePath / "assets")
  }

  def index = T.source {
    PathRef(millSourcePath / "index.html")
  }

  def sass = T {
    //TODO: main stylesheet
    val main = stylesheets().apply(0)
    val out =
      T.ctx.dest / s"${main.path.last.split('.')(0)}.css"
    yarn().%("node-sass", main.path, out)(T.ctx.dest)
    PathRef(out)
  }

  def vendor = T {
    val config = T.ctx.dest / "webpack.config.js"
    os.write.over(
      config,
      "module.exports = " + ujson
        .Obj(
          "resolve" -> ujson.Obj(
            "alias" -> ujson.Obj.from(
              npmDeps().map(d =>
                (d.name -> ujson
                  .Str((yarn().nodeModules.path / d.name).toString))
              )
            )
          ),
          "mode" -> (if (development()) "development" else "production"),
          "devtool" -> "source-map",
          "entry" -> entryPoint().path.toString,
          "output" -> ujson.Obj(
            "filename" -> "vendor.js"
          )
        )
        .render(2) + ";\n"
    )
    yarn().%("webpack")(T.ctx.dest)
    Seq(
      PathRef(T.ctx.dest / "dist" / "vendor.js")
    ) ++ (if (development()) Seq(PathRef(T.ctx.dest / "dist" / "vendor.js.map"))
          else Seq())
  }

  def bundle = T {
    vendor().map { p => os.copy.into(p.path, T.ctx.dest) }
    os.copy.into(sass().path, T.ctx.dest)
    os.copy.into(assets().path, T.ctx.dest)
    os.copy.into(index().path, T.ctx.dest)
    os.copy.into(mainJS().path, T.ctx.dest)
    PathRef(T.ctx.dest)
  }

}
