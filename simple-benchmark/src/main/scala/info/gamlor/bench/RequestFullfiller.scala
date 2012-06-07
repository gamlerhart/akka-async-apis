package info.gamlor.bench

import akka.actor.ActorSystem
import akka.dispatch.{Promise, Future}
import org.adbcj.ConnectionManagerProvider
import info.gamlor.db.{DatabaseAccess, DatabaseSettings, DBPreparedQuery, Database}
import org.adbcj.jdbc.JdbcConnectionManagerFactory
import com.jolbox.bonecp.{BoneCP, BoneCPConfig}
import org.adbcj.jdbc.connectionpools.{BoneCPConnection}
import java.util.concurrent.Executors
import java.sql.{ResultSet, Connection}
import collection.mutable.ListBuffer

/**
 * @author roman.stoffel@gamlor.info
 */

trait RequestFullfiller {

  def system: ActorSystem

  def requestTweets(forUser: String): RequestResponse

  def requestRetweet(tweetToRetweet: Tweet, userName: String): Future[Unit]

}

class NullFullfiller(val system: ActorSystem) extends RequestFullfiller {

  private implicit val dispatcher = system.dispatcher

  def requestTweets(forUser: String) = {
    RequestResponse(Promise.successful(Seq(Tweet("Message", forUser))), Promise.successful(Seq(Tweet("Related", forUser))))
  }

  def requestRetweet(tweetToRetweet: Tweet, userName: String) = {
    Promise.successful[Unit]()
  }
}

class AysncDBApiFullfiller(val system: ActorSystem) extends RequestFullfiller {
  val dbSupport = Database(system)
//  val dbSupport = {
//
//    val boneCP = new BoneCPConfig()
//    boneCP.setJdbcUrl("jdbc:mysql://localhost/dbbench?characterEncoding=UTF-8")
//    boneCP.setUsername("dbbench")
//    boneCP.setPassword("dbbench")
//    boneCP.setPartitionCount(4)
//    boneCP.setMaxConnectionsPerPartition(64)
//
//
//    val connectionManager = new JdbcConnectionManagerFactory()
//      .createConnectionManager(Executors.newCachedThreadPool(),
//      new  BoneCPConnection(new BoneCP(boneCP)))
//      new DatabaseAccess(connectionManager, system.dispatcher)
//
//  }

  private implicit val dispatcher = system.dispatcher

  def requestTweets(forUser: String) = {
    val similarUser = forUser.substring(0,forUser.length-1)+"%"
    val loadedTweets = Promise[Seq[Tweet]]
    val similarTwitterers = Promise[Seq[Tweet]]
    dbSupport.withConnection {
      conn =>
        val doneFuture = for {
          loadTweetsStatement <- conn.prepareQuery("SELECT message,username FROM messages, user " +
            "WHERE username LIKE ? AND messages.userId = user.id " +
            "ORDER BY messages.postTS ASC LIMIT 0,10")
          _ <- {
            val directTweets = loadTweetsForUser(loadedTweets, forUser, loadTweetsStatement)
            val indirectTweets = loadTweetsForUser(similarTwitterers, similarUser, loadTweetsStatement)
            for {
              _ <- directTweets
              _ <- indirectTweets
            } yield "Done"

          }
        } yield ""
        doneFuture
    }
    RequestResponse(loadedTweets, similarTwitterers)
  }

  def requestRetweet(tweetToRetweet: Tweet, userName: String) = {
    dbSupport.withConnection {
      conn =>
        val operation = for {
          userIdQuery <- conn.prepareQuery("SELECT id FROM user where username LIKE ? LIMIT 0,1")
          insertNewTweet <- conn.prepareUpdate("INSERT INTO messages(message,userId,postTS) VALUES (?,?,?)")
          _ <- for {
            id <- userIdQuery.execute(userName)
            _ <- insertNewTweet.execute("RT: " + tweetToRetweet.message + " via @" + tweetToRetweet.user, id(0, 0).getLong, System.currentTimeMillis())
          } yield id

        } yield ""
        operation.map(s => ())
    }

  }


  private def loadTweetsForUser(usersWithPromiseToComplete: Promise[Seq[Tweet]], name: String, preparedQuery: DBPreparedQuery): Future[Seq[Tweet]] = {
    val resultSet = preparedQuery.execute(name)
      .map(rs => rs.map(row => Tweet(row("message").getString, row("username").getString)))
    usersWithPromiseToComplete.completeWith(resultSet)
    resultSet
  }
}

class WithPlainJDBC(val system: ActorSystem) extends RequestFullfiller {

  val jdbc = {
      val boneCP = new BoneCPConfig()
      boneCP.setJdbcUrl("jdbc:mysql://localhost/dbbench?characterEncoding=UTF-8")
      boneCP.setUsername("dbbench")
      boneCP.setPassword("dbbench")
      boneCP.setPartitionCount(4)
      boneCP.setMaxConnectionsPerPartition(64)

    new BoneCP(boneCP)
  }
  def requestTweets(forUser: String) = {

    val result =Future{
      val similarUser = forUser.substring(0,forUser.length-1)+"%"
      val connection: Connection = jdbc.getConnection
      val stmt = connection.prepareStatement("SELECT message,username FROM messages, user " +
        "WHERE username LIKE ? AND messages.userId = user.id " +
        "ORDER BY messages.postTS ASC LIMIT 0,10")


      val directTweets = {
        val buffer = new ListBuffer[Tweet]()
        stmt.setString(1,forUser)
        val resultSet = stmt.executeQuery()
        while(resultSet.next()){
          buffer.append(Tweet(resultSet.getString("message"),resultSet.getString("username")))
        }
        resultSet.close();
        buffer
      }

      val relatedTweets = {
        val buffer = new ListBuffer[Tweet]()
        stmt.setString(1,similarUser)
        val resultSet = stmt.executeQuery()
        while(resultSet.next()){
          buffer.append(Tweet(resultSet.getString("message"),resultSet.getString("username")))
        }
        resultSet.close();
        buffer
      }

      stmt.close()
      connection.close()
      (directTweets,relatedTweets)
    }
    RequestResponse(result.map(_._1),result.map(_._2))

  }

  def requestRetweet(tweetToRetweet: Tweet, userName: String) = {
    Future[Unit]{

      val connection: Connection = jdbc.getConnection
      val getUserQuery = connection.prepareStatement("SELECT id FROM user where username LIKE ? LIMIT 0,1")
      val insertUser = connection.prepareStatement("INSERT INTO messages(message,userId,postTS) VALUES (?,?,?)")

      val userId = {
          getUserQuery.setString(1,userName)
          val resultSet = getUserQuery.executeQuery()
          resultSet.next()
          val userId = resultSet.getLong(1);
          resultSet.close()
          userId
      }

      insertUser.setString(1,"RT: " + tweetToRetweet.message + " via @" + tweetToRetweet.user)
      insertUser.setLong(2,userId)
      insertUser.setLong(3,System.currentTimeMillis())

      insertUser.executeUpdate()


      getUserQuery.close()
      insertUser.close()

      connection.close()

    }
  }
}


case class RequestResponse(tweets: Future[Seq[Tweet]], relatedTweets: Future[Seq[Tweet]])

case class Tweet(message: String, user: String)
