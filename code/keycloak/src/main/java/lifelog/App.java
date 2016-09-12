package lifelog;

public class App {

  public static void main(String[] args) throws Exception {
    LifeLogContainer.newContainer(args)
      .start()
      .deploy(LifeLogDeployment.deployment());
  }

}
