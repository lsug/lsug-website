---
layout: post
title:  "Kata: Optics with Monocle"
date:   2019-10-17 19:04:06 +0100
author: Yilin Wei
categories: workshop kata scala functional
---

# Prerequisites

## Sign up!

- Sign up to the [Meetup](https://www.meetup.com/london-scala/events/260846904/).
- Sign up to [SkillsMatter](https://skillsmatter.com/meetups/13009-london-scala-kata-yilin-wei-optics-with-monocle) so you can enter the building
- Join the [Gitter channel](https://gitter.im/lsug/2019-10-22-kata-optics-with-monocle#)

# Tools

This kata requires some tools to work.  Please try your best to install these before the workshop and reach out in the Gitter channel if you have problems.

- [git](https://git-scm.com/book/en/v2/Getting-Started-Installing-Git)
- [Java 8](https://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html)
- [sbt 1.3.2](https://www.scala-sbt.org/release/docs/Setup.html)
- [Scala 2.13.1](https://www.scala-lang.org/download/)
- [bloop](https://scalacenter.github.io/bloop/setup)
- [The kata repository](https://github.com/lsug/kata-optics-with-monocle.git)

Start the bloop server beforehand, as this will download more dependencies.

### ArchLinux Setup

1. Install the following tools with `pacman`:

   ```console
   lsug$ pacman -S git jdk8-openjdk sbt
   ```
   
   Check that these have been installed
   
   ```console
   lsug$ command -v git java sbt idea
   /usr/bin/git
   /usr/bin/java
   /usr/bin/sbt
   /usr/bin/idea
   ```

2. Install bloop from the [AUR](https://wiki.archlinux.org/index.php/Arch_User_Repository) with your favourite tool (I use `auracle`).

   Start bloop, as this will download more dependencies 
   
   ```console
   lsug$ systemctl --user enable bloop
   lsug$ systemctl --user start bloop
   ```
   
4. Checkout the kata repository

```console
lsug$ git clone https://github.com/lsug/kata-optics-with-monocle.git
```

5. Generate the bloop configuration
  
```console
lsug$ cd kata-optics-with-monocle/
kata-optics-with-monocle]$ sbt bloopInstall
[success] Generated .bloop/root-test.json
[success] Generated .bloop/root.json
```

6. Compile the project with bloop

```console
kata-optics-with-monocle$ bloop compile root
Compiling root (1 Scala source)
Compiled root (1929ms)

```

### MacOS Setup

The easiest way to install packages on Mac is via homebrew

#### Homebrew

https://brew.sh/

Run:

```console
/usr/bin/ruby -e "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/master/install)"
```

#### XCode

Run:
```console
$ xcode-select --install
```

#### Java8

```console
$ brew tap caskroom/versions brew cask install java8
```
Alternatively, you can use the [AdopOpenJDK brew version](https://github.com/AdoptOpenJDK/homebrew-openjdk)

#### SBT 1:

```console
$ brew install sbt@1
```

Other SBT options can be found on the SBT [Setup guide](https://www.scala-sbt.org/release/docs/Setup.html)

#### git

```console
$ brew install git
```

#### Bloop repository

See the bloop repository instructions for ArchLinux.

# Joining remotely

Join the [Virtual Classroom](https://eu.bbcollab.com/guest/008a7054fd7945928d0762d230236842) to access screenshare and chat features.  It will open at 6PM, 30 minutes before the session.  Please follow the [instructions for joining](https://help.blackboard.com/Collaborate/Ultra/Participant/Join_Sessions#from-a-link_OTP-0).
