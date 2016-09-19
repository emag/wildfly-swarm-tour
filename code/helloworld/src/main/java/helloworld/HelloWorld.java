package helloworld;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("/hello")
public class HelloWorld {

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public String hello() {
    return "{\"message\" : \"Hello, WildFly Swarm!\"}";
  }

}
