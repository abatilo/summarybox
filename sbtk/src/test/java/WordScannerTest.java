import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertArrayEquals;

public class WordScannerTest {
    @Test
    public void split() throws Exception {
        String s1 = "This is a single sentence. Here, and now, is a second sentence with more words?";
        System.out.println(Arrays.toString(WordScanner.split(s1)));
        String[] expected = new String[] {
                "This", "is", "a", "test"
        };
        assertArrayEquals(expected, WordScanner.split(s1));
    }

    @Test
    public void sentence() throws Exception {
        System.out.println(Arrays.toString(WordScanner.sentence("Interviewing is too blind. Writing software is so much more than just the act of writing code. If you want to write software professionally, you’re talking about planning, debugging, writing, testing, bug reporting, all sorts of things. But most people will only interview on two things: culture fit, and technical ability in its broadest definition. It’s impossible to be able to measure all of that, let alone in a reasonable amount of time to ask of an interviewee.")));
    }
}