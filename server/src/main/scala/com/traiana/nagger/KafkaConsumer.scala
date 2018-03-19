package com.traiana.nagger

import akka.Done
import akka.actor.ActorSystem
import akka.kafka.scaladsl.{Consumer, Producer}
import akka.kafka.{ConsumerSettings, ProducerSettings, Subscriptions}
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, RunnableGraph, Sink, Source}
import akka.stream.{ActorMaterializer, ClosedShape}
import com.typesafe.config.ConfigFactory
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.{ByteArrayDeserializer, ByteArraySerializer, StringDeserializer, StringSerializer}

import scala.concurrent.Future

/**
  * Created by IntelliJ IDEA.
  * User: Anatoly Libman
  * Date: 12/03/2018
  * Time: 14:09
  */

object KafkaConsumer extends App {

  implicit val system: ActorSystem = ActorSystem("kafka-consumer", ConfigFactory.load("dev.conf"))

  implicit val actorMaterializer: ActorMaterializer = ActorMaterializer()

  val consumerSettings = ConsumerSettings(system, new ByteArrayDeserializer, new StringDeserializer)
    .withBootstrapServers("localhost:9092")
    .withGroupId("group1")
    .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")


  val subscription = Subscriptions.topics("test")

  val src: Source[String, Consumer.Control] = Consumer.plainSource(consumerSettings, subscription).map(_.value())

  val producerSettings = ProducerSettings(system, new ByteArraySerializer, new StringSerializer)
    .withBootstrapServers("localhost:9092")

  val kafkaSink: Sink[ProducerRecord[Array[Byte], String], Future[Done]] = Producer.plainSink(producerSettings)

  val s1 = Flow[String].filter(_.length < 3).map(new ProducerRecord[Array[Byte], String]("topic1", _)).to(kafkaSink)

  val s2 = Flow[String].filter(s => s.length >= 3 && s.length < 5).map(new ProducerRecord[Array[Byte], String]("topic2", _)).to(kafkaSink)

  val s3 = Flow[String].filter(s => s.length >= 5).map(new ProducerRecord[Array[Byte], String]("topic3", _)).to(kafkaSink)

  val printlnSink: Sink[String, Future[Done]] = Sink.foreach(println(_))

  val g = RunnableGraph.fromGraph(GraphDSL.create(src, s1, s2, s3)((_, _, _, _)) { implicit builder =>
    (src, sink1, sink2, sink3) =>
      import GraphDSL.Implicits._

      val broadcast = builder.add(Broadcast[String](3))

      src ~> broadcast.in
      broadcast.out(0) ~> sink1
      broadcast.out(1) ~> sink2
      broadcast.out(2) ~> sink3
      ClosedShape
  })

  g.run()

}
