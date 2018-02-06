package com.traiana.nagger.actor

import akka.actor.{ActorLogging, ActorRef}
import akka.persistence.{PersistentActor, RecoveryCompleted}
import akka.util.Timeout

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
}

case class AddChannel(name: String)
case class RemoveChannel(name: String)

class ChannelManagerActor extends PersistentActor with ActorLogging {

  var channels: Map[String, ActorRef] = Map[String, ActorRef]()

  implicit val ec: ExecutionContextExecutor = context.dispatcher

  implicit val timeout: Timeout = Timeout(5 seconds)

  override def receiveCommand: Receive = {

    case ChannelManagerActor.AddToChannel(nickname, channelName) =>
      channels.get(channelName) match {
        case Some(channel) =>
          channel forward ChannelActor.JoinChannel(nickname)

        case None =>
          persist(AddChannel(channelName)) { addChannel =>
            val channel = context.actorOf(ChannelActor.props(addChannel.name))
            channels = channels ++ Map(channelName -> channel)
            channel forward ChannelActor.JoinChannel(nickname)
          }
      }

    case ChannelManagerActor.LeaveChannel(nickname, channelName) =>
      channels(channelName) forward ChannelActor.LeaveChannel(nickname)

    case ChannelManagerActor.SendChannelMessage(nickname, channelName, message) =>
      channels(channelName) forward ChannelActor.SendChannelMessage(nickname, message)
  }

  override def receiveRecover: Receive = {
    case AddChannel(name) =>
      val channel = context.actorOf(ChannelActor.props(name))
      channels = channels ++ Map(name -> channel)

    case RecoveryCompleted =>
      log.info("finished channel manager actor")
  }

  override def persistenceId: String = "channel-manager-id"
}
