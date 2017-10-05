import com.google.common.collect.Sets;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import io.dropwizard.setup.Environment;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Set;
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

    private final InputStream sentenceStream = Resources.getResource("en-sent.bin").openStream();
    private final SentenceModel sentenceModel = new SentenceModel(sentenceStream);
    private final ThreadLocalDetector detector = new ThreadLocalDetector(sentenceModel);

    // TODO(aaron): Put path in config
    private final InputStream posStream =
        WordScanner.class.getResourceAsStream("en-pos-perceptron.bin");
    private final POSModel posModel = new POSModel(posStream);
    private final ThreadLocalTagger tagger = new ThreadLocalTagger(posModel);

    // TODO(aaron): Put path in config
    private final String filePath = Resources.getResource("news.bin").getPath();
    private final Word2Vec vec = WordVectorSerializer.readWord2VecModel(filePath);

    private final Set<String> STOP_WORDS =
        Sets.newHashSet(
            Files.readLines(new File(Resources.getResource("stopwords.txt").getFile()),
                Charset.defaultCharset()));

    private final WordScanner scanner = new WordScanner(detector, tagger, vec, STOP_WORDS);
    private final RootResource rootResource = new RootResource(scanner);
  }
}
