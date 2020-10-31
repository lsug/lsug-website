#!/usr/bin/env amm

import $file.aws
import aws.{
  App,
  Stack,
  Image,
  Asset,
  Bucket,
  Vpc,
  Eip,
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
        config <- Bucket("lsug-website-config", "Config")
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
                |package_upgrade: true
                |packages:
                |- java-11-amazon-corretto-headless
                |cloud_final_modules:
                |- [scripts-user, always]
                |write_files:
                |- content: |
                |    [Service]
                |    Type=exec
                |    ExecStart=/bin/bash /usr/local/bin/lsug /var/www/html /usr/local/lsug/.config 80
                |  path: /etc/systemd/system/lsug.service
                |--//
                |Content-Type: text/x-shellscript; charset="us-ascii"
                |MIME-Version: 1.0
                |Content-Transfer-Encoding: 7bit
                |Content-Disposition: attachment; filename="userdata.txt"
                |
                |#!/bin/bash
                |code_dir=$(mktemp -d)
                |""".stripMargin +
              s"""aws s3 cp 's3://${asset.getS3BucketName}/${asset.getS3ObjectKey}' """ + """ "$code_dir/assets.zip" """ + """
                |aws s3 cp 's3://lsug-website-config/fullchain.pkcs12' """.stripMargin + """ "$code_dir/fullchain.pkcs12" """ + """
                |cd $code_dir
                |unzip assets.zip
                |if [ -d /var/www/html ]; then
                |  rm -rf /var/www/html
                |fi
                |mkdir --p /var/www
                |mv static /var/www/html
                |if [ -d /usr/local/lsug/.config ]; then
                |  rm -rf /usr/local/lsug/.config
                |fi
                |mkdir --p /usr/local/lsug
                |mv resources /usr/local/lsug/.config
                |mv fullchain.pkcs12 /usr/local/lsug/fullchain.pkcs12
                |mv app.jar /usr/local/bin/lsug
                |chmod +x /usr/local/bin/lsug
                |rm -rf $code_dir
                |systemctl enable lsug.service
                |systemctl start lsug.service""".stripMargin
            )
          )
        )
        instance <- Instance(
          "WebServer",
          vpc,
          image,
          sg,
          Instance.public,
          keyName = Some("lsug")
        )
        _ <- Eip("WebsiteIp", instance.getInstanceId)
      } yield {
        // Really ugly...
        asset.getBucket.grantRead(instance)
        config.grantRead(instance)
        instance
      }
    )
  ).synth()
}
