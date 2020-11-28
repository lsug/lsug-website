import $ivy.`software.amazon.awssdk:ec2:2.15.19`

import software.amazon.awssdk.services.ec2.Ec2Client
import software.amazon.awssdk.services.ec2.model.RebootInstancesRequest

def instanceId = {

  import scala.jdk.CollectionConverters._

  val client = Ec2Client.create()
  val instances = client.describeInstances()
  instances.reservations.asScala.headOption
    .flatMap(_.instances.asScala.headOption)
    .map(_.instanceId)
    .get
}

def reboot(instanceId: String) = {
  val client = Ec2Client.create()
  client
    .rebootInstances(
      RebootInstancesRequest
        .builder()
        .instanceIds(instanceId)
        .build()
    )
    .toString
}
