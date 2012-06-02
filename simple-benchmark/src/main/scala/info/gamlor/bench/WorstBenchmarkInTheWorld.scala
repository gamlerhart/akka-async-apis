package info.gamlor.bench

import akka.actor.{Actor, ActorSystem}
import akka.util.duration._
import info.gamlor.db.{DBConnection, Database}
import akka.dispatch.{Await, Future}
import com.typesafe.config.ConfigFactory
import util.Random

/**
 * @author roman.stoffel@gamlor.info
 */

object WorstBenchmarkInTheWorld extends App {
  private val AmountOfUsers = 20000

  implicit val akkaSystem = ActorSystem("benchmark",ConfigFactory.load().getConfig("benchmark"))

  main()

  def main() {
    val setupFuture = setup()

    Await.result(setupFuture,10 minutes)
    println("Done")

  }

  def setup() : Future[Unit]= {

    Database(akkaSystem).withConnection {
      conn => {
       for{
         _ <-createSchema(conn)
         _ <-insertUsersAndPosts(conn)
      } yield ()
    }

  }}


  private def createSchema(conn: DBConnection): Future[String] =
    for {
      _ <- conn.executeUpdate("DROP TABLE IF EXISTS messages;")
      _ <- conn.executeUpdate("DROP TABLE IF EXISTS user;")
      _ <- conn.executeUpdate( """CREATE TABLE IF NOT EXISTS `messages` (
                                       `id` int(11) NOT NULL AUTO_INCREMENT,
                                       `message` varchar(255) NOT NULL,
                                       `userId` int(11) NOT NULL,
                                       `postTS` bigint(20) NOT NULL ,
                                       PRIMARY KEY (`id`),
                                       KEY `userId` (`userId`),
                                       KEY `postTS` (`postTS`)
                                     ) ENGINE=InnoDB;""")
      - <- conn.executeUpdate( """CREATE TABLE IF NOT EXISTS `user` (
                                       `id` int(11) NOT NULL AUTO_INCREMENT,
                                       `username` varchar(255) NOT NULL,
                                       PRIMARY KEY (`id`),
                                       KEY `username` (`username`)
                                     ) ENGINE=InnoDB; """)
    } yield "done"

  private def insertUsersAndPosts(connection: DBConnection): Future[Unit] = {
    val inserts = for {
      insertUser <- connection.prepareUpdate("INSERT INTO user(username) VALUES (?)")
      insertPost <- connection.prepareUpdate("INSERT INTO messages(message,userId,postTS) VALUES (?,?,?)")
      insertedUsers <- Future.sequence(
        for (i <- 0 to AmountOfUsers)
        yield insertUser.execute("UserNo " + i)
      )
      initialPost <- Future.sequence(
        for (user <- insertedUsers)
        yield insertPost.execute("#Hello I'm new here. Ths is like #Twitter",
          user.generatedKeys(0, 0).getInt,
          System.currentTimeMillis()))
      secondPost <- Future.sequence(
        for (user <- insertedUsers)
        yield insertPost.execute("#Love the number #" + user.generatedKeys(0, 0) + " the most",
          user.generatedKeys(0, 0).getInt,
          System.currentTimeMillis()))
      thirdPost <- Future.sequence(
        for (user <- insertedUsers)
        yield insertPost.execute("This is my #third entry",
          user.generatedKeys(0, 0).getInt,
          System.currentTimeMillis()))
      lastEntry <- Future.sequence(
        for (user <- insertedUsers)
        yield insertPost.execute("This is my #last entry",
          user.generatedKeys(0, 0).getInt,
          System.currentTimeMillis()))

    } yield secondPost

    inserts.map(r => ())
  }


  class RequestSender extends Actor {
    private var requestsPerSecond = 20;
    private var rnd = new Random()

    override def preStart() {
      context.system.scheduler.schedule(1 seconds, 1 seconds, self, NewRequest)
      context.system.scheduler.schedule(30 seconds, 30 seconds, self, IncreaseRequestPerSecond)
    }

    protected def receive = {
      case NewRequest => {
        val requests = pickSomeUsers()

      }
      case IncreaseRequestPerSecond => {
        requestsPerSecond = requestsPerSecond + 10
      }

    }

    private def pickSomeUsers() = {
      for (times <- 1 to requestsPerSecond) yield "UserNo " + rnd.nextInt(AmountOfUsers)
    }


    case object NewRequest
    case object IncreaseRequestPerSecond

  }


}




class Request(user: String) {
  val startTime = System.currentTimeMillis()





}
