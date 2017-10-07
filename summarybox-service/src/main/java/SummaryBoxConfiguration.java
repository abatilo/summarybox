import io.dropwizard.Configuration;
import javax.validation.Valid;
import lombok.Getter;

@Getter
public class SummaryBoxConfiguration extends Configuration {
  @Valid private String sentenceModel;
  @Valid private String posModel;
  @Valid private String w2vModel;
  @Valid private String stopWords;
  @Valid private WordScannerConfiguraton wordScanner;
}
