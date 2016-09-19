package wildflyswarm;

import helloworld.HelloWorld;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.wildfly.swarm.Swarm;
import org.wildfly.swarm.jaxrs.JAXRSArchive;

public class Bootstrap {

  public static void main(String[] args) throws Exception {
    Swarm swarm = new Swarm(args); // (1)

    // (4) ShrinkWrap の API を使ってデプロイできる形にパッケージング
    JAXRSArchive archive = ShrinkWrap.create(JAXRSArchive.class);
    archive.addClass(HelloWorld.class);

    swarm
      .start() // (2)
      .deploy(archive); // (3)
  }

}
