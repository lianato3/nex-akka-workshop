package com.traiana.nagger.actor

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.cluster.sharding.ClusterSharding
import akka.pattern.{ask, pipe}
import akka.util.Timeout

import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._

/**
  * Created by IntelliJ IDEA.
  * User: Anatoly Libman
  * Date: 29/01/2018
  * Time: 16:26
  */
case object ChannelManagerActor {

  case class AddToChannel(nickname: String, channelName: String)

  case class LeaveChannel(nickname: String, channelName: String)

  case class SendChannelMessage(nickname: String, channelName: String, message: String)

  case class AddApiListener(apiActor: ActorRef)

  trait ChannelManagerResponse
  case object Ack extends ChannelManagerResponse

  case object End
}

final case class EntityEnvelope(id: String, payload: Any)
class ChannelManagerActor extends Actor with ActorLogging {

  implicit val ec: ExecutionContextExecutor = context.dispatcher

  implicit val timeout: Timeout = Timeout(5 seconds)

  var apiListeners: ListBuffer[ActorRef] = ListBuffer()

  val channelRegion: ActorRef = ClusterSharding(context.system).shardRegion("ChannelActor")

  override def receive: Receive = {
    case ChannelManagerActor.AddToChannel(nickname, channelName) =>
      val cm = channelRegion ? EntityEnvelope(channelName, ChannelActor.JoinChannel(nickname, channelName))

      cm map (_ => ChannelManagerActor.Ack) pipeTo sender()

      cm map (m => apiListeners.foreach(_ ! m))

    case ChannelManagerActor.LeaveChannel(nickname, channelName) =>
      (channelRegion ? EntityEnvelope(channelName, ChannelActor.LeaveChannel(nickname, channelName)))
        .map(_ => ChannelManagerActor.Ack)
        .pipeTo(sender())

    case ChannelManagerActor.SendChannelMessage(nickname, channelName, message) =>
      val mr = channelRegion ? EntityEnvelope(channelName,
                                              ChannelActor.SendChannelMessage(nickname, channelName, message))

      mr map (_ => ChannelManagerActor.Ack) pipeTo sender()

      mr map (m => apiListeners.foreach(_ ! m))

    case ChannelManagerActor.End =>
      log.info("user details actor is terminating")

    case ChannelManagerActor.AddApiListener(apiActor) =>
      apiListeners.append(apiActor)
  }

}
