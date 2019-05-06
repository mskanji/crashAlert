package org.dataVectis.detection
import org.apache.log4j.{Level, LogManager}
import org.apache.spark.{HashPartitioner, SparkConf}
import org.apache.spark.rdd.RDD
import org.apache.spark.streaming._
object test {

  def main(args: Array[String]): Unit = {

    var sparkConfig = new SparkConf()
      .setMaster("local[*]")
      .setAppName("TextFileStream")

    //Process the stream data every 10 seconds
    val ssc = new StreamingContext(sparkConfig, Seconds(10))

    //A check point directory is needed by Spark for stateful stream process
    ssc.checkpoint("/tmp/spark_checkpoint")

    //Data stream from files: one word per line
    //Make sure you create the fold before running the code
    val lines = ssc.textFileStream("/tmp/data_stream")

    //Supress the info log
    LogManager.getRootLogger.setLevel(Level.WARN)

    //Comment the lines to switch mehtods between updateStateByKey and mapWithState
    //Use of updateStateByKey(...)
    val r = lines.map( line => (line, 1L) ).reduceByKey(_ + _).updateStateByKey(updateFunction)
    //User iterator update function
    //val r = lines.map(line => (line, 1L)).reduceByKey(_ + _).updateStateByKey(updateFunction2 _, new HashPartitioner(8), false)


    //Use of mapWithState(...)
    //val spec = StateSpec.function(mappingFunction _).timeout(Durations.seconds(2))
    //val r = lines.map( line => (line, 1L) ).reduceByKey(_ + _).mapWithState(spec)

    //Print to console
    r.print()

    //Save to file
    r.foreachRDD { (rdd : RDD[(String, Long)], time : Time) =>
      println(">>>Time: " + time + " Number of partitions: " + rdd.getNumPartitions)
      rdd.saveAsTextFile("/tmp")
    }

    ssc.start()
    ssc.awaitTermination()
  }

  def updateFunction(newData: Seq[Long], state: Option[Long]) = {
    val newState = state.getOrElse(0L) + newData.sum
    Some(newState)
  }

  def updateFunction2(iterator: Iterator[(String, Seq[Long], Option[Long])]) = {
    iterator.toIterable.map {
      case (key, values, state) => (key, state.getOrElse(0L) + values.sum)
    }.toIterator
  }

  // A mapping function that maintains an integer (Long) state and return a tuple (String, Long)
  // the returned tuple is (word, count) which will be a record of the mapped stream
  def mappingFunction(key: String, value: Option[Long], state: State[Long]): (String, Long) = {
    // Use state.exists(), state.get(), state.update() and state.remove() to manage state
    value match {
      case Some(v) => {
        state.update(state.getOption().getOrElse(0L) + v)
        //return the (word, count)
        (key, state.get())
      }
      case _ => (key, 0L)
    }
  }


}
