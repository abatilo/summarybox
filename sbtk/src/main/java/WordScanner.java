import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.tokenize.SimpleTokenizer;

import java.io.IOException;
import java.io.InputStream;

public class WordScanner {
    private static SentenceDetectorME detector;
    static {
        final InputStream modelStream =
                WordScanner.class.getResourceAsStream("en-sent.bin");
        try {
            final SentenceModel model = new SentenceModel(modelStream);
            detector = new SentenceDetectorME(model);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (modelStream != null) {
                try {
                    modelStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    static String[] split(String s) {
        return SimpleTokenizer.INSTANCE.tokenize(s);
    }

    public static String[] sentence(String s) throws IOException {
        return detector.sentDetect(s);
    }
}
