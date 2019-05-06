package org.dataVectis.producer

import java.util
import java.util.Properties

import com.google.gson.Gson
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}
import org.dataVectis.detection.Prop
import org.dataVectis.map.Event

object Producer {

  def main(args: Array[String]): Unit = {


    val props = new Properties()
    props.put("bootstrap.servers", "localhost:9092")
    props.put("group.id", "group1")
    props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer")
    props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer")

    val producer = new KafkaProducer[String, String](props)

    val gSon = new Gson
   val list =  new  util.ArrayList [Long]


    val dataRead = new sendCsvFile

    val p = new Prop


    val topic = "event"

    for (us <- dataRead.readData( p.getProp("HDFS_PATH") ) )  {

      val eventOnly = new Event(us.appName, us.sleepTime , us.time)

      val sleepTime = us.sleepTime
      val eventRecord = new ProducerRecord( topic , "", gSon.toJson(eventOnly))

      println(eventOnly)
      producer.send(eventRecord)
      Thread.sleep(500)
    }







  }



}
