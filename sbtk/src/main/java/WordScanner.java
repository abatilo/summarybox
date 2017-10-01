import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.MinMaxPriorityQueue;
import com.google.common.io.Resources;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.tokenize.SimpleTokenizer;
import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
import org.deeplearning4j.models.word2vec.Word2Vec;

@Slf4j
public class WordScanner {
  //    private static final String filePath = Resources.getResource("GoogleNews-vectors-negative300.bin.gz").getPath();
  //    private static final String filePath = Resources.getResource("text8-50dimens-1iters.bin").getPath();
  //    private static final String filePath = Resources.getResource("text8-200dimens-15iters.bin").getPath();
  private static final String filePath =
      Resources.getResource("text8-10dimen-1iters.bin").getPath();
  private static final Word2Vec vec = WordVectorSerializer.readWord2VecModel(filePath);
  private static SentenceDetectorME detector;

  // Statically loads the sentence boundary detector model that comes standard
  // with OpenNLP
  static {
    final InputStream modelStream = WordScanner.class.getResourceAsStream("en-sent.bin");
    try {
      final SentenceModel model = new SentenceModel(modelStream);
      detector = new SentenceDetectorME(model);
    } catch (IOException e) {
      log.error("We couldn't open the sentence detector model", e);
    } finally {
      if (modelStream != null) {
        try {
          modelStream.close();
        } catch (IOException e) {
          log.error("We couldn't close the file stream to the sentence detector model", e);
        }
      }
    }
  }

  /**
   * Uses OpenNLP's SimpleTokenizer which breaks up words by whitespace and by punctuation. This
   * means that some of the elements returned are just ',' or '.' <p> This function is case
   * sensitive.
   *
   * @param s The string whose individual tokens you want to retrieve
   * @return A list of all of the words in the input, in the same order they came in
   */
  @VisibleForTesting
  static List<String> wordsOf(String s) {
    return Arrays.stream(SimpleTokenizer.INSTANCE.tokenize(s))
        .filter(s1 -> s1.length() != 1 || Character.isAlphabetic(s1.charAt(0)))
        .collect(Collectors.toList());
  }

  /**
   * Uses OpenNLP's SentenceDetector with the included max entropy model.
   *
   * @param s The sentences as a single string that you want to break up
   * @return A list of Strings, each string being a different sentence
   */
  @VisibleForTesting
  static List<String> sentencesOf(String s) {
    return Arrays.stream(detector.sentDetect(s)).collect(Collectors.toList());
  }

  /**
   * Finds all the unique words in a list of strings, and returns them in sorted order. This is
   * primarily used for creating vectors out of a list of words
   *
   * This function is case sensitive.
   *
   * @param words The list of words to operate on
   * @return Returns the words in a sorted list
   */
  @VisibleForTesting
  static List<String> vocabOf(List<String> words) {
    SortedSet<String> uniqueWords = new TreeSet<>(words);
    return new ArrayList<>(uniqueWords);
  }

  /**
   * This function is the same as {@link #vocabOf(List)} with the difference being that you can give
   * it all of your words as a single String and we'll tokenize it for you.
   *
   * @param words The corpus of text to operate on
   * @return Returns the words in a sorted list
   */
  @VisibleForTesting
  static List<String> vocabOf(String words) {
    return vocabOf(wordsOf(words));
  }

  /**
   * Returns a frequency vector for the list of words that were passed in. The returned list is only
   * a list of integers. If you want to see the words that go with them, you'll have to also make a
   * call to {@link #vocabOf(List)} or {@link #vocabOf(String)}
   *
   * @param words The list of words to operate on
   * @return A list of integers, representing the frequencies of the unique words in the list
   */
  @VisibleForTesting
  static List<Integer> vectorOf(List<String> words) {
    Map<String, Integer> map = new TreeMap<>();
    for (String s : words) {
      map.put(s, map.getOrDefault(s, 0) + 1);
    }
    return new ArrayList<>(map.values());
  }

  /**
   * give it all of your words as a single String and we'll tokenize it for you.
   * This function is the same as {@link #vectorOf(List)} with the difference being that you can
   *
   * @param words The corpus of text to operate on
   * @return A list of integers, representing the frequencies of the unique words in the list
   */
  @VisibleForTesting
  static List<Integer> vectorOf(String words) {
    return vectorOf(wordsOf(words));
  }

  @VisibleForTesting
  public static List<String> mostSimilarToN(String word, String body, int n) {
    List<String> mostSimilar = new ArrayList<>();
    List<String> uniques = WordScanner.vocabOf(body);
    MinMaxPriorityQueue<VectorDistancePair> topFive = MinMaxPriorityQueue
        .maximumSize(n)
        .create();

    uniques.stream()
        .filter(s -> !word.equals(s))
        .map(s -> new VectorDistancePair(s, vec.similarity(word, s)))
        .filter(pair -> !Double.isNaN(pair.distance))
        .forEach(topFive::add);

    topFive.forEach(pair -> mostSimilar.add(pair.word));
    return mostSimilar;
  }

  @AllArgsConstructor
  private static class VectorDistancePair implements Comparable<VectorDistancePair> {
    private final String word;
    private final Double distance;

    @Override
    public int compareTo(VectorDistancePair other) {
      return -this.distance.compareTo(other.distance);
    }
  }
}
