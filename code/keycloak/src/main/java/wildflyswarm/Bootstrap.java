package wildflyswarm;

import org.wildfly.swarm.Swarm;

public class Bootstrap {

  public static void main(String[] args) throws Exception {
    new Swarm(args)
      .start()
      .deploy(LifeLogDeployment.deployment());
  }

}
