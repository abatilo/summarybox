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

  public static class ResourceFactory extends BaseResourceFactory {
    public ResourceFactory(SummaryBoxConfiguration config, Environment env) throws IOException {
      super(config, env);
    }

    private final String w2vModel = config.getW2vModel();
    private final Word2Vec vec = WordVectorSerializer.readWord2VecModel(w2vModel);

    private final InputStream sentenceStream =
        Resources.getResource(config.getSentenceModel()).openStream();
    private final SentenceModel sentenceModel = new SentenceModel(sentenceStream);
    private final ThreadLocalDetector detector = new ThreadLocalDetector(sentenceModel);

    private final InputStream posStream = Resources.getResource(config.getPosModel()).openStream();
    private final POSModel posModel = new POSModel(posStream);
    private final ThreadLocalTagger tagger =
        new ThreadLocalTagger(posModel);

    private final Set<String> STOP_WORDS =
        Sets.newHashSet(Resources.readLines(Resources.getResource(config.getStopWords()),
            Charset.defaultCharset()));

    private final WordScannerConfiguraton wordScannerConfig = config.getWordScanner();
    private final Set<String> TAGS = wordScannerConfig.getAllowedTags();
    private final int similar = wordScannerConfig.getSimilarToTop();
    private final double percentile = wordScannerConfig.getPercentile();
    private final int topics = wordScannerConfig.getTopics();
    private final WordScanner scanner =
        new WordScanner(detector, tagger, vec, STOP_WORDS, similar, percentile, topics, TAGS);

    @Getter(AccessLevel.PUBLIC)
    private final RootResource rootResource = new RootResource(scanner);
  }
}
