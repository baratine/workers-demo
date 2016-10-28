package qa;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import com.caucho.junit.ConfigurationBaratine;
import com.caucho.junit.ServiceTest;
import com.caucho.junit.WebRunnerBaratine;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import examples.rest.BlockingService;
import examples.rest.RestService;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(WebRunnerBaratine.class)
@ServiceTest(RestService.class)
@ServiceTest(BlockingService.class)
@ConfigurationBaratine(testTime = -1)
public class RestServiceTest
{
  private SlowServerMock _slowServer;

  private ExecutorService _executors;

  @Before
  public void setup()
  {
    _slowServer = new SlowServerMock();
    _executors = Executors.newFixedThreadPool(4);
  }

  @After
  public void destroy()
  {
    _slowServer.stop();
    _executors.shutdownNow();
  }

  @Test
  public void test()
    throws IOException, ExecutionException, InterruptedException
  {
    Future<long[]> call1 = _executors.submit(this::invoke);
    Future<long[]> call2 = _executors.submit(this::invoke);

    long[] response1 = call1.get();
    System.out.println();
    System.out.println();
    System.out.println();
    System.out.println("RestServiceTest.test response 1 received"
                       + System.currentTimeMillis());
    long[] response2 = call2.get();
    System.out.println("RestServiceTest.test response 2 received"
                       + System.currentTimeMillis());

    System.out.println();

    System.out.println(Arrays.toString(response1));
    System.out.println(Arrays.toString(response2));

    //responseX[0] indicates when slow recieved request for processing
    //responseX[1] indicates when response was received into the future

    //assert that slow received request at the 'same' time
    //with accuracy of 100 ms

    Assert.assertTrue(Math.abs(response1[0] - response2[0]) < 100);

    //assert that future received responses ~10s apart
    //with accuracy of 100 ms
    Assert.assertTrue(Math.abs(response1[1] - response2[1]) < 10100);

    //assert that processing of request 1 took more then 2000 ms
    Assert.assertTrue(response1[1] - response1[0] >= 2000);

    //assert that processing of request 2 took more then 12000 ms
    Assert.assertTrue(response2[1] - response2[0] >= 12000);

    //assert that processing of request took less then 2100 ms
    Assert.assertTrue(response1[1] - response1[0] < 2100);
    //assert that processing of request took less then 12100 ms
    Assert.assertTrue(response2[1] - response2[0] < 12100);
  }

  private long[] invoke() throws IOException
  {
    HttpURLConnection conn
      = (HttpURLConnection) new URL("http://localhost:8080/hello").openConnection();

    byte[] bytes = new byte[64];

    int l = conn.getInputStream().read(bytes);

    conn.disconnect();

    //should be 2 seconds after slow received its request
    long responseTime = System.currentTimeMillis();

    return new long[]{Long.parseLong(new String(bytes, 0, l)),
                      responseTime};
  }

  private static class SlowServerMock implements HttpHandler
  {
    HttpServer _server;
    int[] m = new int[]{1, 6};
    AtomicInteger i = new AtomicInteger();

    public SlowServerMock()
    {
      try {
        InetSocketAddress localhost = new InetSocketAddress(8888);

        _server = HttpServer.create(localhost, 0);

        _server.createContext("/").setHandler(this);

        _server.setExecutor(Executors.newFixedThreadPool(4));

        _server.start();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public void handle(HttpExchange httpExchange) throws IOException
    {
      InputStream requestBody = httpExchange.getRequestBody();

      while (requestBody.read() > 0) ;

      // response indicates when the request was received by slow server
      byte[] response = Long.toString(System.currentTimeMillis()).getBytes();

      try {
        int sleep = 2000 * m[i.getAndIncrement()];
        System.out.println("\n\nSlowServerMock.sleep [" + this + "]" + sleep);
        Thread.sleep(sleep);

      } catch (InterruptedException e) {
        e.printStackTrace();
      }

      httpExchange.sendResponseHeaders(200, response.length);

      OutputStream responseBody = httpExchange.getResponseBody();

      responseBody.write(response);

      responseBody.close();
    }

    public void stop()
    {
      _server.stop(1);
    }
  }
}
