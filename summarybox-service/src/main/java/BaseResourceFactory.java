import com.google.common.collect.Sets;
import com.google.common.io.Resources;
import io.dropwizard.setup.Environment;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Set;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import opennlp.tools.postag.POSModel;
import opennlp.tools.sentdetect.SentenceModel;
import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
import org.deeplearning4j.models.word2vec.Word2Vec;

@AllArgsConstructor
public class BaseResourceFactory {
  protected final SummaryBoxConfiguration config;
  protected final Environment env;

  @Getter
  public static class ResourceFactory extends BaseResourceFactory {
    public ResourceFactory(SummaryBoxConfiguration config, Environment env) throws IOException {
      super(config, env);
    }

    @Getter(AccessLevel.NONE) private final String w2vModel = config.getW2vModel();
    @Getter(AccessLevel.NONE) private final Word2Vec vec =
        WordVectorSerializer.readWord2VecModel(w2vModel);
    @Getter(AccessLevel.NONE) private final InputStream sentenceStream =
        Resources.getResource(config.getSentenceModel()).openStream();
    @Getter(AccessLevel.NONE) private final SentenceModel sentenceModel =
        new SentenceModel(sentenceStream);
    @Getter(AccessLevel.NONE) private final ThreadLocalDetector detector =
        new ThreadLocalDetector(sentenceModel);
    @Getter(AccessLevel.NONE) private final InputStream posStream =
        Resources.getResource(config.getPosModel()).openStream();
    @Getter(AccessLevel.NONE) private final POSModel posModel = new POSModel(posStream);
    @Getter(AccessLevel.NONE) private final ThreadLocalTagger tagger =
        new ThreadLocalTagger(posModel);
    @Getter(AccessLevel.NONE) private final Set<String> STOP_WORDS = Sets.newHashSet(
        Resources.readLines(Resources.getResource(config.getStopWords()),
            Charset.defaultCharset()));
    @Getter(AccessLevel.NONE) private final WordScanner scanner =
        new WordScanner(detector, tagger, vec, STOP_WORDS);

    private final RootResource rootResource = new RootResource(scanner);
  }
}
