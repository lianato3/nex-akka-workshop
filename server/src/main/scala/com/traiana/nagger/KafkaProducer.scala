package com.traiana.nagger

import akka.NotUsed
import akka.actor.ActorSystem
import akka.kafka.ProducerSettings
import akka.kafka.scaladsl.Producer
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.query.{EventEnvelope, PersistenceQuery}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import com.traiana.nagger.actor.UserDetailsActor
import com.typesafe.config.ConfigFactory
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.{ByteArraySerializer, StringSerializer}

/**
  * Created by IntelliJ IDEA.
  * User: Anatoly Libman
  * Date: 12/03/2018
  * Time: 12:12
  */

object KafkaProducer extends App {

  implicit val system = ActorSystem("kafka-producer", ConfigFactory.load("dev.conf"))

  implicit val actorMaterializer = ActorMaterializer()

  val producerSettings = ProducerSettings(system, new ByteArraySerializer, new StringSerializer)
    .withBootstrapServers("localhost:9092")

  val readJournal =
    PersistenceQuery(system).readJournalFor[CassandraReadJournal](CassandraReadJournal.Identifier)

  val envSrc: Source[EventEnvelope, NotUsed] = readJournal.eventsByPersistenceId("user-details-id", 0, Long.MaxValue)

  envSrc.map{eventEnvelop =>
    new ProducerRecord[Array[Byte], String]("test", eventEnvelop.event.asInstanceOf[UserDetailsActor.User].nickName.toString)
  }.runWith(Producer.plainSink(producerSettings))


}
