import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
import com.google.common.collect.MinMaxPriorityQueue;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.annotation.Nonnull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import opennlp.tools.tokenize.SimpleTokenizer;
import org.deeplearning4j.models.word2vec.Word2Vec;

@Slf4j
@AllArgsConstructor class WordScanner {

  private final ThreadLocalDetector detector;
  private final ThreadLocalTagger tagger;
  private final Word2Vec vec;
  private final Set<String> STOP_WORDS;
  private final Set<String> ALLOWED_POS_TAGS;

  // Based on:
  // https://stackoverflow.com/questions/14062030/removing-contractions
  private static String expandContractions(String inputString) {
    return inputString
        .replace('’', '\'')
        .replace("'s", " is") // We don't care about possessive
        .replace("can't", "cannot")
        .replace("won't", "will not")
        .replace("n't", " not")
        .replace("'re", " are")
        .replace("'m", " am")
        .replace("'ll", " will")
        .replace("'ve", " have");
  }

  private boolean allowedWord(String word, String tag) {
    return word.length() > 2
        && ALLOWED_POS_TAGS.contains(tag)
        && !STOP_WORDS.contains(word)
        && vec.hasWord(word);
  }

  private List<String> normalizedTokensOf(String corpus) {
    final String expandedCorpus = expandContractions(corpus);
    final String removedPunctuation = expandedCorpus.replaceAll("[^a-zA-Z. ]", "");

    final String[] sentences = detector.get().sentDetect(removedPunctuation);

    final List<String> normalized = new ArrayList<>();
    for (String sentence : sentences) {
      final String[] allWords = SimpleTokenizer.INSTANCE.tokenize(sentence);
      final String[] tags = tagger.get().tag(allWords);
      for (int i = 0; i < tags.length; ++i) {
        final String word = allWords[i].toLowerCase();
        final String tag = tags[i];
        if (allowedWord(word, tag)) {
          normalized.add(word);
        }
      }
    }
    return normalized;
  }

  private Set<WordWithScore> mostSimilarTo(String word, Set<String> body, int n)
      throws ExecutionException {
    final MinMaxPriorityQueue<WordWithScore> topN = MinMaxPriorityQueue
        .maximumSize(n)
        .create();
    for (String s : body) {
      double sim = vec.similarity(word, s);
      long score = Math.round(1000000 * sim);
      topN.add(new WordWithScore(word, score));
    }
    return new HashSet<>(topN);
  }

  private static Map<String, Long> wordFrequenciesOf(List<String> words) {
    return words.stream().collect(Collectors.groupingBy(s -> s, Collectors.counting()));
  }

  @VisibleForTesting
  static Map<String, Double> unigramProbabilitiesOf(List<String> words) {
    Map<String, Long> frequencies = wordFrequenciesOf(words);
    long totalNumberOfWords =
        frequencies.values().stream().mapToLong(Long::longValue).sum();
    return frequencies.entrySet()
        .stream()
        .map(e -> Maps.immutableEntry(e.getKey(), (double) e.getValue() / totalNumberOfWords))
        .parallel()
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  private static WordPair sortedPair(String s1, String s2) {
    return (s1.compareTo(s2) <= 0) ? new WordPair(s1, s2) : new WordPair(s2, s1);
  }

  @VisibleForTesting
  static Map<WordPair, Long> skipgramFrequenciesOf(List<String> words, int windowSize) {
    return IntStream.range(0, words.size())
        .boxed()
        .map(i -> {
          List<WordPair> batch = new ArrayList<>();
          final String currentWord = words.get(i);
          IntStream.range(0, windowSize)
              .forEach(j -> {
                int preOffset = i - windowSize + j;
                int postOffset = i + windowSize - j;

                if (preOffset >= 0) {
                  batch.add(sortedPair(currentWord, words.get(preOffset)));
                }

                if (postOffset < words.size()) {
                  batch.add(sortedPair(currentWord, words.get(postOffset)));
                }
              });
          return batch;
        })
        .flatMap(Collection::stream)
        .parallel()
        .collect(Collectors.groupingByConcurrent(wp -> wp, Collectors.counting()));
  }

  @VisibleForTesting
  static Map<WordPair, Double> skipgramProbabilitiesOf(List<String> corpus, int windowSize) {
    Map<WordPair, Long> frequencies = skipgramFrequenciesOf(corpus, windowSize);
    long totalNumberOfSkipgrams =
        frequencies.values().stream().mapToLong(Long::longValue).sum();
    return frequencies.entrySet()
        .stream()
        .map(e -> Maps.immutableEntry(e.getKey(), (double) e.getValue() / totalNumberOfSkipgrams))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  static double pointwiseMutualInformationOf(List<String> corpus, String s1, String s2) {
    Map<String, Double> unigramProbs = unigramProbabilitiesOf(corpus);
    Map<WordPair, Double> skipgramProbs = skipgramProbabilitiesOf(corpus, 1);
    return Math.log(
        skipgramProbs.getOrDefault(sortedPair(s1, s2), 0.0)
            / unigramProbs.getOrDefault(s1, 1.0)
            / unigramProbs.getOrDefault(s2, 1.0));
  }

  private Map<String, Long> totalLinkagesOf(Map<String, Set<WordWithScore>> words) {
    final List<WordPair> bigrams = new ArrayList<>(words.size());
    words.forEach((from, to) -> to.forEach(t -> {
      bigrams.add(new WordPair(from, t.word));
    }));

    final Map<String, Map<String, Long>> bigramFreq = new HashMap<>(bigrams.size());
    for (WordPair bigram : bigrams) {
      if (bigramFreq.containsKey(bigram.left)) {
        Map<String, Long> firstLevel = bigramFreq.get(bigram.left);
        firstLevel.put(bigram.right, firstLevel.getOrDefault(bigram.right, 0L) + 1);
        bigramFreq.put(bigram.left, firstLevel);
      } else {
        Map<String, Long> firstLevel = new HashMap<>();
        firstLevel.put(bigram.right, 1L);
        bigramFreq.put(bigram.left, firstLevel);
      }
    }

    final Map<String, Long> totals = new HashMap<>();
    bigramFreq.forEach((fromWord, toCount) -> toCount.values()
        .stream()
        .reduce(Long::sum)
        .ifPresent(i -> totals.put(fromWord, i)));
    return totals;
  }

  @SneakyThrows
  Set<String> textRank(String corpus, Integer similar, Double percentile, Integer topics) {
    final List<String> words = normalizedTokensOf(corpus);
    final Set<String> uniques = new HashSet<>(words);
    final Map<String, Set<WordWithScore>> adjacencyList =
        new HashMap<>(uniques.size());

    for (String unique : uniques) {
      adjacencyList.put(unique, mostSimilarTo(unique, uniques, similar));
    }

    // walk the graph
    final Map<String, Long> scores = new HashMap<>(adjacencyList.keySet().size());
    final Map<String, Long> frequencies = wordFrequenciesOf(words);
    final Map<String, Long> totals = totalLinkagesOf(adjacencyList);

    adjacencyList.forEach((rootWord, setOfPairs) -> {
      scores.put(rootWord,
          scores.getOrDefault(rootWord, (long) uniques.size()) - totals.get(rootWord));
      setOfPairs.forEach(
          pair -> scores.put(rootWord,
              scores.getOrDefault(rootWord, (long) uniques.size()) + frequencies.get(
                  pair.getWord())));
    });

    final List<Long> scoresOnly = new ArrayList<>();
    scores.forEach((word, score) -> scoresOnly.add(score));
    Collections.sort(scoresOnly);
    long minimumScore = scores.isEmpty() ? 0
        : scoresOnly.get((int) Math.floor(scoresOnly.size() * percentile));

    final MinMaxPriorityQueue<WordWithScore> t = MinMaxPriorityQueue
        .maximumSize(topics)
        .create();
    scores.forEach((word, score) -> t.add(new WordWithScore(word, score)));

    final Set<String> finalWords = new HashSet<>();
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
    private final Long score;

    @Override public int compareTo(@Nonnull WordWithScore other) {
      return -this.score.compareTo(other.score);
    }
  }

  @Data
  @RequiredArgsConstructor
  @VisibleForTesting
  static final class WordPair {
    private final String left;
    private final String right;
  }
}
