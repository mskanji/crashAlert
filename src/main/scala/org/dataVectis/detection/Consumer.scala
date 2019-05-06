package org.dataVectis.detection


import java.util.Calendar
import java.util.logging.{Level, Logger}

import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.spark.sql.{SaveMode, SparkSession}
import org.apache.spark.streaming.kafka010.KafkaUtils
import org.apache.spark.streaming.kafka010.LocationStrategies.PreferConsistent
import org.apache.spark.streaming.kafka010.ConsumerStrategies.Subscribe
import org.apache.spark.SparkConf
import org.apache.spark.streaming._





object Consumer {

  def main(args: Array[String]): Unit = {


    val logger = Logger.getLogger(getClass.getName)
    val p = new Prop
    val conf = new SparkConf().setMaster("local[*]").setAppName(p.getProp("APP_NAME"))
    val spark = SparkSession.builder().config(conf).getOrCreate()
    val sc = spark.sparkContext
    val streamingContext = new StreamingContext(sc, Seconds(5))
    streamingContext.checkpoint("log")

    logger.log(Level.INFO, "Spark context Created", spark.sparkContext.appName)
    logger.log(Level.INFO, "Spark Session Created", spark.sparkContext.appName)
    logger.log(Level.INFO, "Spark Streaming Context Created", spark.sparkContext.appName)


    val kafkaParams = Map[String, Object](
      "bootstrap.servers" -> "localhost:9092",
      "key.deserializer" -> classOf[StringDeserializer],
      "value.deserializer" -> classOf[StringDeserializer],
      "group.id" -> "spark_test_group",
      "auto.offset.reset" -> "latest",
      "enable.auto.commit" -> (false: java.lang.Boolean)
    )
    val topics = Array("event")

    val stream = KafkaUtils.createDirectStream[String, String](
      streamingContext,
      PreferConsistent,
      Subscribe[String, String](topics, kafkaParams)
    )

    def mappingFunction(key: String, value: Option[Long], state: State[Long]): (String, Long, Long, String) = {
      val p = new Prop
      val PAPER_CRASH_MAX_REMAINING = p.getProp("PAPER_CRASH_MAX_REMAINING").toInt
      val PAPER_THERSHOLD_ALERT = p.getProp("PAPER_THERSHOLD_ALERT").toDouble
      value match {
        case Some(v) => {
          state.update(state.getOption().getOrElse(0L) + v)
          var msg = "test_String"
          val remaining = PAPER_CRASH_MAX_REMAINING - state.get()
          if (remaining < PAPER_THERSHOLD_ALERT) {
            msg = "Alert"
          } else {
            msg = "Not alert"
          }
          (key, v , remaining, msg)
        }
        case _ => (key, 0L, 0L, "")
      }
    }

    val spec = StateSpec.function(mappingFunction _).timeout(Durations.seconds(5))

    val reducedRDD = stream
      .map(rdd => rdd.value.split(","))
      .map(array => (
        array(0).split(":")(1).trim.replaceAll("\\W", ""),
        array(1).split(":")(1).trim.replaceAll("\\W", "").toLong))
      .reduceByKey((x, y) => x + y)


    import spark.implicits._


    val cumSumRdd = reducedRDD.mapWithState(spec)
    cumSumRdd.print
    cumSumRdd.saveAsTextFiles("output"+"_"+Calendar.getInstance.getTimeInMillis.toString   )


    /*cumSumRdd.foreachRDD( rdd => rdd.toDF ( "id" , "cumSum" , "remaining" , "state")
      .repartition(1)
      .write
      .mode(SaveMode.Overwrite)
      .format("json")
      .save("out" + Calendar.getInstance().getTimeInMillis.toString)*/









    streamingContext.start()
    streamingContext.awaitTermination()


  }

  def updateFunction(newData: Seq[Long], state: Option[Long]) = {
    val newState = state.getOrElse(0L) + newData.sum
    Some(newState)
  }

}