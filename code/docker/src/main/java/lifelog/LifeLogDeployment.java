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
    JAXRSArchive deployment = ShrinkWrap.create(JAXRSArchive.class);

    deployment.addPackages(true, App.class.getPackage());
    deployment.addAsWebInfResource(
      new ClassLoaderAsset("META-INF/persistence.xml", App.class.getClassLoader()), "classes/META-INF/persistence.xml");

    deployment.as(Secured.class)
      .protect("/entries/*")
      .withMethod("POST", "PUT", "DELETE")
      .withRole("author");
    replaceKeycloakJson(deployment);

    return deployment;
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
        line = line.replace("change_me", authServerUrl());
        sb.append(line).append("\n");
      });
    } catch (IOException e) {
      e.printStackTrace();
    }
    deployment.add(new ByteArrayAsset(sb.toString().getBytes()), keycloakPath);
  }

  private static String authServerUrl() {
    String urlFromEnv = System.getenv("AUTH_PORT_8080_TCP_ADDR") + ":" + System.getenv("AUTH_PORT_8080_TCP_PORT");

    if (! urlFromEnv.equals(":")) {
      return "http://" + urlFromEnv +  "/auth";
    }

    return System.getProperty("swarm.auth.server.url", "http://localhost:18080/auth");
  }

}
