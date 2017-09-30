import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.tokenize.SimpleTokenizer;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

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

    static List<String> wordsOf(String s) {
        return Arrays.stream(SimpleTokenizer.INSTANCE.tokenize(s))
                .filter(s1 -> s1.length() != 1 || Character.isAlphabetic(s1.charAt(0)))
                .collect(Collectors.toList());
    }

    static List<String> sentencesOf(String s) throws IOException {
        return Arrays.stream(detector.sentDetect(s)).collect(Collectors.toList());
    }

    static List<Integer> vectorOf(List<String> words) {
        Map<String, Integer> map = new TreeMap<>();
        for (String s : words) {
            if (map.containsKey(s)) {
                int currentCount = map.get(s);
                map.put(s, currentCount + 1);
            } else {
                map.put(s, 1);
            }
        }
        return new ArrayList<>(map.values());
    }
}
