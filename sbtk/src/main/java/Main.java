import com.google.common.io.Resources;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.stream.Collectors;
import opennlp.tools.sentdetect.SentenceModel;
import org.apache.commons.lang.StringUtils;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.cpu.nativecpu.blas.CpuLapack;
import org.nd4j.linalg.factory.Nd4j;

public class Main {
  public static void main(String[] args) throws IOException {
    final InputStream sentenceStream =
        Resources.getResource("en-sent.bin").openStream();
    final SentenceModel sentenceModel = new SentenceModel(sentenceStream);
    final ThreadLocalDetector detector = new ThreadLocalDetector(sentenceModel);

    List<String> corpus = new ArrayList<>();
    {
      long start = System.currentTimeMillis();
      System.out.println("Reading in file");
      Scanner scanner = new Scanner(Resources.getResource("corpus.txt").openStream());
      StringBuilder buffer = new StringBuilder();
      while (scanner.hasNext()) {
        buffer.append(scanner.next());
        buffer.append(" ");
        String[] sentences = detector.get().sentDetect(buffer.toString());

        if (sentences.length > 1) {
          corpus.add(sentences[0]);
          // Flush
          buffer.delete(0, sentences[0].length() + 1);
        }
      }
      corpus.add(buffer.toString());
      System.out.println("That took: " + (System.currentTimeMillis() - start));
    }
    long start = System.currentTimeMillis();
    System.out.println("Counting unigrams");
    Map<String, Double> unigramProbs = WordScanner.unigramProbabilitiesOf(corpus);
    System.out.println("That took: " + (System.currentTimeMillis() - start));
    start = System.currentTimeMillis();
    System.out.println("Calculating skipgram probs");
    Map<WordScanner.WordPair, Double> skipgramProbs =
        WordScanner.skipgramProbabilitiesOf(corpus, 2);
    System.out.println("That took: " + (System.currentTimeMillis() - start));

    System.out.println("Getting frequent uniques");
    long startUniques = System.currentTimeMillis();
    List<String> uniques = corpus.stream()
        .flatMap(s -> Arrays.stream(s.split(" ")))
        .filter(StringUtils::isAlphanumeric)
        .filter(s -> !Character.isDigit(s.charAt(0)))
        .collect(Collectors.groupingBy(w -> w, Collectors.counting()))
        .entrySet().stream()
        .filter(entry -> entry.getValue() > 1000)
        .map(Map.Entry::getKey)
        .sorted()
        .collect(Collectors.toList());
    System.out.println("That took: " + (System.currentTimeMillis() - startUniques));

    System.out.println("Building matrix");
    long startMatrix = System.currentTimeMillis();
    INDArray m = Nd4j.create(uniques.size(), uniques.size());
    for (int i = 0; i < uniques.size(); ++i) {
      for (int j = i; j < uniques.size(); ++j) {
        double pmi =
            WordScanner.pointwiseMutualInformationOf(unigramProbs, skipgramProbs, uniques.get(i),
                uniques.get(j));
        m.put(i, j, pmi);
        m.put(j, i, pmi);
      }
    }
    System.out.println("That took: " + (System.currentTimeMillis() - startMatrix));

    System.out.println("Getting U");
    long startU = System.currentTimeMillis();
    INDArray u = new CpuLapack().getUFactor(m);
    System.out.println("That took: " + (System.currentTimeMillis() - startU));


    long start2 = System.currentTimeMillis();
    System.out.println(u.getRow(uniques.indexOf("smile")).getColumn(uniques.indexOf("happy")));
    System.out.println(
        WordScanner.pointwiseMutualInformationOf(unigramProbs, skipgramProbs, "smile", "happy"));
    System.out.println("That took: " + (System.currentTimeMillis() - start2));
  }
}
