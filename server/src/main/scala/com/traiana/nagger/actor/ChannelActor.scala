package com.traiana.nagger.actor

import akka.actor.{ActorLogging, Props}
import akka.persistence.{PersistentActor, RecoveryCompleted}

import scala.collection.mutable.ListBuffer

/**
  * Created by IntelliJ IDEA.
  * User: Anatoly Libman
  * Date: 29/01/2018
  * Time: 16:08
  */
object ChannelActor {

  case class JoinChannel(nickname: String, channel: String)

  case class LeaveChannel(nickname: String, channel: String)

  case class SendChannelMessage(nickname: String, channel: String, message: String)

  trait ChannelResponse
  case class MessageRecipients(channel: String, sendMessageList: List[String], message: String, nickname: String)
      extends ChannelResponse
  case class ChannelMessages(nickname: String, channel: String, messages: ListBuffer[Message]) extends ChannelResponse
  case object Ack                                                                              extends ChannelResponse

  def props() = Props(new ChannelActor())

}

case class AddToChannel(nickname: String)
case class RemoveFromChannel(nickname: String)
case class Message(message: String, nickname: String)

class ChannelActor() extends PersistentActor with ActorLogging {

  val subscribers: ListBuffer[String] = ListBuffer()
  val messages: ListBuffer[Message]   = ListBuffer()

  override def receiveCommand: Receive = {
    case ChannelActor.JoinChannel(nickname, channel) =>
      persist(AddToChannel(nickname)) { addToChannel =>
        subscribers.append(addToChannel.nickname)
        sender() ! ChannelActor.ChannelMessages(nickname, channel, messages)
      }

    case ChannelActor.LeaveChannel(nickname, _) =>
      persist(RemoveFromChannel(nickname)) { removeFromChannel =>
        subscribers.remove(subscribers.indexOf(removeFromChannel.nickname))
        sender() ! ChannelActor.Ack
      }

    case ChannelActor.SendChannelMessage(nickname, channel, message) =>
      persist(Message(message, nickname)) { m =>
        messages.append(m)
        sender() ! ChannelActor.MessageRecipients(channel,
                                                  subscribers.filterNot(_ == nickname).toList,
                                                  m.message,
                                                  m.nickname)
      }
  }

  override def receiveRecover: Receive = {
    case AddToChannel(nickname) =>
      subscribers.append(nickname)
    case RemoveFromChannel(nickname) =>
      subscribers.remove(subscribers.indexOf(nickname))
    case m: Message =>
      messages.append(m)
    case RecoveryCompleted =>
      log.info(s"finished channel actor ${context.self.path.name}")
  }

  override def persistenceId: String = s"channel-${context.self.path.name}-id"

}
