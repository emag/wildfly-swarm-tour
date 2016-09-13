package wildflyswarm;

import org.wildfly.swarm.Swarm;
import org.wildfly.swarm.datasources.DatasourcesFraction;
import org.wildfly.swarm.jpa.JPAFraction;

public class LifeLogContainer {

  public static Swarm newContainer(String[] args) throws Exception {
    Swarm swarm = new Swarm(args);

    swarm.fraction(new DatasourcesFraction()
        .dataSource("lifelogDS", (ds) -> {
          ds.driverName("h2");
          ds.connectionUrl("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
          ds.userName("sa");
          ds.password("sa");
        })
    );

    swarm.fraction(new JPAFraction()
        .defaultDatasource("jboss/datasources/lifelogDS")
    );

    return swarm;
  }

}
