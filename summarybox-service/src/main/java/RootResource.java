import java.util.Set;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Path("/")
@RequiredArgsConstructor
public class RootResource {

  private final WordScanner scanner;
  private final ThreadLocalDetector detector;

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response keywords(SummaryRequest t) {
    SummaryResponse resp = SummaryResponse.builder().keywords(
        scanner.textRank(t.getCorpus(), t.getSimilar(), t.getPercentile(), t.getTopics())
        ).build();
    return Response.ok(resp).build();
  }

  @Data
  private static class SummaryRequest {
    private String corpus = "";
    private Integer similar = 4;
    private Double percentile = 0.97;
    private Integer topics = 4;
  }

  @Data
  @Builder
  private static class SummaryResponse {
    public Set<String> keywords;
  }
}
