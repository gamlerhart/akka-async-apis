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


  def withTestServerExtended[A](serverImpl: (Request, Response, TestWebServer) => Unit, test: TestWebServer => A) {
    val server = new TestWebServer(serverImpl)
    try {
      test(server)
    } finally {
      server.close()
    }
  }

  def withTestServer[A](serverImpl: (Request, Response) => Unit, test: TestWebServer => A) {
    withTestServerExtended((req, resp, server) => serverImpl(req, resp), test)
  }

  val HelloWorld = (req: Request, response: Response) => {
    addDefaultHeaders(response)
    val body = response.getPrintStream;

    body.println("Hello World");
    body.close();
  }

  val EchoServer = (req: Request, response: Response) => {

    addDefaultHeaders(response)
    val body = response.getPrintStream;

    body.println(req.getContent);
    body.close();
  }
  val VerySlowServer = (req: Request, response: Response) => {

    Thread.sleep(7000)
    addDefaultHeaders(response)
    val body = response.getPrintStream;
    body.println("Hello World");
    body.close();
  }
  val FailCompletly = (req: Request, response: Response, server: TestWebServer) => {
    server.close()
    throw new RuntimeException("Simulated Error on the Server")
  }
  private def addDefaultHeaders(response: Response) {
    val time = System.currentTimeMillis();
    response.set("Content-Type", "text/plain");
    response.set("Server", "HelloWorld/1.0 (Simple 4.0)");
    response.setDate("Date", time);
    response.setDate("Last-Modified", time);
  }
}


class TestWebServer(serverBehavior: (Request, Response, TestWebServer) => Unit) {


  val port = findFreePort()
  val url = "http://localhost:" + port
  private val connection = new SocketConnection(new Container {
    def handle(req: Request, resp: Response) {
      serverBehavior(req, resp, TestWebServer.this)
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

