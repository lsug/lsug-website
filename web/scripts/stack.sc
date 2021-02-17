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
    assetPath: Path,
    sslPassword: String
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
              s"""Content-Type: multipart/mixed; boundary="//"
                |MIME-Version: 1.0
                |
                |--//
                |Content-Type: text/cloud-config; charset="us-ascii"
                |MIME-Version: 1.0
                |Content-Transfer-Encoding: 7bit
                |Content-Disposition: attachment; filename="cloud-config.txt"
                |
                |#cloud-config
                |yum_repos:
                |  epel:
                |    baseurl: http://download.fedoraproject.org/pub/epel/7/SRPMS
                |    enabled: true
                |package_upgrade: true
                |packages:
                |- java-11-amazon-corretto-headless
                |- certbot
                |cloud_final_modules:
                |- [scripts-user, always]
                |write_files:
                |- content: |
                |    [Service]
                |    Type=exec
                |    ExecStart=/bin/bash /usr/local/bin/lsug /var/www/html /usr/local/lsug/.config 443 80 /usr/local/lsug/fullchain.pkcs12 ${sslPassword}
                |  path: /etc/systemd/system/lsug.service
                |- content: |
                |    PRE_HOOK=--pre-hook "systemctl stop lsug.service"
                |    POST_HOOK=--post-hook "systemctl start lsug.service"
                |    DEPLOY_HOOK=--deploy-hook "/usr/local/bin/lsug-cert"
                |  path: /etc/sysconfig/certbot
                |- content: |
                |    #!/bin/bash -xe
                |    openssl pkcs12 -export -out /usr/local/lsug/fullchain.pkcs12 -in /etc/letsencrypt/live/www.lsug.co.uk/fullchain.pem -inkey /etc/letsencrypt/live/www.lsug.co.uk/privkey.pem -passin pass:${sslPassword} -passout pass:${sslPassword}
                |    aws s3 cp /usr/local/lsug/fullchain.pkcs12 s3://lsug-website-config/fullchain.pkcs12
                |    aws s3 cp /etc/letsencrypt/renewal/www.lsug.co.uk.conf s3://lsug-website-config/cert-renewal.conf
                |    aws s3 cp /etc/letsencrypt/live/www.lsug.co.uk/chain.pem s3://lsug-website-config/chain.pem
                |    aws s3 cp /etc/letsencrypt/live/www.lsug.co.uk/cert.pem s3://lsug-website-config/cert.pem
                |    aws s3 cp /etc/letsencrypt/live/www.lsug.co.uk/privkey.pem s3://lsug-website-config/privkey.pem
                |    aws s3 cp /etc/letsencrypt/live/www.lsug.co.uk/fullchain.pem s3://lsug-website-config/fullchain.pem
                |    systemctl restart lsug.service
                |  path: /usr/local/bin/lsug-cert
                |  permissions: '0775'
                |--//
                |Content-Type: text/x-shellscript; charset="us-ascii"
                |MIME-Version: 1.0
                |Content-Transfer-Encoding: 7bit
                |Content-Disposition: attachment; filename="userdata.txt"
                |
                |#!/bin/bash -xe
              | exec > >(tee /var/log/user-data.log) 2>&1
                |code_dir=$$(mktemp -d)
                |""".stripMargin +
              cp(s"s3://${asset.getS3BucketName}/${asset.getS3ObjectKey}", "$code_dir/assets.zip") +
              cp("s3://lsug-website-config/fullchain.pkcs12", "$code_dir/fullchain.pkcs12") +
              cp("s3://lsug-website-config/cert-renewal.conf", "/etc/letsencrypt/renewal/www.lsug.co.uk.conf") +
              cp("s3://lsug-website-config/chain.pem", "/etc/letsencrypt/archive/www.lsug.co.uk/chain1.pem") ++
              cp("s3://lsug-website-config/cert.pem", "/etc/letsencrypt/archive/www.lsug.co.uk/cert1.pem") ++
              cp("s3://lsug-website-config/privkey.pem", "/etc/letsencrypt/archive/www.lsug.co.uk/privkey1.pem") ++
              cp("s3://lsug-website-config/fullchain.pem", "/etc/letsencrypt/archive/www.lsug.co.uk/fullchain1.pem") ++ """
                |mkdir -p /etc/letsencrypt/live/www.lsug.co.uk
                |ln -sf /etc/letsencrypt/archive/www.lsug.co.uk/chain1.pem /etc/letsencrypt/live/www.lsug.co.uk/chain.pem
                |ln -sf /etc/letsencrypt/archive/www.lsug.co.uk/cert1.pem /etc/letsencrypt/live/www.lsug.co.uk/cert.pem
                |ln -sf /etc/letsencrypt/archive/www.lsug.co.uk/privkey1.pem /etc/letsencrypt/live/www.lsug.co.uk/privkey.pem
                |ln -sf /etc/letsencrypt/archive/www.lsug.co.uk/fullchain1.pem /etc/letsencrypt/live/www.lsug.co.uk/fullchain.pem
                |cd $code_dir
                |ls -al
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
                |systemctl start lsug.service
                |systemctl enable certbot-renew.timer
                |""".stripMargin
            )
          )
        )
        instance <- Instance(
          "LSUGWebServer",
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
        asset.getBucket.grantRead(instance)
        config.grantRead(instance)
        config.grantWrite(instance)
        instance
      }
    )
  ).synth()

  def cp(from: String, to: String): String = s"aws s3 cp ${from} ${to}\n"
}
