package org.dataVectis.detection;



import java.util.Calendar
import java.util.logging.{Level, Logger}

import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.spark.sql.{SparkSession}
import org.apache.spark.streaming.kafka010.KafkaUtils
import org.apache.spark.streaming.kafka010.LocationStrategies.PreferConsistent
import org.apache.spark.streaming.kafka010.ConsumerStrategies.Subscribe
import org.apache.spark.SparkConf
import org.apache.spark.streaming._
import org.elasticsearch.spark._


object Consumer {

  def main(args: Array[String]): Unit = {

    val logger = Logger.getLogger(getClass.getName)
    val p = new Prop
    val PAPER_CRASH_MAX_REMAINING = p.getProp("PAPER_CRASH_MAX_REMAINING").toInt
    val PAPER_THERSHOLD_ALERT = p.getProp("PAPER_THERSHOLD_ALERT").toDouble
    val currentTime = Calendar.getInstance()
    val currentHour = currentTime.get(Calendar.HOUR_OF_DAY)
    var LOG_DATA_RECEIVED_KAFKA = "test_String"


    val conf = new SparkConf().setMaster("local[*]").setAppName(p.getProp("APP_NAME"))
    /*
    .set("es.index.auto.create", "true")
    .set("es.nodes", "localhost")
    .set("es.port", "9200")
    .set("es.http.timeout", "5m") // elastic search parameters
    .set("es.scroll.size", "50")
  */
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
    def mappingFunction(key: String, value: Option[Long], state: State[Long]):  (String , Long , Long , String)  = {
      value match {
        case Some(v) => {
          state.update(state.getOption().getOrElse(0L) + v)
          val remaining = PAPER_CRASH_MAX_REMAINING - state.get()
          if ( remaining>=1 ) { // if not TIME OUT
            if (currentHour > 8 && currentHour < 20) { // if current time's in [6,18]
              if (remaining < PAPER_THERSHOLD_ALERT) {
                LOG_DATA_RECEIVED_KAFKA = "Alert"
              } else {
                LOG_DATA_RECEIVED_KAFKA = "Not alert"
              }
              (key, v, remaining, LOG_DATA_RECEIVED_KAFKA)
            } else {
              LOG_DATA_RECEIVED_KAFKA = "TIME OUT"
              (key, v, remaining, LOG_DATA_RECEIVED_KAFKA)
            }
          }else{
            LOG_DATA_RECEIVED_KAFKA = s"THE PRINTER $key IS OUT"
            (key, 0 , 0 , LOG_DATA_RECEIVED_KAFKA)}
        }
        case _ => {
          LOG_DATA_RECEIVED_KAFKA = s"DATA NOT RECEIVED YET"
          (key, 0L, state.getOption().getOrElse(0L), LOG_DATA_RECEIVED_KAFKA)}

      }
    }

    val spec = StateSpec.function(mappingFunction _).timeout(Durations.seconds(5))
    val reducedRDD = stream //  Json ===> RDD
      .map(rdd => rdd.value.split(","))
      .map(array => (
        array(0).split(":")(1).trim.replaceAll("\\W", ""),
        array(1).split(":")(1).trim.replaceAll("\\W", "").toLong)
      ).reduceByKey((x, y) => x + y) // reduce by key ( printer ID )  and count the paper's used.

    val cumSumRdd = reducedRDD.mapWithState(spec) // update states changes (mapWithStates)
    cumSumRdd.print
    // cumSumRdd.saveAsTextFiles("output" , Calendar.getInstance.getTimeInMillis.toString  ) //save data to local
    cumSumRdd.foreachRDD( rdd => rdd.saveToEs("crashalert")    )



    streamingContext.start()
    streamingContext.awaitTermination()

  }
}
