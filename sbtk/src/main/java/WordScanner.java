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
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import opennlp.tools.tokenize.SimpleTokenizer;
import org.deeplearning4j.models.word2vec.Word2Vec;

@Slf4j
@RequiredArgsConstructor class WordScanner {

  private final ThreadLocalTagger tagger;
  private final Word2Vec vec;

  /**
   * Uses OpenNLP's SimpleTokenizer which breaks up words by whitespace and by punctuation. This
   * means that some of the elements returned are just ',' or '.' <p> This function is case
   * sensitive.
   *
   * @param corpus The string whose individual tokens you want to retrieve
   * @return A list of all of the words in the input, in the same order they came in
   */
  @VisibleForTesting
  private static List<String> wordsOf(String corpus) {
    return Arrays.stream(SimpleTokenizer.INSTANCE.tokenize(corpus))
        .filter(s1 -> !STOP_WORDS.contains(s1.toLowerCase()))
        .filter(s1 -> s1.length() != 1 || Character.isAlphabetic(s1.charAt(0)))
        .collect(Collectors.toList());
  }

  private final LoadingCache<WordPair, Integer> similarityCache =
      CacheBuilder.newBuilder()
          .expireAfterAccess(30, TimeUnit.SECONDS)
          .build(new CacheLoader<WordPair, Integer>() {
            @Override public Integer load(@Nonnull WordPair key) throws Exception {
              return Math.toIntExact(Math.round(vec.similarity(key.from, key.to)));
            }
          });

  @VisibleForTesting
  private Set<WordWithScore> mostSimilarTo(String word, Set<String> body) {
    MinMaxPriorityQueue<WordWithScore> topN = MinMaxPriorityQueue
        .maximumSize(4)
        .create();

    body.stream()
        .map(s -> {
          try {
            return new WordWithScore(s, similarityCache.get(new WordPair(word, s)));
          } catch (ExecutionException e) {
            e.printStackTrace();
          }
          return new WordWithScore(s, -1);
        })
        .filter(pair -> pair.score > 0)
        .forEach(topN::add);

    return new HashSet<>(topN);
  }

  private static Map<String, Integer> totalLinkagesOf(String corpus) {
    List<String> words =
        wordsOf(corpus).stream().map(String::toLowerCase).collect(Collectors.toList());
    List<WordPair> bigrams = new ArrayList<>();
    for (int i = 1; i < words.size(); ++i) {
      bigrams.add(new WordPair(words.get(i - 1), words.get(i)));
    }

    Map<String, Map<String, Integer>> bigramFreq = new HashMap<>();
    for (WordPair bigram : bigrams) {
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

  private static Map<String, Integer> wordFrequenciesOf(String corpus) {
    List<String> words =
        wordsOf(corpus).stream().map(String::toLowerCase).collect(Collectors.toList());
    Map<String, Integer> frequencies = new HashMap<>();
    words.forEach(w -> frequencies.put(w, frequencies.getOrDefault(w, 0) + 1));
    return frequencies;
  }

  @VisibleForTesting
  private Set<String> valuableTokensOf(String corpus) {
    Set<String> nouns = new HashSet<>();
    List<String> allWords = wordsOf(corpus);

    String[] tags = tagger.get().tag(allWords.toArray(new String[allWords.size()]));
    for (int i = 0; i < tags.length; ++i) {
      if (ALLOWED_POS_TAGS.contains(tags[i])) {
        nouns.add(allWords.get(i).toLowerCase());
      }
    }
    return nouns;
  }

  @SneakyThrows Set<String> buildGraph(String corpus) {
    // build graph
    ConcurrentMap<String, Set<WordWithScore>> adjacencyList =
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

    Map<String, Integer> frequencies = wordFrequenciesOf(corpus);
    Map<String, Integer> totals = totalLinkagesOf(corpus);

    adjacencyList.forEach((rootWord, setOfPairs) -> {
      if (totals.containsKey(rootWord)) {
        scores.put(rootWord,
            scores.getOrDefault(rootWord, uniques.size()) - totals.get(rootWord));
      }
      setOfPairs.forEach(
          pair -> scores.put(rootWord,
              scores.getOrDefault(rootWord, uniques.size()) + frequencies.get(pair.getWord())));
    });

    List<Integer> scoresOnly = new ArrayList<>();
    scores.forEach((word, score) -> scoresOnly.add(score));
    Collections.sort(scoresOnly);
    int minimumScore =
        scores.isEmpty() ? 0 : scoresOnly.get((int) Math.round(scoresOnly.size() * 0.95));

    MinMaxPriorityQueue<WordWithScore> topics = MinMaxPriorityQueue
        .maximumSize(5)
        .create();
    scores.forEach((word, score) -> topics.add(new WordWithScore(word, score)));

    Set<String> finalWords = new HashSet<>();
    scores.forEach((word, score) -> {
      if (score >= minimumScore) {
        finalWords.add(word);
      }
    });

    return finalWords;
  }

  @Data
  @RequiredArgsConstructor
  private static class WordWithScore implements Comparable<WordWithScore> {
    private final String word;
    private final Integer score;

    @Override public int compareTo(@Nonnull WordWithScore other) {
      return -this.score.compareTo(other.score);
    }
  }

  @Data
  @RequiredArgsConstructor
  private static final class WordPair {
    private final String from;
    private final String to;
  }


  @RequiredArgsConstructor
  private class PopulateSimilarN implements Runnable {
    private final ConcurrentMap<String, Set<WordWithScore>> adjacencyList;
    private final String rootWord;
    private final Set<String> wordSet;

    @Override public void run() {
      adjacencyList.put(rootWord, mostSimilarTo(rootWord, wordSet));
    }
  }

  private static final Set<String> ALLOWED_POS_TAGS = ImmutableSet.of(
      "NN", "NNP", "VB"//, "VBD", "VBN", "VBP", "VBZ"
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
