import java.util.Set;
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import lombok.Getter;

@Getter
public class WordScannerConfiguraton {
  @Valid @Max(10) @Min(1)
  private int similarToTop;
  @Valid @Max(1) @Min(0)
  private double percentile;
  @Valid @Max(10) @Min(1)
  private int topics;
  @Valid private Set<String> allowedTags;
}
