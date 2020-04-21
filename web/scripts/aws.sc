import $ivy.`software.amazon.awscdk:core:1.33.1`
import $ivy.`software.amazon.awscdk:ec2:1.33.1`
import $ivy.`software.amazon.awscdk:s3-assets:1.33.1`

import software.amazon.awscdk.services.ec2
import software.amazon.awscdk.services.s3
import software.amazon.awscdk.core
import scala.jdk.CollectionConverters._

trait Resource[+A] {
  def apply(parent: core.Construct): A
  def map[B](f: A => B): Resource[B] =
    parent => f(apply(parent))
  def flatMap[B](f: A => Resource[B]): Resource[B] =
    parent => f(apply(parent))(parent)
}

object Vpc {
  type AWS = ec2.IVpc
  def apply(
      id: String,
      cidr: String,
      maxAzs: Int
  ): Resource[ec2.IVpc] =
    scope => {
      ec2.Vpc.Builder.create(scope, id).cidr(cidr).maxAzs(maxAzs).build
    }
}

object SecurityGroup {

  type AWS = ec2.ISecurityGroup

  def apply(id: String, vpc: Vpc.AWS): Resource[AWS] =
    scope => {
      new ec2.SecurityGroup(
        scope,
        id,
        ec2.SecurityGroupProps.builder.vpc(vpc).allowAllOutbound(true).build
      )
    }
}

object Peer {
  val ipv4 = ec2.Peer.anyIpv4
}

object Port {
  val http = ec2.Port.tcp(80)
  val ssh = ec2.Port.tcp(22)
  val https = ec2.Port.tcp(443)
}

object Connections {
  def apply(
      defaultPort: ec2.Port,
      securityGroups: List[SecurityGroup.AWS],
      ingress: List[(ec2.IConnectable, ec2.Port)]
  ): Resource[ec2.Connections] =
    _ => {
      val connection = ec2.Connections.Builder.create
        .defaultPort(defaultPort)
        .securityGroups(securityGroups.asJava)
        .build
      ingress.foreach {
        case (conn, p) => connection.allowFrom(conn, p)
      }
      connection
    }
}

object Asset {

  type AWS = s3.assets.Asset

  def apply(id: String, path: os.Path): Resource[AWS] =
    scope =>
      new s3.assets.Asset(
        scope,
        id,
        s3.assets.AssetProps.builder.path(path.toString).build()
      )
}

object App {

  type AWS = core.App

  def apply(path: os.Path, children: Resource[_]*): AWS = {
    val app =
      new core.App(
        core.AppProps.builder
        // .outdir(path.toString)
          .build()
      )
    children.foreach(_(app))
    app
  }
}

object Bucket {

  type AWS = s3.IBucket

  def apply(path: String, name: String): Resource[s3.IBucket] =
    scope => {
      s3.Bucket.fromBucketAttributes(
        scope,
        name,
        s3.BucketAttributes.builder.bucketArn(s"arn:aws:s3:::$path").build
      )
    }
}

object Instance {
  type AWS = ec2.Instance

  val public =
    ec2.SubnetSelection.builder.subnetType(ec2.SubnetType.PUBLIC).build

  def apply(
      id: String,
      vpc: Vpc.AWS,
      ami: Image.AWS,
      securityGroup: SecurityGroup.AWS,
      subnetSelection: ec2.SubnetSelection,
      instanceType: ec2.InstanceType = ec2.InstanceType
        .of(ec2.InstanceClass.BURSTABLE3, ec2.InstanceSize.NANO),
      keyName: Option[String] = None
  ): Resource[ec2.Instance] = { scope =>
    {
      val props =
        ec2.InstanceProps.builder
          .vpc(vpc)
          .securityGroup(securityGroup)
          .vpcSubnets(subnetSelection)
          .instanceType(instanceType)
          .machineImage(ami)
          .allowAllOutbound(true)
      new ec2.Instance(
        scope,
        id,
        keyName.map(props.keyName(_)).getOrElse(props).build
      )
    }
  }
}

object Image {

  object Data {

    type AWS = ec2.UserData

    sealed trait Command

    object Command {

      case class Exec(s: String) extends Command
      case class S3(options: ec2.S3DownloadOptions) extends Command

      def s3(
          bucket: Resource[Bucket.AWS],
          key: String,
          path: String
      ): Resource[S3] =
        scope => {
          S3(
            ec2.S3DownloadOptions.builder
              .bucket(bucket(scope))
              .localFile(path)
              .bucketKey(key)
              .build
          )
        }

      def mv(from: String, to: String): Resource[Exec] =
        _ => Exec(s"mv $from $to")

      def mkdir(dir: String): Resource[Exec] =
        _ => Exec(s"mkdir --parents $dir")

      def yumInstall(packages: String*): Resource[Exec] =
        _ => Exec(s"""yum install -y ${packages.mkString(" ")}""")
    }

    def apply(cmds: Resource[Command]*): Resource[ec2.UserData] = { scope =>
      {
        val userData = ec2.UserData.forLinux
        cmds.map(_(scope)).foreach {
          case Command.S3(options) =>
            userData.addS3DownloadCommand(options)
          case Command.Exec(command) =>
            userData.addCommands(command)
        }
        userData
      }
    }
  }

  type AWS = ec2.IMachineImage

  def apply(
      data: Option[Resource[Data.AWS]] = None,
      edition: ec2.AmazonLinuxEdition = ec2.AmazonLinuxEdition.STANDARD,
      generation: ec2.AmazonLinuxGeneration =
        ec2.AmazonLinuxGeneration.AMAZON_LINUX_2,
      storage: ec2.AmazonLinuxStorage = ec2.AmazonLinuxStorage.GENERAL_PURPOSE
  ): Resource[ec2.IMachineImage] =
    scope => {
      val builder = ec2.AmazonLinuxImage.Builder
        .create()
        .edition(edition)
        .generation(generation)
        .storage(storage)

      data
        .map(r => builder.userData(r(scope)))
        .getOrElse(builder)
        .build()
    }
}

object Stack {

  private[this] final class AnonymousStack(
      val parent: core.Construct,
      val name: String,
      val stackProps: core.StackProps,
      f: (core.Construct) => Unit
  ) extends core.Stack(parent, name, stackProps) {
    f(this)
  }

  def apply(
      name: String,
      account: String,
      region: String,
      children: Resource[_]*
  ): Resource[core.Stack] =
    scope =>
      new AnonymousStack(
        scope,
        name,
        core.StackProps
          .builder()
          .env(
            core.Environment
              .builder()
              .account(account)
              .region(region)
              .build()
          )
          .build(),
        scope => {
          children.foreach(_(scope))
        }
      )
}
