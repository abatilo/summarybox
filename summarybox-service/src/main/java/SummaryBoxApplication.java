import io.dropwizard.Application;
import io.dropwizard.setup.Environment;

public class SummaryBoxApplication extends Application<SummaryBoxConfiguration> {
  @Override public void run(SummaryBoxConfiguration configuration, Environment environment)
      throws Exception {
    BaseResourceFactory.ResourceFactory resources =
        new BaseResourceFactory.ResourceFactory(configuration, environment);
    environment.jersey().register(resources.getRootResource());
  }

  public static void main(String[] args) throws Exception {
    new SummaryBoxApplication().run(args);
  }
}
