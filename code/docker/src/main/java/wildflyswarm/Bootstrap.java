package wildflyswarm;

import org.wildfly.swarm.Swarm;

public class Bootstrap {

  public static void main(String[] args) throws Exception {
    LifeLogConfigurationFromEnv.setupUrl(
      "swarm.datasources.data-sources.lifelogDS.connection-url",
      "DB_PORT_5432_TCP_ADDR",
      "DB_PORT_5432_TCP_PORT",
      "jdbc:postgresql://%s/lifelog"
    );

    LifeLogConfigurationFromEnv.setupUrl(
      "auth.url",
      "AUTH_PORT_8080_TCP_ADDR",
      "AUTH_PORT_8080_TCP_PORT",
      "http://%s/auth"
    );

    new Swarm(args)
      .start()
      .deploy(LifeLogDeployment.deployment());
  }

}
