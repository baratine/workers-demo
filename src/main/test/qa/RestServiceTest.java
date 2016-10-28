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
    long[] response2 = call2.get();

    System.out.println();

    System.out.println(Arrays.toString(response1));
    System.out.println(Arrays.toString(response2));

    //responseX[0] indicates when slow recieved request for processing
    //responseX[1] indicates when response was received into the future

    //assert that slow received request at the 'same' time
    //with accuracy of 100 ms

    long nano = 1000000;

    Assert.assertTrue(Math.abs(response1[0] - response2[0]) < (100 * nano));

    //assert that future received response at the 'same' time
    //with accuracy of 100 ms
    Assert.assertTrue(Math.abs(response1[1] - response2[1]) < (100 * nano));

    //assert that processing of request took more then 2000 ms
    Assert.assertTrue(response1[1] - response1[0] >= (2000 * nano));
    Assert.assertTrue(response2[1] - response2[0] >= (2000 * nano));

    //assert that processing of request took less then 2100 ms
    Assert.assertTrue(response1[1] - response1[0] < (2100 * nano));
    Assert.assertTrue(response2[1] - response2[0] < (2100 * nano));
  }

  private long[] invoke() throws IOException
  {
    HttpURLConnection conn
      = (HttpURLConnection) new URL("http://localhost:8080/hello").openConnection();

    byte[] bytes = new byte[64];

    int l = conn.getInputStream().read(bytes);

    conn.disconnect();

    //should be 2 seconds after slow received its request
    long responseTime = System.nanoTime();

    return new long[]{Long.parseLong(new String(bytes, 0, l)),
                      responseTime};
  }

  private static class SlowServerMock implements HttpHandler
  {
    HttpServer _server;

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
      byte[] response = Long.toString(System.nanoTime()).getBytes();

      try {
        Thread.sleep(2000);
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


