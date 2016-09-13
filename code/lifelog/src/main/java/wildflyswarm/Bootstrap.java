package wildflyswarm;

public class Bootstrap {

  public static void main(String[] args) throws Exception {
    LifeLogContainer.newContainer(args)
        .start()
        .deploy(LifeLogDeployment.deployment());
  }

}
