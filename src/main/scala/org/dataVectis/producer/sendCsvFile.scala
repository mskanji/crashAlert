package org.dataVectis.producer

import org.apache.spark.sql
import org.apache.spark.sql.SaveMode
import org.dataVectis.map
import org.dataVectis.map.Event

import scala.io.Source.fromFile

class sendCsvFile {

  def readData(fileName: String): List[map.Event] = {
    fromFile(fileName).getLines().drop(1)
      .map(line => line.split(",").map(_.trim))
      .map(parts => Event(parts(0) , parts(1).toInt , parts(2).toLong ) )
      .toList

  }


  def exportToLocal ( df : sql.DataFrame  , path : String): Unit= {

    df
      .repartition(1)
      .write
      .mode(SaveMode.Overwrite)
      .format("csv")
      .save(path)




  }

}
