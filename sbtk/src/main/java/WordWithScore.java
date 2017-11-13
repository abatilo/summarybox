import javax.annotation.Nonnull;

import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
public class WordWithScore implements Comparable<WordWithScore> {
  private final String word;
  private final Integer score;

  @Override public int compareTo(@Nonnull WordWithScore other) {
  return -this.score.compareTo(other.score);
  }
}
