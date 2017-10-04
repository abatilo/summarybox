import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.MinMaxPriorityQueue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import opennlp.tools.postag.POSTagger;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.tokenize.SimpleTokenizer;
import org.deeplearning4j.models.word2vec.Word2Vec;

@Slf4j
@RequiredArgsConstructor
public class WordScanner {

  private final SentenceDetectorME detector;
  private final POSTagger tagger;
  private final Word2Vec vec;

  private final Object taggerLock = new Object();

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
  public List<String> sentencesOf(String s) {
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
  private List<String> vocabOf(List<String> words) {
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
  private List<String> vocabOf(String words) {
    return vocabOf(wordsOf(words));
  }

  private Set<String> unorderedVocabOf(String corpus) {
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
  public List<Integer> vectorOf(List<String> words) {
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
  private List<Integer> vectorOf(String words) {
    return vectorOf(wordsOf(words));
  }

  @Data
  @RequiredArgsConstructor
  private static final class WordPair {
    private final String from;
    private final String to;
  }

  private final LoadingCache<WordPair, Double> similarityCache =
      CacheBuilder.newBuilder()
          .maximumSize(1000)
          .expireAfterAccess(2, TimeUnit.SECONDS)
          .build(new CacheLoader<WordPair, Double>() {
            @Override public Double load(WordPair key) throws Exception {
              return vec.similarity(key.from, key.to);
            }
          });

  @VisibleForTesting
  private Set<VectorDistancePair> mostSimilarToN(String word, Set<String> body, int n) {
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
  private Set<String> valuableTokensOf(String corpus) {
    Set<String> nouns = new HashSet<>();
    List<String> allWords = wordsOf(corpus);

    String[] asArray = new String[allWords.size()];
    for (int i = 0; i < asArray.length; ++i) {
      asArray[i] = allWords.get(i);
    }
    if (allWords == null) {
      System.out.println("allWords was null");
      return ImmutableSet.of();
    }
    if (asArray == null) {
      System.out.println("asArray was null");
      return ImmutableSet.of();
    }
    if (tagger == null) {
      System.out.println("Tagger was null");
      return ImmutableSet.of();
    }
    String[] tags;
    synchronized (taggerLock) {
      tags = tagger.tag(asArray);
    }
    for (int i = 0; i < tags.length; ++i) {
      if (ALLOWED_POS_TAGS.contains(tags[i])) {
        nouns.add(allWords.get(i).toLowerCase());
      }
    }
    return nouns;
  }

  @SneakyThrows
  public Set<String> buildGraph(String corpus) {
    // build graph
    ConcurrentMap<String, Set<WordScanner.VectorDistancePair>> adjacencyList =
        new ConcurrentHashMap<>();
    Set<String> uniques = valuableTokensOf(corpus);

    ExecutorService service =
        Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() + 1);
    for (String unique : uniques) {
      service.submit(new PopulateSimilarN(adjacencyList, unique, uniques));
    }
    service.shutdown();
    service.awaitTermination(1, TimeUnit.MINUTES);

    // walk the graph
    Map<String, Integer> scores = new TreeMap<>();

    Map<String, Integer> frequencies = WordScanner.wordFrequenciesOf(corpus);
    Map<String, Integer> totals = WordScanner.totalLinkagesOf(corpus);

    adjacencyList.forEach((rootWord, setOfPairs) -> {
      if (totals.containsKey(rootWord)) {
        scores.put(rootWord,
            scores.getOrDefault(rootWord, uniques.size()) - totals.get(rootWord));
      } else {
        // Normalize the cleaning/munging of all of the words
      }
      setOfPairs.forEach(
          pair -> {
            scores.put(rootWord,
                scores.getOrDefault(rootWord, uniques.size()) + frequencies.get(pair.getWord()));
          });
    });

    List<Integer> scoresOnly = new ArrayList<>();
    scores.forEach((word, score) -> scoresOnly.add(score));
    Collections.sort(scoresOnly);
    int nintiethPercentile =
        scores.isEmpty() ? 0 : scoresOnly.get((int) Math.round(scoresOnly.size() * 0.95));

    MinMaxPriorityQueue<ScoredWords> topics = MinMaxPriorityQueue
        .maximumSize(5)
        .create();
    scores.forEach((word, score) -> topics.add(new ScoredWords(word, score)));

    Set<String> finalWords = new HashSet<>();
    scores.forEach((word, score) -> {
      if (score >= nintiethPercentile) {
        finalWords.add(word);
      }
    });

    return finalWords;
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

  @RequiredArgsConstructor
  private class PopulateSimilarN implements Runnable {
    private final ConcurrentMap<String, Set<VectorDistancePair>> adjacencyList;
    private final String rootWord;
    private final Set<String> wordSet;

    @Override public void run() {
      adjacencyList.put(rootWord, mostSimilarToN(rootWord, wordSet, 4));
    }
  }

  @Data
  @RequiredArgsConstructor
  private static class ScoredWords implements Comparable<ScoredWords> {
    private final String word;
    private final Integer score;

    @Override public int compareTo(@Nonnull ScoredWords other) {
      return -this.score.compareTo(other.score);
    }
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
