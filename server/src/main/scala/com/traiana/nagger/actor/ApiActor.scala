package com.traiana.nagger.actor

import akka.actor.{Actor, ActorRef, Props}
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

  val channelManagerActor: ActorRef = context.actorOf(Props[ChannelManagerActor], "ChannelManagerActor")

  var tokensToStreams: Map[String, StreamObserver[ListenEvent]] = Map()

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
        (channelManagerActor ? ChannelManagerActor.AddToChannel(nickname, channel))
          .mapTo[ChannelActor.ChannelMessages] map { cm =>
          cm.messages.foreach { message =>
            tokensToStreams(nickname).onNext(ListenEvent(channel, nickname, message))
          }
          Empty
        }
      } pipeTo sender()

    case JoinLeaveRequest(token, channel, false) =>
      (loginActor ? LoginActor.ValidateToken(token)).mapTo[LoginActor.ValidateSuccessResp] flatMap { vsr =>
        channelManagerActor ? ChannelManagerActor.LeaveChannel(vsr.nickname, channel)
      } pipeTo sender()

    case MessageRequest(token, channel, message) =>
      (loginActor ? LoginActor.ValidateToken(token)).mapTo[LoginActor.ValidateSuccessResp] flatMap { vsr =>
        val nickname = vsr.nickname
        (channelManagerActor ? ChannelManagerActor.SendChannelMessage(nickname, channel, message))
          .mapTo[ChannelActor.MessageRecipients]
          .map { mr =>
            mr.sendMessageList.foreach(tokensToStreams(_).onNext(ListenEvent(channel, nickname, message)))
          }
      } pipeTo sender()

    case (ListenRequest(token), responseObserver) =>
      (loginActor ? LoginActor.ValidateToken(token)).mapTo[LoginActor.ValidateSuccessResp] map { vsr =>
        TokenDetails(vsr.nickname, responseObserver.asInstanceOf[StreamObserver[ListenEvent]])
      } pipeTo self

    case TokenDetails(nickname, streamObserver) =>
      tokensToStreams = tokensToStreams ++ Map(nickname -> streamObserver)

  }

}
