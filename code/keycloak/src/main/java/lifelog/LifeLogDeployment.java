package lifelog;

import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.Node;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.ByteArrayAsset;
import org.jboss.shrinkwrap.api.asset.ClassLoaderAsset;
import org.wildfly.swarm.jaxrs.JAXRSArchive;
import org.wildfly.swarm.keycloak.Secured;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class LifeLogDeployment {

  public static JAXRSArchive deployment() {
    JAXRSArchive archive = ShrinkWrap.create(JAXRSArchive.class);

    archive.addPackages(true, App.class.getPackage());
    archive.addAsWebInfResource(
      new ClassLoaderAsset("META-INF/persistence.xml", App.class.getClassLoader()), "classes/META-INF/persistence.xml");

    archive.as(Secured.class)
      .protect("/entries/*")
      .withMethod("POST", "PUT", "DELETE")
      .withRole("author");
    replaceKeycloakJson(archive);

    return archive;
  }

  private static void replaceKeycloakJson(Archive deployment) {
    String keycloakPath = "WEB-INF/keycloak.json";
    Node keycloakJson = deployment.get(keycloakPath);
    if (keycloakJson == null) {
      // FIXME keycloak.json は wildfly-swarm:run で読めない
      return;
    }

    InputStream is = keycloakJson.getAsset().openStream();
    StringBuilder sb = new StringBuilder();
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
      reader.lines().forEach(line -> {
        line = line.replace("change_me", System.getProperty("swarm.auth.server.url", "http://localhost:18080/auth"));
        sb.append(line).append("\n");
      });
    } catch (IOException e) {
      e.printStackTrace();
    }
    deployment.add(new ByteArrayAsset(sb.toString().getBytes()), keycloakPath);
  }

}
