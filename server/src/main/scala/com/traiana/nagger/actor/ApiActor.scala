package com.traiana.nagger.actor

import akka.actor.{Actor, ActorRef, Props}
import akka.cluster.singleton.{
  ClusterSingletonManager,
  ClusterSingletonManagerSettings,
  ClusterSingletonProxy,
  ClusterSingletonProxySettings
}
import akka.pattern.{ask, pipe}
import akka.util.Timeout
import com.traiana.nagger.actor.LoginActor.{LoginFailedResp, LoginRequestSuccessResp, LoginResponse}
import com.traiana.nagger.spb.LoginRegisterResponse.Response.Empty
import com.traiana.nagger.spb._
import io.grpc.stub.StreamObserver

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._

/**
  * Created by IntelliJ IDEA.
  * User: Anatoly Libman
  * Date: 29/01/2018
  * Time: 12:29
  */
class ApiActor extends Actor {

  implicit val ec: ExecutionContextExecutor = context.dispatcher

  implicit val timeout: Timeout = Timeout(5 seconds)

  val loginActor: ActorRef = context.actorOf(Props[LoginActor], "LoginActor")

  context.actorOf(ClusterSingletonManager.props(singletonProps = Props(classOf[ChannelManagerActor]),
                                                terminationMessage = UserDetailsActor.End,
                                                settings = ClusterSingletonManagerSettings(context.system)),
                  name = "channelManager")

  val channelManagerProxy: ActorRef = context.actorOf(
    ClusterSingletonProxy.props(singletonManagerPath = s"/user/ApiActor/channelManager",
                                settings = ClusterSingletonProxySettings(context.system)),
    name = "channelManagerProxy")

  override def preStart(): Unit = {
    super.preStart()
    channelManagerProxy ! ChannelManagerActor.AddApiListener(this.self)
  }

  var nicknamesToStreams: Map[String, StreamObserver[ListenEvent]] = Map()

  case class TokenDetails(nickname: String, streamObserver: StreamObserver[ListenEvent])

  override def receive: Receive = {

    case RegisterRequest(username, password, nickname) =>
      (loginActor ? LoginActor.RegisterNewUser(username, password, nickname)).mapTo[LoginResponse].map {
        case LoginRequestSuccessResp(token) => LoginRegisterResponse().withSuccess(LoginSuccess(token))
        case LoginFailedResp                => LoginRegisterResponse().withFailure(LoginFailure())
      } pipeTo sender()

    case LoginRequest(username, password) =>
      (loginActor ? LoginActor.LoginRequest(username, password)).mapTo[LoginResponse].map {
        case LoginRequestSuccessResp(token) => LoginRegisterResponse().withSuccess(LoginSuccess(token))
        case LoginFailedResp                => LoginRegisterResponse().withFailure(LoginFailure())
      } pipeTo sender()

    case JoinLeaveRequest(token, channel, true) =>
      (loginActor ? LoginActor.ValidateToken(token)).mapTo[LoginActor.ValidateSuccessResp] flatMap { vsr =>
        val nickname = vsr.nickname
        (channelManagerProxy ? ChannelManagerActor.AddToChannel(nickname, channel))
          .map(_ => Empty)
      } pipeTo sender()

    case JoinLeaveRequest(token, channel, false) =>
      (loginActor ? LoginActor.ValidateToken(token)).mapTo[LoginActor.ValidateSuccessResp] flatMap { vsr =>
        (channelManagerProxy ? ChannelManagerActor.LeaveChannel(vsr.nickname, channel))
          .map(_ => Empty)
      } pipeTo sender()

    case MessageRequest(token, channel, message) =>
      (loginActor ? LoginActor.ValidateToken(token)).mapTo[LoginActor.ValidateSuccessResp] flatMap { vsr =>
        val nickname = vsr.nickname
        (channelManagerProxy ? ChannelManagerActor.SendChannelMessage(nickname, channel, message))
          .map(_ => Empty)
      } pipeTo sender()

    case (ListenRequest(token), responseObserver) =>
      (loginActor ? LoginActor.ValidateToken(token)).mapTo[LoginActor.ValidateSuccessResp] map { vsr =>
        TokenDetails(vsr.nickname, responseObserver.asInstanceOf[StreamObserver[ListenEvent]])
      } pipeTo self

    case TokenDetails(nickname, streamObserver) =>
      nicknamesToStreams = nicknamesToStreams + (nickname -> streamObserver)

    case ChannelActor.ChannelMessages(nickname, channel, messages) =>
      nicknamesToStreams.get(nickname) match {
        case None =>
        case Some(s) =>
          messages.foreach { m =>
            s.onNext(ListenEvent(channel, m.nickname, m.message))
          }
      }

    case ChannelActor.MessageRecipients(channel, sendMessageList, message, nickname) =>
      sendMessageList.foreach { recipient =>
        nicknamesToStreams.get(recipient) match {
          case None =>
          case Some(s) =>
            s.onNext(ListenEvent(channel, nickname, message))
        }
      }
  }

}
