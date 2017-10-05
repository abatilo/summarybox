import lombok.RequiredArgsConstructor;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;

@RequiredArgsConstructor
public class ThreadLocalDetector extends ThreadLocal<SentenceDetectorME> {
  private final SentenceModel model;
  @Override public SentenceDetectorME get() {
    return new SentenceDetectorME(model);
  }
}
