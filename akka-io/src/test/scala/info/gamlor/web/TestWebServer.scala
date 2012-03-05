package info.gamlor.web

import org.simpleframework.http.core.Container
import org.simpleframework.transport.connect.SocketConnection
import java.net.{ServerSocket, InetSocketAddress}
import org.simpleframework.http.{Request, Response}

/**
 * @author roman.stoffel@gamlor.info
 * @since 05.03.12
 */


object TestWebServer {

  def withTestServer[A](serverImpl: (Request, Response) => Unit, test: TestWebServer => A) {
    val server = new TestWebServer(serverImpl)
    try {
      test(server)
    } finally {
      server.close()
    }
  }

  val HelloWorld = (req: Request, response: Response) => {
    val body = response.getPrintStream;
    val time = System.currentTimeMillis();

    response.set("Content-Type", "text/plain");
    response.set("Server", "HelloWorld/1.0 (Simple 4.0)");
    response.setDate("Date", time);
    response.setDate("Last-Modified", time);

    body.println("Hello World");
    body.close();
  }
}


class TestWebServer(serverBehavior: (Request, Response) => Unit) {


  val port = findFreePort()
  val url = "http://localhost:"+port
  private val connection = new SocketConnection(new Container {
    def handle(req: Request, resp: Response) {
      serverBehavior(req, resp)
    }
  });

  connection.connect(new InetSocketAddress(port));


  def close() {
    connection.close()
  }

  private def findFreePort() = {
    val tester = new ServerSocket(0)
    val port = tester.getLocalPort
    tester.close()
    port
  }
}

