import com.google.common.collect.MinMaxPriorityQueue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.junit.Test;

public class Word2VecTest {

  @Test
  @SneakyThrows
  public void buildGraph() {

    final String source = earthquake;

    // build graph
    ConcurrentMap<String, Set<WordScanner.VectorDistancePair>> adjacencyList =
        new ConcurrentHashMap<>();
    Set<String> uniques = WordScanner.valuableTokensOf(source);

    ExecutorService service = Executors.newFixedThreadPool(9);
    for (String unique : uniques) {
      service.submit(new PopulateSimilarN(adjacencyList, unique, uniques));
    }
    service.shutdown();
    service.awaitTermination(1, TimeUnit.MINUTES);

    // walk the graph
    Map<String, Integer> scores = new TreeMap<>();

    Map<String, Integer> frequencies = WordScanner.wordFrequenciesOf(source);
    long start = System.currentTimeMillis();
    Map<String, Integer> totals = WordScanner.totalLinkagesOf(source);
    System.out.println(System.currentTimeMillis() - start);

    adjacencyList.forEach((rootWord, setOfPairs) -> {
      if (totals.containsKey(rootWord)) {
        scores.put(rootWord, scores.getOrDefault(rootWord, uniques.size()) - totals.get(rootWord));
      } else {
        // Normalize the cleaning/munging of all of the words
        System.out.println(rootWord);
      }
      setOfPairs.forEach(
          pair -> {
            scores.put(rootWord,
                scores.getOrDefault(rootWord, uniques.size()) + frequencies.get(pair.getWord()));
          });
    });

    List<Integer> scoresOnly = new ArrayList<>();
    scores.forEach((word, score) -> scoresOnly.add(score));
    Collections.sort(scoresOnly);
    int nintiethPercentile = scoresOnly.get((int) Math.round(scoresOnly.size() * 0.95));

    MinMaxPriorityQueue<ScoredWords> topics = MinMaxPriorityQueue
        .maximumSize(5)
        .create();
    scores.forEach((word, score) -> topics.add(new ScoredWords(word, score)));

    List<String> finalWords = new ArrayList<>();
    scores.forEach((word, score) -> {
      if (score >= nintiethPercentile) {
        finalWords.add(word);
      }
    });

    System.out.println(finalWords);

    //topics.forEach(scoredWords -> {
    //  System.out.println(scoredWords.word + " -- " + scoredWords.score);
    //});
    //scores.forEach((s, aDouble) -> System.out.println(s + " -- " + aDouble));
  }

  @RequiredArgsConstructor
  private static class PopulateSimilarN implements Runnable {
    private final ConcurrentMap<String, Set<WordScanner.VectorDistancePair>> adjacencyList;
    private final String rootWord;
    private final Set<String> wordSet;
    @Override public void run() {
      adjacencyList.put(rootWord, WordScanner.mostSimilarToN(rootWord, wordSet, 4));
    }
  }

  @Data
  @RequiredArgsConstructor
  private static class ScoredWords implements Comparable<ScoredWords> {
    private final String word;
    private final Integer score;

    @Override public int compareTo(ScoredWords other) {
      return -this.score.compareTo(other.score);
    }
  }

  private static final String corpus =
      "Code reviews are a valuable part of developing software. A good code review process can help reduce the number of bugs that make it through to production, but more importantly they will help ensure that the code you submit is understandable by more than just you. Unfortunately, there are many times where management will be against code reviews. Usually the argument is that there’s no point in wasting other engineers’ time and if you should be reviewing your code on your own. There’s pockets of validity in the fact that the time could be spent elsewhere, but that doesn’t justify not having them at all. Code reviews will take time, and they should take time. In order to be effective at reviewing code, you should be understanding the changes being submitted in their full context, not just the changes as they stand.\n"
          +
          "\n"
          +
          "For example, if someone were adding some member variable to a class, the diff would likely show that member being initialized, and then accessors being added to that class file. At first glance, this might be a really simple addition and shouldn’t take much time to add. However, what is this member being added? Why is it being added to this class? Does it make sense to be added here? Maybe the value for that member can be derived from other variables and as such doesn’t need to have its own member variable, maybe you can return its calculated value on every get. The code that is being changed is often not the thing that matters. It’s the intention of the change. In order to understand the intention of the change, you need to understand the code that surrounds the change. The context is important because it changes how you view everything that you’re reviewing.\n"
          +
          "\n"
          +
          "Code reviews themselves typically serve two purposes, to find bugs, and to make sure that the code is understandable. Finding bugs is very useful, although there’s an argument for just having tests to help prevent bugs. Unit tests won’t catch every bug, even with 100% coverage, but I’d argue that code reviews are more valuable for helping spread knowledge in the code base. In a perfect world, any engineer could take over for any other engineer. This isn’t to say that every engineer is replaceable, just that there shouldn’t be parts of the code base that are necessarily owned solely by one person. At least an introductory understanding of all parts of the code base should be held by everyone. Obviously, this stops working as well when you have an increasingly large code base. You want to get around this by making sure that there’s ample documentation for all parts of the code base. This helps because even if someone might not understand all parts, they could go read the documentation and understand how it works. It’s worth pointing out that this is incredibly hard to do. Writing documentation that truly distills all the knowledge of the author is really difficult and requires the author to be good with writing and communicating which can be difficult. The ideas make sense in your head but it’s hard for you to translate those to paper.\n"
          +
          "\n"
          +
          "making documentation easy to understand also circles back to something that can be checked in a code review. many people will say that it’s not worth the time because it slows down development, but this is patently false. writing documentation is always valuable and should always be done. it makes passing off code easy to do as well, but this is starting to digress. when you are acting as the reviewer, you need to be aggressive in whether or not you understand some bit of code. if someone tells you that what you’re asking for is redundant or obvious, tell them that it’s clearly not the case because otherwise you wouldn’t have been confused in the first place. you shouldn’t feel hesitant to ask for any changes. if you are, then you need to talk to the person submitting code, or maybe you need to establish a different culture in regards to code reviews. people who don’t understand the value in a code review will just want to churn out code which can be fun but you have to remember that you’re trying to provide the best product for your clients. code reviews will help that, and you need to stand firm in asking for your changes even if someone is being combative about it. on the other hand, if they’re being truly against making the changes, then accept the changes and create a new branch and pr with the changes that you wanted. there’s nothing stopping you from adding commits to that same branch.\n"
          +
          "\n"
          +
          "A big problem I find with code reviews is that people get too defensive about their own code. They take it personally if you say that the code is hard to understand, but you need to just calmly remind them that your intention here is to help improve the code base. You’re not trying to belittle them, you’re not trying to insult their abilities, you just want the code base to be easy to understand.\n"
          +
          "\n"
          +
          "As for who does the code reviews, it’s common to have whoever originally wrote the code be the person to review it. However, you’ll find the case where you were the original author, and now you need someone else to review your changes. In my opinion, you should keep a circular queue of who you have asked to review your code. This way you’re getting feedback from all members of your team which is a great way to help you improve and learn quickly. You’ll get to see the opinions of all people. This can be very daunting. I know that for myself, I will tend to ask specific people to do all my code reviews because they tend to be easier to do reviews with. For example, someone might be less opinionated about something, and some times you might ask someone who doesn’t really care to review your code. This means your code gets reviewed more quickly and you’re less likely to be in a situation where someone questions your code. Asking the most opinionated person on your team to review your code means that you’ll have the most opportunities to learn and improve, even if it is a little more scary. Another thing to keep in mind here is to ask both people your senior and junior to review your code. Don’t only ask your seniors to review your code because you think that juniors won’t provide anything valuable. Take all opinions and levels of experience seriously with these reviews.\n"
          +
          "\n"
          +
          "Code reviews should not be for stylistic preference. If you’re checking that code styles match in a code review, then you’re wasting your time. You should have a linter for that. Linter’s are a small time investment and keep everything consistent. If you don’t like the preset or default for a linter, then change it. You don’t have to use all the same styles as an already defined code base, but the point is that all of the code is consistent. If you can tell who wrote the code without looking at a blame then that means that whoever is reading the code will now have to understand the style being used. If you have several different styles of code being used, then that just makes your code base cluttered for no reason.\n"
          +
          "\n"
          +
          "Code reviews should be small. Ideally, you’re never reviewing more than just a few hundred lines at a time. You want to keep these small because that means there’s less context that someone needs to know in order to understand the changes. If you submit a code review that changed 1500 lines in 30 files, then it’s going to be nearly impossible for someone to be able to understand what the implications are of every change. This also means that code reviews don’t have to only be for master branches. You can submit a pull request for merging your code into your own branch and have someone read it. Yes, this does tend to slow down your development velocity, but it can absolutely be worth it to have eyes on your changes often, as opposed to working on the same branch for too long and then you have some very glaring mistake that you’ve been building upon.\n"
          +
          "\n"
          +
          "Having a culture and system around code reviews is difficult, but it’s really worth it. You’re helping your development team understand and share each other’s code and you’re also trying to provide the best experience for your users. It’s a win win, even if it feels like it’s a hindrance.";

  private static final String cnn =
      "Washington (CNN) - President Donald Trump on Sunday again mocked North Korean leader Kim Jong Un and said Secretary of State Rex Tillerson should not bother trying to negotiate with him in an effort to stop the country's development of nuclear weapons.\n"
          + "\n"
          + "\"I told Rex Tillerson, our wonderful Secretary of State, that he is wasting his time trying to negotiate with Little Rocket Man...\" Trump said on Twitter.\n"
          + "\n"
          + "He continued, \"...Save your energy Rex, we'll do what has to be done!\"\n"
          + "\n"
          + "Trump's tweets undermining his secretary of state follow his attacks Saturday on San Juan's mayor over the Puerto Rico hurricane crisis and come a day after Tillerson said the US had direct lines of communication with North Korea and that he was trying to \"calm things down\" following months of escalating rhetoric over Pyongyang's continued nuclear weapons and ballistic missile tests.\n"
          + "\n"
          + "Tillerson, speaking at a press conference in Beijing, said the US made it clear through its direct channels to North Korea that it was seeking peace through talks.\n"
          + "\n"
          + "\"We've made it clear that we hope to resolve this through talks,\" Tillerson said.\n"
          + "\n"
          + "\"I think the most immediate action that we need is to calm things down,\" Tillerson added. \"They're a little overheated right now, and I think we need to calm them down first.\"\n"
          + "\n"
          + "Asked about Trump's own rhetoric, Tillerson said the entire situation was \"overheated.\"\n"
          + "\n"
          + "Trump's tweets on Sunday seem to directly counter Tillerson's stated goal to use direct communication to lower tension between the two hostile nations. The US and North Korea have ramped up their rhetoric about one another as Pyongyang continues to develop its nuclear weapons program.\n"
          + "\n"
          + "Asked if the President's tweets indicate he has decided to abandon the diplomatic track on North Korea, a senior administration official told CNN: \"We are still committed to a diplomatic approach.\"\n"
          + "\n"
          + "Trump has sent similar tweets complicating diplomatic efforts in the past on issues such as Qatar's regional isolation over accusations that it supports terrorism and the future of NATO.\n"
          + "\n"
          + "Tillerson and Defense Secretary James Mattis have said the goal on North Korea is to reach a diplomatic solution between the countries, and after the pair briefed members of Congress in closed-door meetings in September, some Democrats noted the difference between their assessments and the President's words.\n"
          + "\n"
          + "\"I feel like we still have two different polices on North Korea: one at the Department of State and Department of Defense, and another on the President's Twitter feed,\" Sen. Chris Murphy, a Connecticut Democrat, said after the briefing.\n"
          + "\n"
          + "In August, Mattis stressed the importance of the nation's diplomatic efforts, particularly through the United Nations, but in September he warned the US would meet threats from North Korea with \"a massive military response.\"\n"
          + "\n"
          + "Last month, North Korea conducted its sixth nuclear test and in recent months it has launched missiles multiple times, which experts say could reach the mainland US.\n"
          + "\n"
          + "In early August, Trump warned the US would rain down \"fire and fury\" on North Korea, saying the US would destroy the nation of some 25 million people if its dictator's threats against the US and its allies continued.\n"
          + "\n"
          + "Trump delivered a speech to the United Nations in September in which he referred to the North Korean dictator as \"rocket man,\" an insult he has used several times, including in Sunday's tweets.\n"
          + "\n"
          + "North Korea responded to the insult at the UN in kind, with Kim saying, \"I will surely and definitely tame the mentally deranged US dotard with fire.\"\n"
          + "\n"
          + "In response to North Korea's continued weapons development this year, the UN Security Council agreed to increased sanctions on North Korea, gaining support from China and Russia.\n"
          + "\n"
          + "Trump also signed an executive order penalizing any company or person doing business with North Korea.";

  private static final String interview =
      "Interviewing as a software engineer is effectively broken. As an interviewer, it’s basically impossible to really determine someone’s skill level, and as an interviewee it’s basically impossible to prove to someone why they should hire you.\n"
          + "\n"
          + "Let’s look at the structure of interviewing, and then talk about what interviewing would be like in a perfect world.\n"
          + "\n"
          + "When you’re an interviewer, there is one question that you need to answer. That question is, “Is this person capable of providing value to this company?”. Businesses need to generate revenue. Ideally they even generate profit. Employees are vehicles to generating revenue, and regardless of how ideal or positive you might be, that’s exactly what you’re there for. As the person making a hiring decision, or just providing feedback to someone who will make a hiring decision, you have to determine whether or not this person will help in that goal. The company will typically be paying some salary (or equivalent) to this person, which means at the very minimum, that person needs to provide enough value to match their salary.\n"
          + "\n"
          + "Say someone gets paid $100,000 a year. This potential hire at the absolute bare minimum, must generate $100,000 a year in revenue or value for the company, otherwise that hire is a money pit. It’s worth pointing out, that this breaks for a company like Google. Almost all of their money comes from search ads, but not every single engineer is working on search. When a company prints money like they do, the rules just break in general. Anyways, back on topic.\n"
          + "\n"
          + "In what ways can someone provide value? Well, maybe you work on a feature that gets the company more customers. Maybe you wrote a clone of some service that’s paid for, and you reduce costs elsewhere. Maybe you come in and refactor something which helps other people implement a feature that provides value, which means you’ve also, albeit indirectly, but you’ve also provided value. The ways that someone can provide value is endless. This is a problem. This is a real problem because that means that with no limitation of parameters, everyone can decide differently how to determine if someone will provide value. There’s a bias in how everyone is going to think about providing value. This also means that everyone is going to have a different way to evaluate what kind of value you would provide. This is entirely broken.\n"
          + "\n"
          + "Interviewing is too emotional. If you interview with someone on a bad day, they’re going to fixate on reasons why you couldn’t possibly provide value. If you interview with someone whom with you have a lot in common, then they’re going to inherently believe that you could provide value, because they think they provide value. Of course they probably provide value, but they’re attributing the wrong characteristics to why you could provide value. It doesn’t matter if you both like the same TV shows, or you guys come from the same small town. Those factors will not help you provide value, but they will make the interviewer like you more, and they’ll make them want to find reasons to hire you.\n"
          + "\n"
          + "Interviewing is too blind. Writing software is so much more than just the act of writing code. If you want to write software professionally, you’re talking about planning, debugging, writing, testing, bug reporting, all sorts of things. But most people will only interview on two things: culture fit, and technical ability in its broadest definition. It’s impossible to be able to measure all of that, let alone in a reasonable amount of time to ask of an interviewee.\n"
          + "\n"
          + "The first step in the job application pipeline is typically the resume screen. You have to somehow distill your most impressive information into a single page. How can you do that though? Well, the best way is for you to describe situations where you provided quantifiable metrics of value for your employer. As we’ve established though, sometimes figuring out what that exact amount is is impossible. Put it into some scale of numbers though. For example:\n"
          + "\n"
          + "Increased code coverage of a 200k LOC project from 70% to 75%\n"
          + "Reduced a search query from taking 200ms down to 25ms\n"
          + "Increased average use time by 3 minutes with feature X\n"
          + "These examples don’t translate to an exact dollar value, but they provide some understanding of improvements that you’ve made that will in turn translate to value.\n"
          + "\n"
          + "Ultimately though, resumes aren’t really a great indicator of who might be best for the job, because someone might have truncated information to get it to fit to a single page. Maybe someone is just a bad writer and can’t express their accomplishments well. Sometimes the work you do has to be written as to remove specific but impressive details due to an NDA or some other reason. In that case, what are your options? What can you do that’s better than representing yourself with a resume? You can get referred.\n"
          + "\n"
          + "A long career usually means you’ve had many coworkers. Not all of them will stay at the same company for their entire life. If you have friends at a different company, you can ask them to refer you. This works well for college new graduates as well. Make friends with people who are graduating before you, and when it’s your turn to graduate, ask around your alumni friends and see what they’ve all been up to. This is one of the reasons that networking and going to meetups is really useful, because you can use it to help skip the resume screening step which is painfully noisy. Sometimes you’ll be approached by recruiters on websites like LinkedIn, and maybe you can find someone who works at a company you want to be at on the same website. You can politely message them and ask about getting referred. Many companies will have a referral bonus, so some employees will happily refer you just for their chance to get that bonus.\n"
          + "\n"
          + "A relatively new opportunity to skip the resume screening process is to be blindly interviewed. The website interviewing.io will let you do anonymous practice interviews, and if you do well in an interview, you might be invited to do a final round interview. Also, I’m not affiliated in any way with interviewing.io. I’ve used their service for practicing for a Google interview, and I enjoyed it so I wanted to give it a shout out.\n"
          + "\n"
          + "After your resume screening, you’ll probably be contacted by a recruiter or someone from HR. Their job is to figure out whether or not they think it’s worth spending any more time to see if you could be a potential hire. Sometimes this step doesn’t exist if a company is smaller and doesn’t have someone dedicated to this job. After the recruiter chat, you’ll likely go into technical steps of the interview. This is where things begin to get incredibly arbitrary and this is where no real solution has been discovered.\n"
          + "\n"
          + "There’s two common camps for technical portions of the interview process. The first one is common amongst a lot of larger companies like Google, Facebook, Uber, AirBnB, and so on. These are the infamous whiteboard interviews where you get asked algorithmic problems that are supposed to test you for your problem solving skills. You can find examples of these types of interview problems all over the internet.\n"
          + "\n"
          + "Perhaps one of the more famous cases of these problems is when Max Howell, the author of the brew package management system on OSX, interviewed at Google. He wasn’t happy with his experience. Whiteboard problems can be telling, but they tend not to be. These problems are supposed to assess your abilities to think critically, and to check your depth of knowledge for Computer Science. One of the problems with that is that we’re not really computer scientists when we’re on the job. In most cases, we’re software engineers that need to deliver some product to our users. This means that we’re querying databases, or we’re provisioning servers on AWS. Knowing how to use a trie to find words on a boggle board isn’t necessarily going to help us. Of course there are exceptions, like if you’re writing a boggle clone, but many times this isn’t the case. Some of these questions are genuinely inciteful, but it’s up to the interviewer to make them so. The interviewer should be trying to map out your knowledge. Algorithm questions have a tendency to check for one specific area of knowledge. At Google they interview while checking your knowledge in five distinct areas, but they always ask you these algorithm questions. There might be better ways to assess those five areas, but they’ll try to gleam your ability to write object oriented code from how you traverse a graph. A lot of these questions also have some data structure or algorithm that if you knew what it was, you could write a solution very quickly.\n"
          + "\n"
          + "Some people will tell you that getting the solution doesn’t matter, and that they’re actually testing your abilities to communicate your thoughts. If this were true, why give someone a question that makes them super uncomfortable? Why take away the resources that they get to use on the job like StackOverflow or auto complete? It’s like a hazing ritual that persists because “I went through it so now you should too”. I don’t like this method of interviewing, even if I find it personally fun. It doesn’t really give you a good idea about whether or not someone might be a good employee for the job.\n"
          + "\n"
          + "The common alternative to whiteboard problems is to receive some take home coding challenge. Some people really like these because it lets you show off your abilities in an environment that you get to determine. The problem I find with a lot of these is that they’re super vague. The employers will know exactly what they’re looking for, but they won’t give you any actual direction. They want to see if you can “figure it out” or see if you follow “best practice”. They justify it by saying that you need to be a self-starter, but this is garbage. Someone might spend 30 minutes working on your challenge, but some people might spend 20+ hours doing all sorts of testing, CI/CD, and who knows what else. The size of these projects also tends to be way off base with what’s reasonable. Different people work at different paces, but this isn’t really telling for a potential employee either. Someone might submit a working solution for your vague problem and it only takes them an hour or two. However, you’ll quickly find that their solution doesn’t generalize well. Maybe someone takes a while to do the challenge, and you think that means they’re slow, but it turns out they have 100% code coverage and have tests for a large number of edge cases. I think most people would say they want the latter case to happen, but what if this takes them 10 hours because they’re using a stack that they’ve never used? Are you going to ding them for not having experience in your EXACT stack? That’s also unreasonable because of the insane number of combinations of tools someone might have in a given stack. Also, lack of experience in Rails doesn’t mean someone would make a bad Rails developer.\n"
          + "\n"
          + "Sometimes people give challenges that they expect to take 40+ hours. That means just for an interview, you’re expected to put in over a week’s worth of work. And you’re not even guaranteed the job. The solution to this that I’ve seen is when a company will ask you to develop such a large feature, but they’ll actually pay you a consulting fee for your time. I really like this because it means that the company is more respectful of your time. There’s still problems here though. You might be interviewing multiple people at once, and maybe one person has external time commitments that means it takes them 3 weeks to do the 40 hours worth of work. There’s another candidate who knocked it out in one week + the weekend and now he’s waiting to hear back. What do you do? Do you tell all candidates that they have to wait for the slowest person? The last person to submit the challenge might be the best programmer but you need to hire someone sooner, so you go with the person who submitted first. You’re giving an advantage to the person who can drop everything and work on your large challenge. Even in the opposite perspective, if you wait for the person who takes longer, that means you’re drawing out the interview process for the person who was more responsive to you.\n"
          + "\n"
          + "There are problems with both of these types of interviews, and sometimes you’ll have companies who will do both to extreme ends. I’ve read about interview processes that take months of constant interviewing with different people. As a candidate you might be trying to get a job as soon as possible, and you just don’t have that kind of time to wait. Unfortunately, if there’s a better method of interviewing, I’m not really sure what it is. This mock interview done by Casey Muratori and Shawn McGrath is an interesting approach that I find useful. It’s basically just a really long conversation about your history and experience, where the interviewer is dynamic and flexible about asking questions to help guide the interviewee to talk about things that are useful to know. Other than that, I think the best way to hire is to use your personal network and take strong recommendations very seriously. There are many people that I’ve met, where if they were looking for a job, I would swear on their abilities to my current employer. This still only works with people with longer careers, and its biased to help the people who are social and build strong networks, but I think using a web of trust is the best way to hire. You trust your inner circle enough to hire them, now you need to trust that their circles are good enough to be hired as well.";

  private static final String languages =
      "Type Checking indicates static or dynamic typing. In statically typed languages, type checking occurs at compile time, and variable names are bound to a value and to a type. In addition, expressions (including variables) are classified by types that correspond to the values they might take on at run-time. In dynamically typed languages, type checking occurs at run-time. Hence, in the latter, it is possible to bind a variable name to objects of different types in the same program.\n"
          + "\n"
          + "Implicit Type Conversion allows access of an operand of type T1 as a different type T2, without an explicit conversion. Such implicit conversion may introduce type-confusion in some cases, especially when it presents an operand of specific type T1, as an instance of a different type T2. Since not all implicit type conversions are immediately a problem, we operationalize our definition by showing examples of the implicit type confusion that can happen in all the languages we identified as allowing it. For example, in languages like Perl, JavaScript, and CoffeeScript adding a string to a number is permissible (e.g., \"5\" + 2 yields \"52\"). The same operation yields 7 in Php. Such an operation is not permitted in languages such as Java and Python as they do not allow implicit conversion. In C and C++ coercion of data types can result in unintended results, for example, int x; float y; y=3.5; x=y; is legal C code, and results in different values for x and y, which, depending on intent, may be a problem downstream.a In Objective-C the data type id is a generic object pointer, which can be used with an object of any data type, regardless of the class.b The flexibility that such a generic data type provides can lead to implicit type conversion and also have unintended consequences.c Hence, we classify a language based on whether its compiler allows or disallows the implicit type conversion as above; the latter explicitly detects type confusion and reports it.\n"
          + "\n"
          + "Disallowing implicit type conversion could result from static type inference within a compiler (e.g., with Java), using a type-inference algorithm such as Hindley10 and Milner,17 or at run-time using a dynamic type checker. In contrast, a type-confusion can occur silently because it is either undetected or is unreported. Either way, implicitly allowing type conversion provides flexibility but may eventually cause errors that are difficult to localize. To abbreviate, we refer to languages allowing implicit type conversion as implicit and those that disallow it as explicit.\n"
          + "\n"
          + "Memory Class indicates whether the language requires developers to manage memory. We treat Objective-C as unmanaged, in spite of it following a hybrid model, because we observe many memory errors in its codebase, as discussed in RQ4 in Section 3.\n"
          + "\n"
          + "Note that we classify and study the languages as they are colloquially used by developers in real-world software. For example, TypeScript is intended to be used as a static language, which disallows implicit type conversion. However, in practice, we notice that developers often (for 50% of the variables, and across TypeScript-using projects in our dataset) use the any type, a catch-all union type, and thus, in practice, TypeScript allows dynamic, implicit type conversion. To minimize the confusion, we exclude TypeScript from our language classifications and the corresponding model";

  private static final String earthquake =
      "The scene is terrifying -- entire sections of a Mexico City office building fall away and crash to the ground. The screams of people reacting are almost worse.\n"
          + "\n"
          + "That moment, captured on cellphone video, shows just one of a number of buildings, from apartments to schools to governmental offices, that collapsed during the 7.1 magnitude quake that hit Mexico on September 19.\n"
          + "\n"
          + "\"I've seen that video,\" says Mark Schlaich, vice president of engineering at Los Angeles-based Alpha Structural Inc. \"It's what we're trying to prevent here.\" Schlaich waves at the central Los Angeles multistory apartment complex he's standing under, a pre-1978 wood-frame soft-story building -- so-called because the first story is substantially weaker and more flexible than the stories above it, lacking walls or frames at the street level, usually reserved for parking spaces.\n"
          + "\n"
          + "Alpha Structural is retrofitting the apartment complex's ground floor, installing much thicker steel beams and columns, rebar and heavier plywood.\n"
          + "\n"
          + "Mexico City's numerous building collapses were a stark reminder of what's to come in California. \"Every 20-25 years, Los Angeles has been hit by a major earthquake,\" says Schlaich. It has been 23 years since the 6.7 magnitude Northridge earthquake hit the city in 1994. \"Statistically, it's coming.\"\n"
          + "\n"
          + "Reducing the number of buildings that might collapse\n"
          + "\n"
          + "In a 2008 study, the US Geological Survey found there's a greater than 99% chance of a 6.7 magnitude quake or larger hitting the California area over the next 30 years. That type of quake along the San Andreas Fault in Southern California could kill an estimated 1,800 people, the study said, injure 53,000 and result in $214 billion in damage. California's infrastructure could be crippled for weeks, if not months.\n"
          + "\n"
          + "In a stark recognition of those numbers, in October 2015, Los Angeles enacted the nation's most sweeping seismic regulations, requiring about 14,000 buildings to be retrofitted so they will withstand violent shaking.\n"
          + "\n"
          + "The apartment building Alpha Structural is retrofitting is one of those city-identified vulnerable buildings. Schlaich points to a new steel column next to a clearly smaller, older column. The steel column goes 5 feet into the ground, plus 2 feet of additional concrete below it. \"This column keeps the people alive, so the building won't collapse,\" explains Schaich. The only problem with the retrofitting in Los Angeles, say seismologists, is the expensive process is happening too slowly, expected to be complete in 2022.\n"
          + "\n"
          + "In San Francisco, a similar city law for retrofitting soft-story buildings was enacted in 2013. Rolling out in four phases, San Francisco has seen compliance at near 99% for phase one and two, and above 85% for phase three, says Bill Strawn, spokesman for San Francisco's Department of Building Inspection. The fourth and last phase of retrofitting the city's soft-story buildings wraps in September 2018. Strawn says once complete, the building collapse rate substantially improves in San Francisco. \"We go from an estimated 1 in 4 collapse rate of these nonretrofitted buildings during a quake to 1 in 30. And that's with minimal retrofitting,\" says Strawn.\n"
          + "\n"
          + "The 2008 study estimating damage at $214 billion was based on the state of buildings at that time, and efforts to retrofit buildings at the city level \"undoubtedly change the statewide damage estimates,\" says geophysicist Doug Given, who serves as the earthquake early warning coordinator for USGS.\n"
          + "\n"
          + "Developing an early warning system\n"
          + "\n"
          + "There is another tool that would save lives and reduce costs in an earthquake, one that Given calls \"a no-brainer.\"\n"
          + "\n"
          + "The Earthquake Early Warning system, as it's called, is the best available technology to give seconds' -- at times, minutes' -- notice that an earthquake is coming.\n"
          + "\n"
          + "It would use seismometers buried underground to detect shaking crucial moments before it's perceptible in all the areas that will experience the quake, and sensors distributed across the West Coast capture the data in real time.\n"
          + "\n"
          + "The most beneficial part of those 30-60 seconds, says CalTech engineering and seismology professor Tom Heaton, is simply the heads-up. Children can get under desks or move to safer areas of the school. Surgery can stop. Trains can come to a halt.\n"
          + "\n"
          + "\"This system is to provide as much information as is possible so you can make the best decisions during the earthquake,\" says Heaton.\n"
          + "\n"
          + "In the Mexico City quake, the early alert system warned people up to 30 seconds in advance that an earthquake was about to hit, allowing them to flee vulnerable buildings or move to safer areas.\n"
          + "\n"
          + "Mexico put its robust warning system into place after nearly 5,000 people died in the 1985 Mexico City quake. An early warning system has also been in place in Japan for decades. Today, cellphones and school alarms ring in unison when a quake is about to hit, a high-tech system installed after the Kobe earthquake in 1995.\n"
          + "\n"
          + "But in the United States, the early warning system is in development, stalled due to federal funding constraints. USGS says only 40% of the necessary sensors for the EEW system are in the ground.\n"
          + "\n"
          + "Hurdles stand in the way\n"
          + "\n"
          + "\"We only have two-thirds of the annual operating funds,\" says Given. \"We're trying to build a system with that limited funding stream and will never get to a fully functioning system at this rate.\"\n"
          + "\n"
          + "\"I can't help but think of Mexico City, people killed by buildings and falling debris. ... We don't want to see the same situation here,\" he says. \"I fear we will regret we didn't do this when we had the chance.\"\n"
          + "\n"
          + "USGS and the CalTech Seismological Laboratory started research on the EEW in 2006 with a thin budget, relying on federal funding and some private donations. In 2012, they went live with a beta system, and earlier this year rolled out a pilot program to train systems and some utilities.\n"
          + "\n"
          + "This year, underscoring the lack of national urgency for an EEW, the Trump administration initially budgeted nothing toward the USGS EEW system. Bipartisan demands by members of Congress tentatively restored the USGS EEW funding to $10.2 million for this year, although Congress has yet to vote on the budget. The state of California last year also allocated $10 million to developing the EEW system.\n"
          + "\n"
          + "But USGS says the EEW system will cost roughly $38 million to build and $16 million a year for operation and maintenance.\n"
          + "\n"
          + "\"It's frustrating,\" says Heaton. \"If you look at other countries, they got the will and message after big and important earthquakes. We'd like to do it before that happens.\"\n"
          + "\n"
          + "He points to the nation's attention to other disasters and political priorities as one of the reasons for the slow rollout for the EEW system. As a result, he says, \"we're not ready.\"\n"
          + "\n"
          + "Heaton underscores what a 7.2 earthquake in Southern California would mean. \"You'd see collapse of literally hundreds of older concrete frame buildings,\" he says. \"You'd see the collapse of many tall steel buildings built before 1995. We might even see collapse of today's most modern tall buildings. It could be a very bad scenario. I honestly don't want to live to see that day.\"\n"
          + "\n"
          + "More than 100,000 people -- even as many as 300,000 -- could be displaced if a major earthquake were to hit Los Angeles, estimates Aram Sahakian, general manager for the Los Angeles Emergency Management Department.\n"
          + "\n"
          + "As cities and states try to prepare for the Big One, seismologists and city officials also recommend being prepared at a personal level.\n"
          + "\n"
          + "\"A major disaster, like a 7.2 earthquake, will overwhelm our resources,\" says Sahakian. \"You as a person, your family, and your neighborhood need to be prepared to get no response if you call 911. You need to be able to survive on your own for hours or days.\"";
}
