package lifelog;

import org.wildfly.swarm.container.Container;
import org.wildfly.swarm.datasources.DatasourcesFraction;
import org.wildfly.swarm.jpa.JPAFraction;

public class LifeLogConfiguration {

  private Container container;

  LifeLogConfiguration(Container container) {
    this.container = container;
  }

  DatasourcesFraction datasourcesFraction(String datasourceName) {
    return new DatasourcesFraction()
        .jdbcDriver(resolve("database.driver.name"), (d) -> {
          d.driverClassName(resolve("database.driver.className"));
          d.xaDatasourceClass(resolve("database.driver.xaDatasourceClass"));
          d.driverModuleName(resolve("database.driver.moduleName"));
        })
        .dataSource(datasourceName, (ds) -> {
          ds.driverName(resolve("database.driver.name"));
          ds.connectionUrl(resolve("database.connection.url"));
          ds.userName(resolve("database.userName"));
          ds.password(resolve("database.password"));
        });
  }

  JPAFraction jpaFraction(String datasourceName) {
    return new JPAFraction()
        .inhibitDefaultDatasource()
        .defaultDatasource("jboss/datasources/" + datasourceName);
  }

  private String resolve(String key) {
    return container.stageConfig().resolve(key).getValue();
  }

}
