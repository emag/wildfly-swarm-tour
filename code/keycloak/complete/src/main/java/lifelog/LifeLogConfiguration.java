package lifelog;

import org.wildfly.swarm.Swarm;
import org.wildfly.swarm.datasources.DatasourcesFraction;
import org.wildfly.swarm.jpa.JPAFraction;

public class LifeLogConfiguration {

  private Swarm swarm;

  LifeLogConfiguration(Swarm swarm) {
    this.swarm = swarm;
  }

  DatasourcesFraction datasourcesFraction(String datasourceName) {
    return new DatasourcesFraction()
      .dataSource(datasourceName, (ds) -> {
        ds.driverName(resolve("database.driver.name"));
        ds.connectionUrl(resolve("database.connection.url"));
        ds.userName(resolve("database.userName"));
        ds.password(resolve("database.password"));
      });
  }

  JPAFraction jpaFraction(String datasourceName) {
    return new JPAFraction()
      .defaultDatasource("jboss/datasources/" + datasourceName);
  }

  private String resolve(String key) {
    return swarm.stageConfig().resolve(key).getValue();
  }

}
