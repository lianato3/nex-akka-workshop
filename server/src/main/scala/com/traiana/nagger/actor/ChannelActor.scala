package com.traiana.nagger.actor

import akka.actor.{ActorLogging, Props}
import akka.persistence.{PersistentActor, RecoveryCompleted}
import com.traiana.nagger.spb.LoginRegisterResponse.Response.Empty

import scala.collection.mutable.ListBuffer

/**
  * Created by IntelliJ IDEA.
  * User: Anatoly Libman
  * Date: 29/01/2018
  * Time: 16:08
  */
object ChannelActor {

  case class JoinChannel(nickname: String)

  case class LeaveChannel(nickname: String)

  case class SendChannelMessage(nickname: String, message: String)

  trait ChannelResponse
  case class MessageRecipients(sendMessageList: List[String]) extends ChannelResponse
  case class ChannelMessages(messages: ListBuffer[String])    extends ChannelResponse

  def props(channelName: String) = Props(new ChannelActor(channelName))

}

case class AddToChannel(nickname: String)
case class InternalRemoveFromChannel(nickname: String)
case class Message(message: String)

class ChannelActor(name: String) extends PersistentActor with ActorLogging {

  val subscribers: ListBuffer[String] = ListBuffer()
  val messages: ListBuffer[String]    = ListBuffer()

  override def receiveCommand: Receive = {
    case ChannelActor.JoinChannel(nickname) =>
      persist(AddToChannel(nickname)) { addToChannel =>
        subscribers.append(addToChannel.nickname)
        sender() ! ChannelActor.ChannelMessages(messages)
      }

    case ChannelActor.LeaveChannel(nickname) =>
      persist(InternalRemoveFromChannel(nickname)) { removeFromChannel =>
        subscribers.remove(subscribers.indexOf(removeFromChannel.nickname))
        sender() ! Empty
      }

    case ChannelActor.SendChannelMessage(nickname, message) =>
      persist(Message(message)) { m =>
        messages.append(m.message)
        sender() ! ChannelActor.MessageRecipients(subscribers.filterNot(_ == nickname).toList)
      }

  }

  override def receiveRecover: Receive = {
    case AddToChannel(nickname) =>
      subscribers.append(nickname)
    case InternalRemoveFromChannel(nickname) =>
      subscribers.remove(subscribers.indexOf(nickname))
    case Message(m) =>
      messages.append(m)
    case RecoveryCompleted =>
      log.info("finished channel actor")
  }

  override def persistenceId: String = s"channel-$name-id"

}
