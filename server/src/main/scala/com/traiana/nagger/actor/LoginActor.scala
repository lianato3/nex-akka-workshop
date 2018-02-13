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
import com.traiana.nagger.actor.LoginActor._

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._
import scala.util.Random

/**
  * Created by IntelliJ IDEA.
  * User: Anatoly Libman
  * Date: 29/01/2018
  * Time: 12:44
  */
object LoginActor {

  case class RegisterNewUser(userName: String, password: String, nickname: String)

  case class LoginRequest(userName: String, password: String)

  case class ValidateToken(token: String)

  trait LoginResponse

  case class LoginRequestSuccessResp(token: String) extends LoginResponse

  case class LoginRequestFailure(origSender: ActorRef) extends LoginResponse

  case class ValidateSuccessResp(nickname: String) extends LoginResponse

  case object LoginFailedResp extends LoginResponse

}

class LoginActor extends Actor {

  case class UserDetails(token: String, nickname: String, origSender: ActorRef)

  context.actorOf(ClusterSingletonManager.props(singletonProps = Props(classOf[UserDetailsActor]),
                                                terminationMessage = UserDetailsActor.End,
                                                settings = ClusterSingletonManagerSettings(context.system)),
                  name = "userDetails")

  val userDetailsProxy: ActorRef = context.actorOf(
    ClusterSingletonProxy.props(singletonManagerPath = s"/user/ApiActor/LoginActor/userDetails",
                                settings = ClusterSingletonProxySettings(context.system)),
    name = "userDetailsProxy")

  var tokenToNickName: Map[String, String] = Map[String, String]()

  implicit val ec: ExecutionContextExecutor = context.dispatcher

  implicit val timeout: Timeout = Timeout(5 seconds)

  override def receive: Receive = {
    case RegisterNewUser(userName, password, nickname) =>
      val origSender = sender()
      userDetailsProxy ? UserDetailsActor.AddNewUser(userName, password, nickname) map {
        case UserDetailsActor.UserAddedResponse =>
          UserDetails(Random.alphanumeric take 10 mkString, nickname, origSender)

        case UserDetailsActor.UserAlreadyExistsResponse =>
          LoginRequestFailure(origSender)

      } pipeTo self

    case LoginRequest(userName, password) =>
      val origSender = sender()
      userDetailsProxy ? UserDetailsActor.IsValidUser(userName, password) map {
        case UserDetailsActor.ValidUserResponse(nickname) =>
          UserDetails(Random.alphanumeric take 10 mkString, nickname, origSender)

        case UserDetailsActor.InvalidUserResponse =>
          LoginRequestFailure(origSender)
      } pipeTo self

    case UserDetails(token, nickname, origSender) =>
      tokenToNickName = tokenToNickName ++ Map(token -> nickname)
      origSender ! LoginRequestSuccessResp(token)

    case LoginRequestFailure(origSender) =>
      origSender ! LoginFailedResp

    case ValidateToken(token) =>
      tokenToNickName.get(token) match {
        case Some(nickname) => sender() ! ValidateSuccessResp(nickname)
        case None           => sender() ! LoginFailedResp
      }
  }

}
