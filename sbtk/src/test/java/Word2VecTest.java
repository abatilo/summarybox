import org.junit.Test;

public class Word2VecTest {

  @Test
  public void testSerial() {
    System.out.println(WordScanner.mostSimilarToN("software", corpus, 5));
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
          "Making documentation easy to understand also circles back to something that can be checked in a code review. Many people will say that it’s not worth the time because it slows down development, but this is patently false. Writing documentation is always valuable and should always be done. It makes passing off code easy to do as well, but this is starting to digress. When you are acting as the reviewer, you need to be aggressive in whether or not you understand some bit of code. If someone tells you that what you’re asking for is redundant or obvious, tell them that it’s clearly not the case because otherwise you wouldn’t have been confused in the first place. You shouldn’t feel hesitant to ask for any changes. If you are, then you need to talk to the person submitting code, or maybe you need to establish a different culture in regards to code reviews. People who don’t understand the value in a code review will just want to churn out code which can be fun but you have to remember that you’re trying to provide the best product for your clients. Code reviews will help that, and you need to stand firm in asking for your changes even if someone is being combative about it. On the other hand, if they’re being truly against making the changes, then accept the changes and create a new branch and PR with the changes that you wanted. There’s nothing stopping you from adding commits to that same branch.\n"
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
}
