package com.example

import kafka.serializer.StringDecoder
import org.apache.log4j.Logger
import org.apache.spark.SparkConf
import org.apache.spark.streaming.kafka.KafkaUtils
import org.apache.spark.streaming.{Seconds, StreamingContext}
import org.apache.log4j._
import java.io._
import java.util
import java.util.Arrays._

import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient
import com.amazonaws.services.dynamodbv2.document.DynamoDB
import com.amazonaws.services.dynamodbv2.document.Item
import com.amazonaws.services.dynamodbv2.document.Table
import com.amazonaws.services.dynamodbv2.model._
import com.fasterxml.jackson.core.JsonFactory
import kafka.common.TopicAndPartition
import kafka.message.MessageAndMetadata
import org.apache.spark.rdd.RDD
import play.api.libs.json.{JsValue, Json}
object Hello3 {

  val client:AmazonDynamoDBClient  = new AmazonDynamoDBClient(new BasicAWSCredentials("fake", "fake"))
    .withEndpoint("http://localhost:8000");

  val dynamoDB:DynamoDB  = new DynamoDB(client)
  val tableName:String  = "CarHistoryClassifiedData"//"CarHistoryClassifiedDataTable"
  val table:Table  = dynamoDB.getTable(tableName)

  def messageHandler(messageAndMetadata: MessageAndMetadata[String,String]):Response = {
    Response(s"${messageAndMetadata.message()}  ${messageAndMetadata.key()} offset:${messageAndMetadata.offset}",offset = messageAndMetadata.offset)
  }

  val partition2: Int = 1
  val partition1: Int = 0

  def getContext(): StreamingContext = {
    println("Hello, world!")

    val Array(brokers, topics) = Array("localhost:9092","t1")
    val topicsSet = topics.split(",").toSet
    val kafkaParams = Map[String, String]("metadata.broker.list" -> brokers)

    println(topics)
    println(kafkaParams)

    val sparkConf = new SparkConf().setAppName("DirectKafkaWordCount").setMaster("local[*]")
    val ssc = new StreamingContext(sparkConf, Seconds(5))
    val topicsMap = Map(TopicAndPartition(topics,partition1) ->0L,TopicAndPartition(topics,partition2) ->0L)

    KafkaUtils.createDirectStream[String,String,StringDecoder, StringDecoder, Response](ssc, kafkaParams ,topicsMap,
      x=>messageHandler(x))  foreachRDD {       //.foreachRDD((r,t)=>writeToFile(r,t)) //saveAsTextFiles("pp","txt")
        rdd=> rdd.foreach(r=>writeToFile(r))
    }
    ssc
  }
  var cnt:Int=0
def writeToFile(r:Response): Unit = {
  cnt=cnt+1
  val json: JsValue = Json.parse("""
{
  "name" : "Watership Down",
  "location" : {
    "lat" : 51.235685,
    "long" : -1.309197
  },
  "residents" : [ {
    "name" : "Fiver",
    "age" : 4,
    "role" : null
  }, {
    "name" : "Bigwig",
    "age" : 6,
    "role" : "Owsla"
  } ]
}
                                 """)

  val s= s"""{  "cnt" : "$cnt"  }"""
  val json2: JsValue = Json.parse(s)

  table.putItem(new Item()
    .withPrimaryKey("ClassifiedGuid", r.name, "ArticleId",  r.offset)
    .withJSON("info", json2.toString()));
  }
  def main(args: Array[String]): Unit = {
    //createTable()
    val ssc = StreamingContext.getOrCreate("~/tmp/cats-spark", getContext)
    ssc.start()
    ssc.awaitTermination()
  }

  def createTable(): Unit ={
      println("Attempting to create table; please wait...");
      val table:Table  = dynamoDB.createTable(tableName,
        asList(
          new KeySchemaElement("ClassifiedGuid", KeyType.HASH),  //Partition key
          new KeySchemaElement("ArticleId", KeyType.RANGE)), //Sort key
        asList(
          new AttributeDefinition("ClassifiedGuid", ScalarAttributeType.S),
          new AttributeDefinition("ArticleId", ScalarAttributeType.N)),
        new ProvisionedThroughput(10L, 10L));
      table.waitForActive();
      println("Success.  Table status: " + table.getDescription().getTableStatus());

  }
}
