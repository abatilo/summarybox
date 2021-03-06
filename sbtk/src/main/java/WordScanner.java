import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.MinMaxPriorityQueue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;

import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import opennlp.tools.tokenize.SimpleTokenizer;

@Slf4j
@AllArgsConstructor class WordScanner {

  private final ThreadLocalDetector detector;
  private final ThreadLocalTagger tagger;
  private final Map<String, double[]> vec;
  private final Set<String> STOP_WORDS;
  private final Set<String> ALLOWED_POS_TAGS;

  private final LoadingCache<WordPair, Integer> similarityCache = CacheBuilder.newBuilder()
      .expireAfterAccess(10, TimeUnit.MINUTES)
      .build(new CacheLoader<WordPair, Integer>() {
        @Override public Integer load(@Nonnull WordPair key) throws Exception {
          double sim = Word2Vec.similarity(vec, key.getFrom(), key.getTo());
          return Math.toIntExact(Math.round(1000000 * sim));
        }
      });

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
        && vec.containsKey(word);
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
      topN.add(new WordWithScore(word, similarityCache.get(new WordPair(word, s))));
    }
    return new HashSet<>(topN);
  }

  private Map<String, Integer> wordFrequenciesOf(List<String> words) {
    final Map<String, Integer> frequencies = new HashMap<>(words.size());
    words.forEach(w -> frequencies.put(w, frequencies.getOrDefault(w, 0) + 1));
    return frequencies;
  }

  private Map<String, Integer> totalLinkagesOf(Map<String, Set<WordWithScore>> words) {
    final List<WordPair> bigrams = new ArrayList<>(words.size());
    words.forEach((from, to) -> to.forEach(t -> {
      bigrams.add(new WordPair(from, t.getWord()));
    }));

    final Map<String, Map<String, Integer>> bigramFreq = new HashMap<>(bigrams.size());
    for (WordPair bigram : bigrams) {
      if (bigramFreq.containsKey(bigram.getFrom())) {
        Map<String, Integer> firstLevel = bigramFreq.get(bigram.getFrom());
        firstLevel.put(bigram.getTo(), firstLevel.getOrDefault(bigram.getTo(), 0) + 1);
        bigramFreq.put(bigram.getFrom(), firstLevel);
      } else {
        Map<String, Integer> firstLevel = new HashMap<>();
        firstLevel.put(bigram.getTo(), 1);
        bigramFreq.put(bigram.getFrom(), firstLevel);
      }
    }

    final Map<String, Integer> totals = new HashMap<>();
    bigramFreq.forEach((fromWord, toCount) -> toCount.values()
        .stream()
        .reduce(Integer::sum)
        .ifPresent(i -> totals.put(fromWord, i)));
    return totals;
  }

  @SneakyThrows
  Set<String> textRank(String corpus, Integer similar, Double percentile, Integer topics) {
    final List<String> words = normalizedTokensOf(corpus);
    final Set<String> uniques = new HashSet<>(words);
    final Map<String, Set<WordWithScore>> adjacencyList =
        new HashMap<>(uniques.size());

    long start = System.currentTimeMillis();
    for (String unique : uniques) {
      adjacencyList.put(unique, mostSimilarTo(unique, uniques, similar));
    }
    System.out.println(System.currentTimeMillis() - start);

    // walk the graph
    final Map<String, Integer> scores = new HashMap<>(adjacencyList.keySet().size());
    final Map<String, Integer> frequencies = wordFrequenciesOf(words);
    final Map<String, Integer> totals = totalLinkagesOf(adjacencyList);

    adjacencyList.forEach((rootWord, setOfPairs) -> {
      scores.put(rootWord,
          scores.getOrDefault(rootWord, uniques.size()) - totals.get(rootWord));
      setOfPairs.forEach(
          pair -> scores.put(rootWord,
              scores.getOrDefault(rootWord, uniques.size()) + frequencies.get(pair.getWord())));
    });

    final List<Integer> scoresOnly = new ArrayList<>();
    scores.forEach((word, score) -> scoresOnly.add(score));
    Collections.sort(scoresOnly);
    int minimumScore = scores.isEmpty() ? 0
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
}
