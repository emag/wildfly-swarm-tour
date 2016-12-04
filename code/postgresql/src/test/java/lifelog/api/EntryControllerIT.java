package lifelog.api;

import lifelog.domain.model.Entry;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.swarm.jaxrs.JAXRSArchive;
import wildflyswarm.LifeLogDeployment;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import java.net.URI;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

@RunWith(Arquillian.class)
public class EntryControllerIT {

  @Deployment(testable = false)
  public static JAXRSArchive createDeployment() {
    return LifeLogDeployment.deployment();
  }

  @ArquillianResource
  private URI deploymentUri;

  @Test
  public void test() {
    UriBuilder baseUri = UriBuilder.fromUri(deploymentUri).path("entries");

    // Create a new entry
    Client client = ClientBuilder.newClient();
    WebTarget target = client.target(baseUri);

    Entry entry = new Entry();
    entry.setDescription("Test");
    Response response = target.request().post(Entity.json(entry));

    assertThat(response.getStatus(), is(Response.Status.CREATED.getStatusCode()));

    URI newEntryLocation = response.getLocation();

    client.close();

    // Get the entry
    client = ClientBuilder.newClient();
    target = client.target(newEntryLocation);
    response = target.request(MediaType.APPLICATION_JSON_TYPE).get();

    assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));
    assertThat(response.readEntity(EntryResponse.class).getDescription(), is("Test"));

    client.close();

    // Delete the entry
    client = ClientBuilder.newClient();
    target = client.target(newEntryLocation);
    response = target.request().delete();

    assertThat(response.getStatus(), is(Response.Status.NO_CONTENT.getStatusCode()));

    client.close();

    // Check no entries
    client = ClientBuilder.newClient();
    target = client.target(baseUri);
    response = target.request(MediaType.APPLICATION_JSON_TYPE).get();

    assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));

    List<EntryResponse> entries = response.readEntity(new GenericType<List<EntryResponse>>() {});
    assertThat(entries.size(), is(0));

    client.close();
  }

}
