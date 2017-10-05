import io.dropwizard.Configuration;
import javax.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class SummaryBoxConfiguration extends Configuration {
  @NotNull
  private String sentenceModel;
  @NotNull
  private String posModel;
  @NotNull
  private String w2vModel;
  @NotNull
  private String stopWords;
}
