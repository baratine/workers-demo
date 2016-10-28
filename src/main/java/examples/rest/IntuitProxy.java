package examples.rest;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

import io.baratine.service.Result;
import io.baratine.service.Service;
import io.baratine.service.Startup;
import io.baratine.service.Workers;

@Service
@Workers(20)
@Startup
public class IntuitProxy
{
  public void service(Result<String> result)
  {
    HttpURLConnection conn = null;

    try {
      conn
        = (HttpURLConnection) new URL("http://localhost:8888/").openConnection();

      byte[] bytes = new byte[64];

      int l = conn.getInputStream().read(bytes);

      result.ok(new String(bytes, 0, l));

    } catch (IOException e) {
      result.fail(e);
    }
  }
}
