import com.google.common.io.Resources;
import io.dropwizard.setup.Environment;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import lombok.AllArgsConstructor;
import lombok.Getter;
import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSTagger;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.sentdetect.SentenceDetectorME;
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

    // TODO(aaron): Put path in config
    private final InputStream modelStream =
        new FileInputStream(Resources.getResource("en-sent.bin").getFile());
    private final SentenceModel model = new SentenceModel(modelStream);
    private final SentenceDetectorME detector = new SentenceDetectorME(model);

    // TODO(aaron): Put path in config
    private final InputStream posStream =
        WordScanner.class.getResourceAsStream("en-pos-perceptron.bin");
    private final POSModel posModel = new POSModel(posStream);
    private final POSTagger tagger = new POSTaggerME(posModel);

    // TODO(aaron): Put path in config
    private static final String filePath = Resources.getResource("news.bin").getPath();
    private static final Word2Vec vec = WordVectorSerializer.readWord2VecModel(filePath);

    private final WordScanner scanner = new WordScanner(detector, tagger, vec);
    private final RootResource rootResource = new RootResource(scanner);
  }
}
