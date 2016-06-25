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
      .jdbcDriver(resolve("database.driver.name"), d -> d
        .driverClassName(resolve("database.driver.className"))
        .xaDatasourceClass(resolve("database.driver.xaDatasourceClass"))
        .driverModuleName(resolve("database.driver.moduleName"))
      )
      .dataSource(datasourceName, ds -> ds
        .driverName(resolve("database.driver.name"))
        .connectionUrl(databaseConnectionUrl())
        .userName(resolve("database.userName"))
        .password(resolve("database.password"))
      );
  }

  JPAFraction jpaFraction(String datasourceName) {
    return new JPAFraction()
      .inhibitDefaultDatasource()
      .defaultDatasource("jboss/datasources/" + datasourceName);
  }

  private String databaseConnectionUrl() {
    String urlFromEnv = System.getenv("DB_PORT_5432_TCP_ADDR") + ":" + System.getenv("DB_PORT_5432_TCP_PORT");

    return urlFromEnv.equals(":") ?
      resolve("database.connection.url") :
      "jdbc:postgresql://" + urlFromEnv + "/lifelog";
  }

  private String resolve(String key) {
    return container.stageConfig().resolve(key).getValue();
  }

}
