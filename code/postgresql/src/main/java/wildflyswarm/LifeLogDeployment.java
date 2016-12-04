package wildflyswarm;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.ClassLoaderAsset;
import org.wildfly.swarm.jaxrs.JAXRSArchive;

public class LifeLogDeployment {

  public static JAXRSArchive deployment() {
    JAXRSArchive archive = ShrinkWrap.create(JAXRSArchive.class);

    archive.addPackages(true, "lifelog");
    archive.addAsWebInfResource(
      new ClassLoaderAsset("META-INF/persistence.xml", Bootstrap.class.getClassLoader()),
      "classes/META-INF/persistence.xml");

    return archive;
  }

}
