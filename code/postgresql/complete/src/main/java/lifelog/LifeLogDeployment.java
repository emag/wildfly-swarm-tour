package lifelog;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.ClassLoaderAsset;
import org.wildfly.swarm.jaxrs.JAXRSArchive;

public class LifeLogDeployment {

  public static JAXRSArchive deployment() {
    JAXRSArchive deployment = ShrinkWrap.create(JAXRSArchive.class);

    deployment.addPackages(true, App.class.getPackage());
    deployment.addAsWebInfResource(
        new ClassLoaderAsset("META-INF/persistence.xml", App.class.getClassLoader()), "classes/META-INF/persistence.xml");

    return deployment;
  }

}
