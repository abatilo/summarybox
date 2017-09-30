import com.google.common.collect.ImmutableList;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class WordScannerTest {
    @Test
    public void split() throws Exception {
        String s1 = "This is a single sentencesOf. Here, and now, is a second sentencesOf with more words?";
        List<String> expected = ImmutableList.of(
                "This", "is", "a", "single", "sentencesOf", "Here", "and",
                "now", "is", "a", "second", "sentencesOf", "with", "more", "words"
        );
        assertEquals(expected, WordScanner.wordsOf(s1));
    }

    @Test
    public void sentence() throws Exception {
        String sent = "This is sentence number one. " +
                "Now we have the second sentence in this paragraph. " +
                "Here's a thought; what about a third sentence?";
        List<String> expected = ImmutableList.of(
                "This is sentence number one.",
                "Now we have the second sentence in this paragraph.",
                "Here's a thought; what about a third sentence?"
        );
        assertEquals(expected, WordScanner.sentencesOf(sent));
    }

    @Test
    public void vectorMaintainsOrder() {
        String s = "Here are several words that are in a different order." +
                "Here are even more words that are in a different sentencesOf";
        List<Integer> expected = ImmutableList.of(
                2, 2, 4, 2, 1, 2, 1, 1, 1, 1, 2, 2
        );
        assertEquals(expected, WordScanner.vectorOf(WordScanner.wordsOf(s)));
    }
}