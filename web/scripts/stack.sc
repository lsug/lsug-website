#!/usr/bin/env amm

import $file.aws
import aws.{
  App,
  Stack,
  Image,
  Asset,
  Bucket,
  Vpc,
  Instance,
  Peer,
  Port,
  Connections,
  SecurityGroup
}

import ammonite.ops._

@main
def main(
    stack: String,
    account: String,
    region: String,
    assetPath: Path
) = {
  App(
    pwd,
    Stack(
      stack,
      account,
      region,
      for {
        vpc <- Vpc("PublicVpc", "10.0.0.0/16", 2)
        sg <- SecurityGroup("WebServiceSecurityGroup", vpc)
        connection <- Connections(
          Port.http,
          List(sg),
          List(
            Peer.ipv4 -> Port.http,
            Peer.ipv4 -> Port.https
          )
        )
        bucket <- Asset(
          "CodeAssets",
          assetPath
        ).map(_.getBucket)
        image <- Image(
          Some(
            Image.Data(
              Image.Data.Command
                .yumInstall("java-11-amazon-corretto-headless"),
              Image.Data.Command.s3(
                _ => bucket,
                "**",
                "/tmp"
              ),
              Image.Data.Command.mkdir(
                "/var/www/html"
              ),
              Image.Data.Command.mv(
                "/tmp/static",
                "/var/www/html"
              ),
              Image.Data.Command.mkdir(
                "/usr/local/lsug/.config"
              ),
              Image.Data.Command.mv(
                "/tmp/resources",
                "/usr/local/lsug/.config"
              ),
              Image.Data.Command.mv(
                "/tmp/app.jar",
                "/usr/local/lsug/app.jar"
              )
            )
          )
        )
        instance <- Instance(
          "WebServer",
          vpc,
          image,
          sg,
          Instance.public
        )
      } yield instance
    )
  ).synth()
}
