package wildflyswarm;

import org.wildfly.swarm.Swarm;
import org.wildfly.swarm.datasources.DatasourcesFraction;
import org.wildfly.swarm.jpa.JPAFraction;

public class LifeLogConfiguration {

  private Swarm swarm;

  LifeLogConfiguration(Swarm swarm) {
    this.swarm = swarm;
  }

  DatasourcesFraction datasourcesFraction(String datasourceName) {
    DatasourcesFraction datasourcesFraction = new DatasourcesFraction()
      .dataSource(datasourceName, (ds) -> ds
        .driverName(resolve("database.driver.name"))
        .connectionUrl(databaseConnectionUrl())
        .userName(resolve("database.userName"))
        .password(resolve("database.password"))
      );

    if(swarm.stageConfig().getName().equals("it")
      || swarm.stageConfig().getName().equals("production")) {
      datasourcesFraction.jdbcDriver("postgresql", (d) -> d
        .driverClassName(resolve("database.driver.className"))
        .xaDatasourceClass(resolve("database.driver.xaDatasourceClass"))
        .driverModuleName(resolve("database.driver.moduleName"))
      );
    }

    return datasourcesFraction;
  }

  JPAFraction jpaFraction(String datasourceName) {
    return new JPAFraction()
      .defaultDatasource("jboss/datasources/" + datasourceName);
  }

  private String databaseConnectionUrl() {
    String urlFromEnv = System.getenv("DB_PORT_5432_TCP_ADDR") + ":" + System.getenv("DB_PORT_5432_TCP_PORT");

    return urlFromEnv.equals("null:null")
      ? resolve("database.connection.url")
      : "jdbc:postgresql://" + urlFromEnv + "/lifelog";
  }

  private String resolve(String key) {
    return swarm.stageConfig().resolve(key).getValue();
  }

}
