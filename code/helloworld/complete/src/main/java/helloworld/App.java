package helloworld;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.wildfly.swarm.container.Container;
import org.wildfly.swarm.jaxrs.JAXRSArchive;

public class App {

  public static void main(String[] args) throws Exception {
    Container container = new Container(args);

    JAXRSArchive deployment = ShrinkWrap.create(JAXRSArchive.class);
    deployment.addPackages(true, App.class.getPackage());

    deployment.setContextRoot("helloworld");

    container.start().deploy(deployment);
  }

}
