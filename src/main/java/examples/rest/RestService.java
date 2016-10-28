package examples.rest;

import javax.inject.Inject;

import io.baratine.service.Service;
import io.baratine.web.Get;
import io.baratine.web.RequestWeb;

@Service
public class RestService
{
  @Inject
  @Service
  private IntuitProxy _intuit;

  @Get("/hello")
  public void hello(RequestWeb requestWeb)
  {
    _intuit.service(requestWeb.then());
  }
}
