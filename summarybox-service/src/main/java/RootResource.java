import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Path("/")
@RequiredArgsConstructor
public class RootResource {

  private final WordScanner scanner;
  private final ThreadLocalDetector detector;

  @POST
  public Response keywords(SummaryRequest t) {
    String fullCorpus = t.getCorpus();
    String[] sentences = detector.get().sentDetect(fullCorpus);

    List<String> corpi = new ArrayList<>();
    StringBuilder sb = new StringBuilder();

    int i = 0;
    int roughlyNumberOfWords = 0;

    while (i < sentences.length) {
      while (i < sentences.length && roughlyNumberOfWords < 100) {
        sb.append(" ");
        sb.append(sentences[i]);
        int wordsInCurrentSentence = sentences[i].split(" ").length;
        roughlyNumberOfWords += wordsInCurrentSentence;
        ++i;
      }
      roughlyNumberOfWords = 0;
      corpi.add(sb.toString());
      sb = new StringBuilder();
    }
    corpi.add(sb.toString());

    try {
      ObjectMapper mapper = new ObjectMapper();
      String resp = mapper.writeValueAsString(SummaryResponse.builder().keywords(
          corpi.stream()
              .map(c -> scanner.textRank(c, t.getSimilar(), t.getPercentile(), t.getTopics()))
              .flatMap(Collection::stream)
              .collect(Collectors.toSet())
      ).build());
      return Response.ok(resp).build();
    } catch (JsonProcessingException e) {
      return Response.serverError().build();
    }
  }

  @Data
  private static class SummaryRequest {
    private String corpus = "";
    private Integer similar = 2;
    private Double percentile = 0.8;
    private Integer topics = 2;
  }

  @Data
  @Builder
  private static class SummaryResponse {
    public Set<String> keywords;
  }
}
