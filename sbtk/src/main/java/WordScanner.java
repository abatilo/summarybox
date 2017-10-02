import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.MinMaxPriorityQueue;
import com.google.common.io.Resources;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSTagger;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.tokenize.WhitespaceTokenizer;
import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
import org.deeplearning4j.models.word2vec.Word2Vec;

@Slf4j
public class WordScanner {
  //    private static final String filePath = Resources.getResource("GoogleNews-vectors-negative300.bin.gz").getPath();
  //    private static final String filePath = Resources.getResource("text8-50dimens-1iters.bin").getPath();
  private static final String filePath = Resources.getResource("news.bin").getPath();
  //    private static final String filePath = Resources.getResource("text8-200dimens-15iters.bin").getPath();
  //private static final String filePath =
  //    Resources.getResource("text8-10dimen-1iters.bin").getPath();
  private static final Word2Vec vec = WordVectorSerializer.readWord2VecModel(filePath);
  private static SentenceDetectorME detector;
  private static POSTagger tagger;

  // Statically loads the sentence boundary detector model that comes standard
  // with OpenNLP
  static {
    final InputStream modelStream = WordScanner.class.getResourceAsStream("en-sent.bin");

    // Using the perceptron model here will shave off about 4 milliseconds per tag call
    final InputStream posStream = WordScanner.class.getResourceAsStream("en-pos-perceptron.bin");

    try {
      final SentenceModel model = new SentenceModel(modelStream);
      detector = new SentenceDetectorME(model);

      final POSModel posModel = new POSModel(posStream);
      tagger = new POSTaggerME(posModel);
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
    return Arrays.stream(WhitespaceTokenizer.INSTANCE.tokenize(s))
        .map(t -> {
          if (t.endsWith(".") || t.endsWith(",")) return t.substring(0, t.length() - 1);
          return t;
        })
        .filter(s1 -> !STOP_WORDS.contains(s1.toLowerCase()))
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
    List<String> noStopWords = new ArrayList<>();
    words.stream()
        .filter(s1 -> !STOP_WORDS.contains(s1.toLowerCase()))
        .forEach(noStopWords::add);
    SortedSet<String> uniqueWords = new TreeSet<>(noStopWords);
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

  static Set<String> unorderedVocabOf(String corpus) {
    return new HashSet<>(
        wordsOf(corpus).stream()
            .map(String::toLowerCase)
            .filter(word -> !STOP_WORDS.contains(word))
            .collect(Collectors.toList())
    );
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
    words.stream()
        .filter(s1 -> !STOP_WORDS.contains(s1.toLowerCase()))
        .forEach(s -> map.put(s, map.getOrDefault(s, 0) + 1));
    return new ArrayList<>(map.values());
  }

  /**
   * give it all of your words as a single String and we'll tokenize it for you. This function is
   * the same as {@link #vectorOf(List)} with the difference being that you can
   *
   * @param words The corpus of text to operate on
   * @return A list of integers, representing the frequencies of the unique words in the list
   */
  @VisibleForTesting
  static List<Integer> vectorOf(String words) {
    return vectorOf(wordsOf(words));
  }

  @Data
  @RequiredArgsConstructor
  private static final class WordPair {
    private final String from;
    private final String to;
  }

  private static final LoadingCache<WordPair, Double> similarityCache =
      CacheBuilder.newBuilder()
          .expireAfterAccess(1, TimeUnit.MINUTES)
          .build(new CacheLoader<WordPair, Double>() {
        @Override public Double load(WordPair key) throws Exception {
          return vec.similarity(key.from, key.to);
        }
      });

  @VisibleForTesting
  static Set<VectorDistancePair> mostSimilarToN(String word, Set<String> body, int n) {
    MinMaxPriorityQueue<VectorDistancePair> topN = MinMaxPriorityQueue
        .maximumSize(n)
        .create();

    body.stream()
        //.filter(s -> !word.equals(s))
        //.filter(s -> !STOP_WORDS.contains(s.toLowerCase()))
        //.map(s -> new VectorDistancePair(s, vec.similarity(word, s)))
        .map(s -> {
          try {
            return new VectorDistancePair(s, similarityCache.get(new WordPair(word, s)));
          } catch (ExecutionException e) {
            e.printStackTrace();
          }
          return new VectorDistancePair(s, Double.NaN);
        })
        .filter(pair -> !Double.isNaN(pair.distance))
        .forEach(topN::add);

    return new HashSet<>(topN);
  }

  @VisibleForTesting
  static Map<String, Map<String, Double>> transitionProbabilitiesOf(String corpus) {
    List<String> words =
        wordsOf(corpus).stream().map(String::toLowerCase).collect(Collectors.toList());
    List<Bigram> bigrams = new ArrayList<>();
    for (int i = 1; i < words.size(); ++i) {
      bigrams.add(new Bigram(words.get(i - 1), words.get(i)));
    }

    Map<String, Map<String, Integer>> bigramFreq = new HashMap<>();
    for (Bigram bigram : bigrams) {
      if (bigramFreq.containsKey(bigram.from)) {
        Map<String, Integer> firstLevel = bigramFreq.get(bigram.from);
        firstLevel.put(bigram.to, firstLevel.getOrDefault(bigram.to, 0) + 1);
        bigramFreq.put(bigram.from, firstLevel);
      } else {
        Map<String, Integer> firstLevel = new HashMap<>();
        firstLevel.put(bigram.to, 1);
        bigramFreq.put(bigram.from, firstLevel);
      }
    }

    Map<String, Integer> totals = new HashMap<>();
    bigramFreq.forEach((fromWord, toCount) -> {
      toCount.values()
          .stream()
          .reduce(Integer::sum)
          .ifPresent(i -> totals.put(fromWord, i));
    });

    Map<String, Map<String, Double>> bigramNormalized = new HashMap<>();
    bigramFreq.forEach((from, toFreq) -> {
      toFreq.forEach((to, freq) -> {
        if (bigramNormalized.containsKey(from)) {
          Map<String, Double> normalized = bigramNormalized.get(from);
          normalized.put(to, freq / (double) totals.get(from));
          bigramNormalized.put(from, normalized);
        } else {
          Map<String, Double> normalized = new HashMap<>();
          normalized.put(to, freq / (double) totals.get(from));
          bigramNormalized.put(from, normalized);
        }
      });
    });

    //bigramNormalized.forEach((s, stringDoubleMap) -> {
    //  System.out.println(s + " -- " + stringDoubleMap);
    //});
    return bigramNormalized;
  }

  static Map<String, Integer> totalLinkagesOf(String corpus) {
    List<String> words =
        wordsOf(corpus).stream().map(String::toLowerCase).collect(Collectors.toList());
    List<Bigram> bigrams = new ArrayList<>();
    for (int i = 1; i < words.size(); ++i) {
      bigrams.add(new Bigram(words.get(i - 1), words.get(i)));
    }

    Map<String, Map<String, Integer>> bigramFreq = new HashMap<>();
    for (Bigram bigram : bigrams) {
      if (bigramFreq.containsKey(bigram.from)) {
        Map<String, Integer> firstLevel = bigramFreq.get(bigram.from);
        firstLevel.put(bigram.to, firstLevel.getOrDefault(bigram.to, 0) + 1);
        bigramFreq.put(bigram.from, firstLevel);
      } else {
        Map<String, Integer> firstLevel = new HashMap<>();
        firstLevel.put(bigram.to, 1);
        bigramFreq.put(bigram.from, firstLevel);
      }
    }

    Map<String, Integer> totals = new HashMap<>();
    bigramFreq.forEach((fromWord, toCount) -> toCount.values()
        .stream()
        .reduce(Integer::sum)
        .ifPresent(i -> totals.put(fromWord, i)));
    return totals;
  }

  static Map<String, Integer> wordFrequenciesOf(String corpus) {
    List<String> words =
        wordsOf(corpus).stream().map(String::toLowerCase).collect(Collectors.toList());
    Map<String, Integer> frequencies = new HashMap<>();
    words.forEach(w -> {
      frequencies.put(w, frequencies.getOrDefault(w, 0) + 1);
    });
    return frequencies;
  }

  @VisibleForTesting
  static Set<String> valuableTokensOf(String corpus) {
    Set<String> nouns = new HashSet<>();
    List<String> allWords = wordsOf(corpus);

    String[] tags = tagger.tag(allWords.toArray(new String[allWords.size()]));

    for (int i = 0; i < tags.length; ++i) {
      if (ALLOWED_POS_TAGS.contains(tags[i])) {
        nouns.add(allWords.get(i).toLowerCase());
      }
    }
    return nouns;
  }

  @Data
  @AllArgsConstructor
  public static class VectorDistancePair implements Comparable<VectorDistancePair> {
    private final String word;
    private final Double distance;

    @Override
    public int compareTo(VectorDistancePair other) {
      return -this.distance.compareTo(other.distance);
    }
  }

  @Data
  @RequiredArgsConstructor
  private static class Bigram {
    private final String from;
    private final String to;
  }

  @Data
  @RequiredArgsConstructor
  private static class TaggedWord {
    private final String word;
    private final String tag;
  }

  private static final Set<String> ALLOWED_POS_TAGS = ImmutableSet.of(
      "NN", "NNS", "NNP", "NNPS"//, "VB"//, "VBD", "VBN", "VBP", "VBZ"
  );

  private static final Set<String> STOP_WORDS = ImmutableSet.of(
      "i", "me", "my", "myself", "we", "our", "ours", "ourselves", "you", "your", "yours",
      "yourself", "yourselves", "he", "him", "his", "himself", "she", "her", "hers", "herself",
      "it", "its", "itself", "they", "them", "their", "theirs", "themselves", "what", "which",
      "who", "whom", "this", "that", "these", "those", "am", "is", "are", "was", "were", "be",
      "been", "being", "have", "has", "had", "having", "do", "does", "did", "doing", "a", "an",
      "the", "and", "but", "if", "or", "because", "as", "until", "while", "of", "at", "by", "for",
      "with", "about", "against", "between", "into", "through", "during", "before", "after",
      "above", "below", "to", "from", "up", "down", "in", "out", "on", "off", "over", "under",
      "again", "further", "then", "once", "here", "there", "when", "where", "why", "how", "all",
      "any", "both", "each", "few", "more", "most", "other", "some", "such", "no", "nor", "not",
      "only", "own", "same", "so", "than", "too", "very", "s", "t", "can", "will", "just", "don",
      "should", "now"
  );
}
