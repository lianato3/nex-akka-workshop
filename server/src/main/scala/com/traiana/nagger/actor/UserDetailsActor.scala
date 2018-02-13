package com.traiana.nagger.actor

import akka.actor.ActorLogging
import akka.persistence.{PersistentActor, RecoveryCompleted}
import com.traiana.nagger.actor.UserDetailsActor._

import scala.collection.mutable.ListBuffer

/**
  * Created by IntelliJ IDEA.
  * User: Anatoly Libman
  * Date: 29/01/2018
  * Time: 12:51
  */
object UserDetailsActor {
  case class AddNewUser(userName: String, password: String, nickName: String)
  case class IsValidUser(userName: String, password: String)

  trait UserDetailsResponse
  case object UserAddedResponse                  extends UserDetailsResponse
  case object UserAlreadyExistsResponse          extends UserDetailsResponse
  case class ValidUserResponse(nickName: String) extends UserDetailsResponse
  case object InvalidUserResponse                extends UserDetailsResponse

  case class User(name: String, pass: String, nickName: String)

  case object End
}

class UserDetailsActor extends PersistentActor with ActorLogging {

  val users: ListBuffer[User] = ListBuffer[User]()

  override def receiveRecover: Receive = {
    case RecoveryCompleted =>
      log.info("finished user details actor recovery")
    case user: User =>
      users.append(user)
  }

  override def receiveCommand: Receive = {

    case UserDetailsActor.AddNewUser(userName, password, nickName) =>
      users.find(u => u.name == userName) match {
        case Some(_) =>
          sender() ! UserDetailsActor.UserAlreadyExistsResponse
        case None =>
          val newUser = User(userName, password, nickName)
          persist(newUser) { user =>
            users.append(user)
            sender() ! UserDetailsActor.UserAddedResponse
          }
      }

    case IsValidUser(userName, password) =>
      users.find(u => u.name == userName && u.pass == password) match {
        case Some(user) => sender() ! UserDetailsActor.ValidUserResponse(user.nickName)
        case None       => sender() ! UserDetailsActor.InvalidUserResponse
      }

    case UserDetailsActor.End =>
      log.info("user details actor is terminating")

  }

  override def persistenceId: String = "user-details-id"
}
