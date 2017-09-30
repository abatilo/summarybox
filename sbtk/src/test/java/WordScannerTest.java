import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;

public class WordScannerTest {
    @Test
    public void split() throws Exception {
        String s1 = "This is a test";
        String[] expected = new String[] {
                "This", "is", "a", "test"
        };
        assertArrayEquals(expected, WordScanner.split(s1));
    }
}