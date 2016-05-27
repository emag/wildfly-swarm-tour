package lifelog;

import org.wildfly.swarm.container.Container;

public class LifeLogContainer {

  private static final String DATASOURCE_NAME = "lifelogDS";

  public static Container newContainer(String[] args) throws Exception {
    Container container = new Container(args);

    LifeLogConfiguration configuration = new LifeLogConfiguration(container);

    container
        .fraction(configuration.datasourcesFraction(DATASOURCE_NAME))
        .fraction(configuration.jpaFraction(DATASOURCE_NAME));

    return container;
  }

}
