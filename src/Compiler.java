import top.tsxb.compiler.Pipeline;

/** The type Compiler. */
public class Compiler {
  /**
   * The entry point of application.
   *
   * @param args the input arguments
   */
  public static void main(String[] args) {
    Pipeline pipeline = new Pipeline();

    try {
      pipeline.run();
    }  catch (Exception e) {
      e.printStackTrace(System.err);
    }
  }
}
