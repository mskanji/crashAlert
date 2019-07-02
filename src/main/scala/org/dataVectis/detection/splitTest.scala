package org.dataVectis.detection

import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession
import org.apache.spark.{SparkConf, SparkContext}
import org.elasticsearch.spark._


object splitTest {

  def main(args: Array[String]): Unit = {

    val conf = new SparkConf().setAppName("splitData").setMaster("local").set("es.index.auto.create", "true")
      .set("es.nodes", "localhost")
      .set("es.port", "9200")
      .set("es.http.timeout", "5m") // elastic search parameters
      .set("es.scroll.size", "50")


    val sc = new SparkContext(conf)
    val spark = SparkSession.builder().config(conf).getOrCreate()
    val p = new Prop
    val rdd0 = sc.textFile(p.getProp("HDFS_LOG_PATH"))
    val reg = """(.*) - - \[(.*)\] "GET (.*) "(.*)" "(.*)"""".r
    def splitMyLine(line:String): Seq[String] = line match {
      case reg(a,b,c,d,e) =>  Seq(a,b,c,d, e)
      case _ =>  Seq("failed to parse Data")


    }
  }

}
