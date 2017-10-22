import com.google.common.io.Resources;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Main {
  public static void main(String[] args) throws IOException {
    Scanner s = new Scanner(Resources.getResource("text8").openStream());
    List<String> corpus = new ArrayList<>();
    long start = System.currentTimeMillis();
    System.out.println("Streaming in corpus");
    while (s.hasNext()) {
      corpus.add(s.next());
    }
    System.out.println("That took: " + (System.currentTimeMillis() - start));
    System.out.println(WordScanner.pointwiseMutualInformationOf(corpus, "happy", "smile"));
  }
}
