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
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import opennlp.tools.stemmer.PorterStemmer;
import opennlp.tools.tokenize.SimpleTokenizer;
import org.deeplearning4j.models.word2vec.Word2Vec;

@Slf4j
@RequiredArgsConstructor class WordScanner {

  private final ThreadLocalDetector detector;
  private final ThreadLocalTagger tagger;
  private final Word2Vec vec;
  private final Set<String> STOP_WORDS;

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

  private List<String> normalizedTokensOf(String corpus)
      throws InterruptedException {
    final String expandedCorpus = expandContractions(corpus);
    final String removedPuncutation = expandedCorpus.replaceAll("[^a-zA-Z. ]", "");

    final String[] sentences = detector.get().sentDetect(removedPuncutation);
    final PorterStemmer stemmer = new PorterStemmer();

    final List<String> normalized = new ArrayList<>();
    for (String sentence : sentences) {
      final List<String> allWords =
          Arrays.stream(SimpleTokenizer.INSTANCE.tokenize(sentence)).collect(Collectors.toList());
      final String[] tags = tagger.get().tag(allWords.toArray(new String[allWords.size()]));
      for (int i = 0; i < tags.length; ++i) {
        final String word = allWords.get(i).toLowerCase();
        if (word.length() > 2 && ALLOWED_POS_TAGS.contains(tags[i]) && !STOP_WORDS.contains(word)) {
          normalized.add(stemmer.stem(word));
        }
      }
    }
    return normalized;
  }

  private static int calculateSimilarity(Word2Vec vec, String from, String to) {
    double sim = vec.similarity(from, to);
    // We multiply by 1 000 000 here so that we can keep some decimal information
    return Math.toIntExact(Math.round(1000000 * sim));
  }

  private Set<WordWithScore> mostSimilarTo(String word, Set<String> body) {
    final MinMaxPriorityQueue<WordWithScore> topN = MinMaxPriorityQueue
        .maximumSize(4)
        .create();

    body.stream()
        .map(s -> new WordWithScore(word, calculateSimilarity(vec, word, s)))
        .filter(pair -> pair.score > 0)
        .forEach(topN::add);

    return new HashSet<>(topN);
  }

  private Map<String, Integer> totalLinkagesOf(List<String> words) {
    final List<WordPair> bigrams = new ArrayList<>(words.size());
    for (int i = 1; i < words.size(); ++i) {
      bigrams.add(new WordPair(words.get(i - 1), words.get(i)));
    }

    final Map<String, Map<String, Integer>> bigramFreq = new HashMap<>(bigrams.size());
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

    final Map<String, Integer> totals = new HashMap<>();
    bigramFreq.forEach((fromWord, toCount) -> toCount.values()
        .stream()
        .reduce(Integer::sum)
        .ifPresent(i -> totals.put(fromWord, i)));
    return totals;
  }

  private Map<String, Integer> wordFrequenciesOf(List<String> words) {
    final Map<String, Integer> frequencies = new HashMap<>(words.size());
    words.forEach(w -> frequencies.put(w, frequencies.getOrDefault(w, 0) + 1));
    return frequencies;
  }

  @SneakyThrows Set<String> textRank(String corpus) {
    final List<String> words = normalizedTokensOf(corpus);
    final Set<String> uniques = new HashSet<>(words);
    final Map<String, Set<WordWithScore>> adjacencyList =
        new HashMap<>(uniques.size());

    for (String unique : uniques) {
      adjacencyList.put(unique, mostSimilarTo(unique, uniques));
    }

    // walk the graph
    final Map<String, Integer> scores = new HashMap<>(adjacencyList.keySet().size());
    final Map<String, Integer> frequencies = wordFrequenciesOf(words);
    final Map<String, Integer> totals = totalLinkagesOf(words);

    adjacencyList.forEach((rootWord, setOfPairs) -> {
      if (totals.containsKey(rootWord)) {
        scores.put(rootWord,
            scores.getOrDefault(rootWord, uniques.size()) - totals.get(rootWord));
      }
      setOfPairs.forEach(
          pair -> scores.put(rootWord,
              scores.getOrDefault(rootWord, uniques.size()) + frequencies.get(pair.getWord())));
    });

    final List<Integer> scoresOnly = new ArrayList<>();
    scores.forEach((word, score) -> scoresOnly.add(score));
    Collections.sort(scoresOnly);
    int minimumScore =
        scores.isEmpty() ? 0 : scoresOnly.get((int) Math.floor(scoresOnly.size() * 0.97));

    final MinMaxPriorityQueue<WordWithScore> topics = MinMaxPriorityQueue
        .maximumSize(5)
        .create();
    scores.forEach((word, score) -> topics.add(new WordWithScore(word, score)));

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

  private static final Set<String> ALLOWED_POS_TAGS = ImmutableSet.of(
      "NN", "NNS", "NNP", "NNPS", "VB", "VBS"
  );
}
