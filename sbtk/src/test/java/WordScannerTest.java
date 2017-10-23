import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class WordScannerTest {

  @Test
  public void testUnigramProbabilities() {
    List<String> words = new ArrayList<>(Arrays.asList((
        "We're no strangers to love "
            + "You know the rules and so do I "
            + "A full commitment's what I'm thinking of "
            + "You wouldn't get this from any other guy "
            + "I just want to tell you how I'm feeling "
            + "Gotta make you understand "
    ).split(" ")));

    Map<String, Double> probs = WordScanner.unigramProbabilitiesOf(words);
    assertEquals(2.0 / words.size(), probs.get("I"), 0.001);
    assertEquals(1.0 / words.size(), probs.get("love"), 0.001);
  }

  @Test
  public void testSkipgramsOf() {
    List<String> words = new ArrayList<>(Arrays.asList(
        "Now they always say congratulations".split(" ")
    ));

    List<WordScanner.WordPair> skipgrams = WordScanner.skipgramFrequenciesOf(words, 1);
    assertEquals(8, skipgrams.size());
    assertTrue(skipgrams.contains(new WordScanner.WordPair("Now", "they")));
    assertTrue(skipgrams.contains(new WordScanner.WordPair("always", "they")));
    assertTrue(skipgrams.contains(new WordScanner.WordPair("always", "say")));
    assertTrue(skipgrams.contains(new WordScanner.WordPair("congratulations", "say")));
  }

  @Test
  public void testSkipgramProbabilitiesOf() {
    List<String> words = new ArrayList<>(Arrays.asList((
        "I've been on the low "
            + "I been taking my time "
            + "I feel like I'm out of my mind "
            + "It feel like my life ain't mine "
            + "Who can relate? Woo! "
            + "I've been on the low "
            + "I been taking my time "
            + "I feel like I'm out of my mind "
            + "It feel like my life ain't mine "
    ).split(" ")));
    Map<WordScanner.WordPair, Double> skipgramProbs = WordScanner.skipgramProbabilitiesOf(
        WordScanner.skipgramFrequenciesOf(words, 1));

    assertEquals(0.0754, skipgramProbs.get(new WordScanner.WordPair("feel", "like")), 0.001);
    assertEquals(0.0377, skipgramProbs.get(new WordScanner.WordPair("I'm", "out")), 0.001);
  }
}