import java.util.Set;
import javax.validation.Valid;
import lombok.Getter;

@Getter
public class WordScannerConfiguraton {
  @Valid private Set<String> allowedTags;
}
