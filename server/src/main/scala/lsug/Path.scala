package lsug

import java.nio.file.Path

final class PathOps(val path: Path) extends AnyVal {

  def baseName: String =
    path.toString.replaceFirst("[.][^.]+$", "")

}

trait PathImplicits {
  implicit def pathToPathOps(path: Path): PathOps =
    new PathOps(path)
}
