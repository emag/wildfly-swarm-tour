package lifelog;

import org.wildfly.swarm.Swarm;

public class LifeLogContainer {

  private static final String DATASOURCE_NAME = "lifelogDS";

  public static Swarm newContainer(String[] args) throws Exception {
    Swarm swarm = new Swarm(args);

    LifeLogConfiguration configuration = new LifeLogConfiguration(swarm);

    swarm
      .fraction(configuration.jpaFraction(DATASOURCE_NAME));

    return swarm;
  }

}
