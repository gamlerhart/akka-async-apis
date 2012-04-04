package info.gamlor.io

import java.io.{ByteArrayOutputStream, FileInputStream}
import java.util.concurrent.Executors
import java.nio.file.Paths
import akka.dispatch.{Await, ExecutionContext}
import akka.util.duration._


/**
 * @author roman.stoffel@gamlor.info
 * @since 22.03.12
 */

object Experiments2 extends App {
  implicit val context = ExecutionContext.fromExecutorService(Executors.newCachedThreadPool())

  main()

  def main() {
    for(i<- 0 to 10){
      aRun()
    }

  }

  def aRun() {
    val startTime = System.currentTimeMillis()
    val bytesOfFile1 = readFile("C:\\temp\\testData\\data1.data")
    val bytesOfFile2 = readFile("C:\\temp\\testData\\data2.data")
    val bytesOfFile3 = readFile("C:\\temp\\testData\\data3.data")
    val bytesOfFile4 = readFile("C:\\temp\\testData\\data4.data")
    val bytesOfFile5 = readFile("C:\\temp\\testData\\data5.data")
    val bytesOfFile6 = readFile("C:\\temp\\testData\\data6.data")
    val bytesOfFile7 = readFile("C:\\temp\\testData\\data7.data")

    println(bytesOfFile1.length)
    println(bytesOfFile2.length)
    println(bytesOfFile3.length)
    println(bytesOfFile4.length)
    println(bytesOfFile5.length)
    println(bytesOfFile6.length)
    println(bytesOfFile7.length)

    println("Time used " + (System.currentTimeMillis() - startTime))
  }

  def aRunAsync() {
    val startTime = System.currentTimeMillis()
    val futureOfFile1 = FileIO.withFile(Paths.get("C:\\temp\\testData\\data1.data")){f => f.read(0,f.size().toInt)}
    val futureOfFile2 = FileIO.withFile(Paths.get("C:\\temp\\testData\\data2.data")){f => f.read(0,f.size().toInt)}
    val futureOfFile3 = FileIO.withFile(Paths.get("C:\\temp\\testData\\data3.data")){f => f.read(0,f.size().toInt)}
    val futureOfFile4 = FileIO.withFile(Paths.get("C:\\temp\\testData\\data4.data")){f => f.read(0,f.size().toInt)}
    val futureOfFile5 = FileIO.withFile(Paths.get("C:\\temp\\testData\\data5.data")){f => f.read(0,f.size().toInt)}
    val futureOfFile6 = FileIO.withFile(Paths.get("C:\\temp\\testData\\data6.data")){f => f.read(0,f.size().toInt)}
    val futureOfFile7 = FileIO.withFile(Paths.get("C:\\temp\\testData\\data7.data")){f => f.read(0,f.size().toInt)}


    println(Await.result(futureOfFile1, 5 seconds).size)
    println(Await.result(futureOfFile2, 5 seconds).size)
    println(Await.result(futureOfFile3, 5 seconds).size)
    println(Await.result(futureOfFile4, 5 seconds).size)
    println(Await.result(futureOfFile5, 5 seconds).size)
    println(Await.result(futureOfFile6, 5 seconds).size)
    println(Await.result(futureOfFile7, 5 seconds).size)
    println("Time used " + (System.currentTimeMillis() - startTime))
  }

  def readFile(filePath: String):Array[Byte] = {
    val byteBuffer = Array.ofDim[Byte](32 * 1024)
    val inStream = new FileInputStream(filePath)
    val byteAccumulator = new ByteArrayOutputStream();
    try {
      var reading = true
      while (reading) {
        inStream.read(byteBuffer,0,byteBuffer.length) match {
          case -1 => reading = false
          case readBytes => {
            byteAccumulator.write(byteBuffer,0,readBytes)
          }
        }
      }
      byteAccumulator.flush()
    }
    finally {
      inStream.close()
    }
    byteAccumulator.toByteArray
  }

}
