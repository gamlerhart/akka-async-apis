package info.gamlor.bench

import akka.actor.ActorSystem
import akka.dispatch.{Promise, Future}
import org.adbcj.ConnectionManagerProvider
import info.gamlor.db.{DatabaseAccess, DatabaseSettings, DBPreparedQuery, Database}
import org.adbcj.jdbc.JdbcConnectionManagerFactory
import com.jolbox.bonecp.{BoneCP, BoneCPConfig}
import org.adbcj.jdbc.connectionpools.{BoneCPConnection}
import java.util.concurrent.Executors
import collection.mutable.ListBuffer
import java.sql.{DriverManager, ResultSet, Connection}

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
    RequestResponse(Promise.successful(Seq(Tweet("Message", forUser))),
      Promise.successful(Seq(Tweet("Related", forUser))),Promise.successful(0))
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
    val statsPromise = Promise[Int]
    dbSupport.withConnection {
      conn =>
        val doneFuture = for {
          loadTweetsStatement <- conn.prepareQuery("SELECT retweets.message, username  " +
            "FROM messages AS retweets, messages AS original, user " +
            "WHERE username LIKE ? " +
            "AND original.userId = user.id " +
            "AND retweets.retweetOf = original.userId " +
            "ORDER BY retweets.postTS ASC " +
            "LIMIT 0 , 10")
          statsStatement <- conn.prepareQuery("SELECT COUNT( retweets.message) FROM messages AS retweets, messages AS original, user " +
            "WHERE username LIKE ? " +
            "AND original.userId = user.id " +
            "AND retweets.retweetOf = original.userId ")
          _ <- {
            val directTweets = loadTweetsForUser(loadedTweets, forUser, loadTweetsStatement)
            val indirectTweets = loadTweetsForUser(similarTwitterers, similarUser, loadTweetsStatement)
            val stats = statsPromise.completeWith(statsStatement.execute(forUser).map(rs=>rs(0,0).getInt))
            for {
              _ <- directTweets
              _ <- indirectTweets
              _ <- stats
            } yield "Done"

          }
        } yield ""
        doneFuture
    }
    RequestResponse(loadedTweets, similarTwitterers,statsPromise)
  }

  def requestRetweet(tweetToRetweet: Tweet, userName: String) = {
    dbSupport.withConnection {
      conn =>
        val operation = for {
          userIdQuery <- conn.prepareQuery("SELECT id FROM user where username LIKE ? LIMIT 0,1")
          insertNewTweet <- conn.prepareUpdate("INSERT INTO messages(message,userId,retweetOf,postTS) VALUES (?,?,?,?)")
          _ <- for {
            id <- userIdQuery.execute(userName)
            _ <- insertNewTweet.execute("RT: " + tweetToRetweet.message + " via @" + tweetToRetweet.user, id(0, 0).getLong, 0,System.currentTimeMillis())
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

  private implicit val dispatcher = system.dispatcher
  val jdbc = new Object{
//      val boneCP = new BoneCPConfig()
//      boneCP.setJdbcUrl("jdbc:mysql://localhost/dbbench?characterEncoding=UTF-8")
//      boneCP.setUsername("dbbench")
//      boneCP.setPassword("dbbench")
//      boneCP.setPartitionCount(4)
//      boneCP.setMaxConnectionsPerPartition(64)
//
//    new BoneCP(boneCP)

    def getConnection() ={
      DriverManager.getConnection(
        "jdbc:mysql://localhost/dbbench?characterEncoding=UTF-8","dbbench","dbbench")
    }
  }
  def requestTweets(forUser: String) = {

    val result =Future{
      val similarUser = forUser.substring(0,forUser.length-1)+"%"
      val connection: Connection = jdbc.getConnection
      val stmt = connection.prepareStatement("SELECT retweets.message, username " +
        "FROM messages AS retweets, messages AS original, user " +
        "WHERE username LIKE ? " +
        "AND original.userId = user.id " +
        "AND retweets.retweetOf = original.userId " +
        "ORDER BY retweets.postTS ASC " +
        "LIMIT 0 , 10")
      val statsStmt = connection.prepareStatement("SELECT COUNT( retweets.message) FROM messages AS retweets, messages AS original, user " +
        "WHERE username LIKE ? " +
        "AND original.userId = user.id " +
        "AND retweets.retweetOf = original.userId ")


      val directTweets = {
        val buffer = new ListBuffer[Tweet]()
        stmt.setString(1,forUser)
        val resultSet = stmt.executeQuery()
        while(resultSet.next()){
          buffer.append(Tweet(resultSet.getString("message"),resultSet.getString("username")))
        }
        buffer
      }

      val relatedTweets = {
        val buffer = new ListBuffer[Tweet]()
        stmt.setString(1,similarUser)
        val resultSet = stmt.executeQuery()
        while(resultSet.next()){
          buffer.append(Tweet(resultSet.getString("message"),resultSet.getString("username")))
        }
        buffer
      }

      val stats = {
        statsStmt.setString(1,forUser)
        val resultSet = statsStmt.executeQuery()
        resultSet.next()
        resultSet.getInt(1)

      }

      stmt.close()
      connection.close()
      (directTweets,relatedTweets,stats)
    }
    RequestResponse(result.map(_._1),result.map(_._2),result.map(_._3))

  }

  def requestRetweet(tweetToRetweet: Tweet, userName: String) = {
    Future[Unit]{

      val connection: Connection = jdbc.getConnection
      val getUserQuery = connection.prepareStatement("SELECT id FROM user where username LIKE ? LIMIT 0,1")
      val insertUser = connection.prepareStatement("INSERT INTO messages(message,userId,retweetOf,postTS) VALUES (?,?,?,?)")

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
      insertUser.setLong(3,0)
      insertUser.setLong(4,System.currentTimeMillis())

      insertUser.executeUpdate()


      getUserQuery.close()
      insertUser.close()

      connection.close()

    }
  }
}


case class RequestResponse(tweets: Future[Seq[Tweet]], relatedTweets: Future[Seq[Tweet]], amountOfTweets:Future[Int])

case class Tweet(message: String, user: String)
