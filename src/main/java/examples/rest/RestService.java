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
  private BlockingService _blockingService;

  @Get("/hello")
  public void hello(RequestWeb requestWeb)
  {
    _blockingService.service(requestWeb.then());
  }
}
