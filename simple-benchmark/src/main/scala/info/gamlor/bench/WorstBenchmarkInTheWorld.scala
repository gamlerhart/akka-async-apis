package info.gamlor.bench

import akka.util.duration._
import akka.dispatch.{Await, Future}
import com.typesafe.config.ConfigFactory
import util.Random
import akka.actor.{Props, Actor, ActorSystem}
import akka.routing.BroadcastRouter
import info.gamlor.db.{DatabaseAccess, DBConnection, Database}

/**
 * @author roman.stoffel@gamlor.info
 */

object WorstBenchmarkInTheWorld extends App {
  private val AmountOfUsers = 20000

  implicit val akkaSystem = ActorSystem("benchmark", ConfigFactory.load().getConfig("benchmark"))

  main()


  def main() {

    //    val requestFullFiller = new NullFullfiller(akkaSystem)
    val requestFullFiller = new AysncDBApiFullfiller(akkaSystem)
    val setupFuture = setup(requestFullFiller.dbSupport)

    Await.result(setupFuture, 10 minutes)
    println("Setup Done")


    runBenchmark(requestFullFiller)

    Thread.sleep(60000)
    Thread.sleep(60000)

    println(">>>>>>>>>>>>>>>>>Done?>>>>>>>>>")
    Thread.sleep(1000)


  }

  def setup(dbAccess:DatabaseAccess): Future[Unit] = {

    dbAccess.withConnection {
      conn => {
        for {
          _ <- createSchema(conn)
          _ <- insertUsersAndPosts(conn)
        } yield ()
      }

    }
  }

  def runBenchmark(requestFullFiller:RequestFullfiller) {
    val loadCreators = akkaSystem.actorOf(Props(new RequestSender(requestFullFiller)).withRouter(BroadcastRouter(30)))


    loadCreators ! Start
  }


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


  class RequestSender(tweetSystem: RequestFullfiller) extends Actor {
    private var amountOfRounds = 60
    private val rnd = new Random()

    override def preStart() {
    }

    protected def receive = {
      case NewRequest => {
        val user = pickSomeUsers()
        new Request(user, tweetSystem).run()
        amountOfRounds = amountOfRounds - 1

        if (amountOfRounds > 0) {
          context.system.scheduler.scheduleOnce(950 + rnd.nextInt(100) millis, self, NewRequest)
        } else {
          context.stop(self)
        }
      }
      case Start => {
        context.system.scheduler.scheduleOnce(rnd.nextInt(1000) millis, self, NewRequest)
      }

    }

    private def pickSomeUsers() = {
      "UserNo " + rnd.nextInt(AmountOfUsers)
    }


    case object NewRequest

  }

  class Request(userName: String, tweetSystem: RequestFullfiller) {
    val startTime = System.currentTimeMillis()
    val rnd = new Random()


    def run() {
      val tweetsAndRelated = tweetSystem.requestTweets(userName)

      val retweetDone = for {
        tweets <- tweetsAndRelated.tweets
        retweet <- tweetSystem.requestRetweet(tweets(rnd.nextInt(tweets.length)), "UserNo " + rnd.nextInt(AmountOfUsers))
      } yield retweet

      val relatedData = tweetsAndRelated.relatedTweets

      val allDone = for {
        rd <- relatedData
        rt <- retweetDone
      } yield ()

      allDone.onSuccess {
        case _ => {
          println("Requests finished in " + (System.currentTimeMillis() - startTime))
        }
      }

    }


  }


}


case object Start


