import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
public class WordPair {
  private final String from;
  private final String to;
}
