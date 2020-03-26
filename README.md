# LSUG website

## Overview

This is the source code for the [London Scala User Group](https://www.lsug.org) website which
is a non-profit [CIC (Community Interest Company)](https://www.gov.uk/government/organisations/office-of-the-regulator-of-community-interest-companies).
Both the HTTP server and website are written using the [Scala Programming Language](https://www.scala-lang.org/)
and are licensed under the copyleft license [GPLv3](https://www.gnu.org/licenses/gpl-3.0.en.html).

## Getting started

This project requires [mill](http://www.lihaoyi.com/mill/), [npm](https://www.npmjs.com/) and [yarn](https://yarnpkg.com/)
to run. You'll also need [git](https://git-scm.com/) to clone the repository and ideally [metals](https://scalameta.org/metals/)
set up with [Visual Studio Code](https://code.visualstudio.com/), [IntelliJ IDEA](https://www.jetbrains.com/idea/) or an IDE
of your choice.

Once setup with the prerequisites, you can run the HTTP server locally on [localhost](http://localhost:8080) with:

```sh
mill -w web.run
```

This will start a background process, so that any changes you make will be reflected in the running server.

If you're only change the website and not the HTTP server, you can run the following to keep the HTTP server alive
when the website updates:

```sh
mill web.run
mill -w web.bundle
```

The project has four modules,

 - `client` which contains the website code
 - `server` which is responsible for serving the website and HTTP resources
 - `protocol` which contains the code shared between the two
 - `web` which is used for deployment and contains certain static assets

## Design

The LSUG website has been written in [idiomatic functional scala](https://en.wikipedia.org/wiki/Functional_programming).
The number of dependencies has been deliberately kept low and certain design decisions to provide a good
case study of how to use certain scala constructs and libraries. Performance is of a lesser concern.

Certain parts of the codebase are deliberately more or less advanced. Where possible, these
are segregated into their own packages

| Package           | Description                                                                                                           | Difficulty     |
| ----------------- | --------------------------------------------------------------------------------------------------------------------- | -------------- |
| `lsug.parsec`     | Port of [megaparsec](https://hackage.haskell.org/package/megaparsec) to demonstrate CPS and Monad Transformers        | Advanced       |
| `lsug.decoders`   | Decoders using [monocle lenses](https://julien-truffaut.github.io/Monocle/) to demonstrate optics                     | Intermediate   |
| `lsug.http`       | Using [http4s](https://http4s.org/) to create a web server                                                            | Beginner       |
| `lsug.meetup`     | Using [http4s](https://http4s.org/) to call APIs                                                                      | Beginner       |



## Code of Conduct

The project is maintained by the organizers of LSUG and we are committed to providing a friendly,
safe and welcoming environment to everyone. We ask that the community adheres to the Scala Code
of Conduct.


## Acknowledgements

Many thanks to the many library authors and maintainers which have made this project possible.
