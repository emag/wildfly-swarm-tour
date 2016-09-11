package lifelog;

import org.wildfly.swarm.container.Container;
import org.wildfly.swarm.datasources.DatasourcesFraction;
import org.wildfly.swarm.jpa.JPAFraction;

public class LifeLogContainer {

  public static Container newContainer(String[] args) throws Exception {
    Container container = new Container(args);

    container.fraction(new DatasourcesFraction()
        .jdbcDriver("h2", (d) -> {
          d.driverClassName("org.h2.Driver");
          d.xaDatasourceClass("org.h2.jdbcx.JdbcDataSource");
          d.driverModuleName("com.h2database.h2");
        })
        .dataSource("lifelogDS", (ds) -> {
          ds.driverName("h2");
          ds.connectionUrl("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
          ds.userName("sa");
          ds.password("sa");
        })
    );

    container.fraction(new JPAFraction()
        .defaultDatasource("jboss/datasources/lifelogDS")
    );

    return container;
  }

}
