package com.traiana.nagger.grpc

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.pattern.ask
import com.google.protobuf.empty.Empty
import com.traiana.kit.boot.grpc.GrpcService
import com.traiana.nagger.actor.ApiActor
import com.traiana.nagger.spb.NaggerGrpc.Nagger
import com.traiana.nagger.spb._
import io.grpc.stub.StreamObserver
import io.grpc.{BindableService, ServerServiceDefinition}
import org.springframework.stereotype.Service

import scala.concurrent.Future
import scala.concurrent.duration._
import akka.util.Timeout

/**
  * Created by IntelliJ IDEA.
  * User: Anatoly Libman
  * Date: 29/01/2018
  * Time: 11:50
  */

@Service
@GrpcService
class NaggerGrpc extends Nagger with BindableService {

  val actorSystem = ActorSystem("naggerService")

  val apiActor: ActorRef = actorSystem.actorOf(Props[ApiActor], "ApiActor")

  implicit val timeout: Timeout = Timeout(5 seconds)

  override def register(request: RegisterRequest): Future[LoginRegisterResponse] =
    (apiActor ? request).mapTo[LoginRegisterResponse]

  override def login(request: LoginRequest): Future[LoginRegisterResponse] =
    (apiActor ? request).mapTo[LoginRegisterResponse]

  override def joinLeave(request: JoinLeaveRequest): Future[Empty] = (apiActor ? request).mapTo[Empty]

  override def sendMessage(request: MessageRequest): Future[Empty] = (apiActor ? request).mapTo[Empty]

  override def listen(request: ListenRequest, responseObserver: StreamObserver[ListenEvent]): Unit = {
    apiActor ! (request, responseObserver)
    Unit
  }

  override def bindService(): ServerServiceDefinition = {
    NaggerGrpc.bindService(this, actorSystem.dispatcher)
  }

}
