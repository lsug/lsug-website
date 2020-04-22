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
            Peer.ipv4 -> Port.https,
            Peer.ipv4 -> Port.ssh
          )
        )
        asset <- Asset(
          "CodeAssets",
          assetPath
        )
        image <- Image(
          Some(
            Image.Data.custom(
              """Content-Type: multipart/mixed; boundary="//"
                |MIME-Version: 1.0
                |
                |--//
                |Content-Type: text/cloud-config; charset="us-ascii"
                |MIME-Version: 1.0
                |Content-Transfer-Encoding: 7bit
                |Content-Disposition: attachment; filename="cloud-config.txt"
                |
                |#cloud-config
                |cloud_final_modules:
                |- [scripts-user, always]
                |
                |--//
                |Content-Type: text/x-shellscript; charset="us-ascii"
                |MIME-Version: 1.0
                |Content-Transfer-Encoding: 7bit
                |Content-Disposition: attachment; filename="userdata.txt"
                |
                |#!/bin/bash
                |yum install -y java-11-amazon-corretto-headless
                |code_dir=$(mktemp -d)
                |""".stripMargin +
              s"""aws s3 cp '${asset.getS3Url}' """ + """ "/tmp/$code_dir/assets.zip" """ +
              """
                |cd $code_dir
                |unzip assets.zip
                |if [ -d /var/www/html ]; then
                |  rm -rf /var/www/html
                |fi
                |mkdir --p /var/www/html
                |mv /tmp/static/ /var/www/html/
                |if [ -d /usr/local/lsug/.config ]; then
                |  rm -rf /usr/local/lsug/.config
                |fi
                |mkdir --parents /usr/local/lsug/.config
                |mv /tmp/resources/ /usr/local/lsug/.config/
                |mv /tmp/app.jar /usr/local/lsug/app.jar""".stripMargin
            )
          )
        )
        instance <- Instance(
          "WebServer",
          vpc,
          image,
          sg,
          Instance.public,
          keyName = Some("admin")
        )
      } yield {
        // Really ugly...
        asset.getBucket.grantRead(instance)
        instance
      }
    )
  ).synth()
}
