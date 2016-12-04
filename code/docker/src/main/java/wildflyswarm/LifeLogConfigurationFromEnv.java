package wildflyswarm;

public class LifeLogConfigurationFromEnv {

  public static void setupUrl(String property, String addr, String port, String format) {
    if (! isValidUrl(addr, port)) return;

    String urlFromEnv = getUrlFromEnv(addr, port);

    System.setProperty(
      property,
      String.format(format, urlFromEnv));
  }

  private static boolean isValidUrl(String addr, String port) {
    String addrFromEnv = System.getenv(addr);
    if (addrFromEnv == null) return false;

    String portFromEnv = System.getenv(port);
    if (portFromEnv == null) return false;

    return true;
  }

  private static String getUrlFromEnv(String addr, String port) {
    return System.getenv(addr) + ":" + System.getenv(port);
  }

}
