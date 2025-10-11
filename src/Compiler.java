import top.tsxb.compiler.Pipeline;
import top.tsxb.compiler.config.Config;

/** The type Compiler. */
public class Compiler {
  /**
   * The entry point of application.
   *
   * @param args the input arguments
   */
  public static void main(String[] args) {
    String configPath;
    if (args.length > 0) {
      configPath = args[0];
    } else {
      configPath = "config/compiler.properties";
    }

    Config config = new Config(configPath);
    Pipeline pipeline = new Pipeline(config);

    try {
      pipeline.run();
    }  catch (Exception e) {
      e.printStackTrace(System.err);
    }
  }
}
