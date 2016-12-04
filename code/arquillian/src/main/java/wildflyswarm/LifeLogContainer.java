package wildflyswarm;

import org.wildfly.swarm.Swarm;
import org.wildfly.swarm.datasources.DatasourcesFraction;

public class LifeLogContainer {

  public static Swarm newContainer(String[] args) throws Exception {
    Swarm swarm = new Swarm(args);

    swarm.fraction(new DatasourcesFraction()
      .dataSource("lifelogDS", (ds) -> ds
        .driverName("h2")
        .connectionUrl("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
        .userName("sa")
        .password("sa"))
    );

    return swarm;
  }

}
