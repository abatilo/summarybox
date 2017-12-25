# summarybox

A web service to perform keyword extraction from an unlabeled body of text

summarybox works using the TextRank algorithm combined with word2vec vectors in
order to calculate similarity between words. The concept of the approach here
is that we try to find the words which are the most similar to all of the other
words in the corpus. We model these relationships as a graph, then walk the
graph in order to score words of relative importance.

## Getting Started

These instructions will get you a copy of the project up and running on your
local machine for development and testing purposes. See deployment for notes on
how to deploy the project on a live system.

```
git clone https://github.com/abatilo/summarybox.git
cd summarybox
wget https://s3.amazonaws.com/dl4j-distribution/GoogleNews-vectors-negative300.bin.gz
```

summarybox is a Dropwizard service which will use the `local.yaml` to load in
configurations.

```
# The OpenNLP model for sentence detection
sentenceModel: en-sent.bin
# The OpenNLP model for part of speech tagging
posModel: en-pos-perceptron.bin
# A word2vec model. By default it's expected to have the binary version of a model
w2vModel: GoogleNews-vectors-negative300.bin.gz
# A list of stopwords to filter out
stopWords: stopwords.txt
# These are configurations for how the PageRank algorithm should behave
wordScanner:
  # Which POS tags will be allowed through our filter
  allowedTags:
    - NN
    - VB
```

### Prerequisites

Requires Java 8 to be installed.

## Built With

* [Dropwizard](http://www.dropwizard.io/1.1.4/docs/) - The web framework used
* [Guava](https://github.com/google/guava/wiki/Release23) - Utility functions
* [OpenNLP](https://opennlp.apache.org/docs/1.8.2/manual/opennlp.html) - NLP library for doing text processing
* [Deeplearning4j](https://deeplearning4j.org/overview) - For Word2Vec implementation
* [Lombok](https://projectlombok.org/) - Annotations for less boilerplate code

## Contributing

Fork the project and submit a PR and one of the maintainers will be in touch.

## Authors

* Aaron Batilo - Developer / maintainer - [abatilo](https://github.com/abatilo)

See also the list of [contributors](https://github.com/abatilo/summarybox/contributors) who participated in this project.

## License

This project is licensed under the MIT License - see the [LICENSE.md](LICENSE.md) file for details

## Acknowledgments

* [Explanation of TextRank](https://www.quora.com/What-is-a-simple-but-detailed-explanation-of-Textrank)
* [Explanation of PageRank](https://youtu.be/v7n7wZhHJj8)

## Notes
An informal development log was kept while working on this project and can be found here:
* [Link to Evernote](https://www.evernote.com/shard/s74/sh/520a9aec-cddf-4e52-b4d5-e6ea29c2d266/e3d272e53b44da996ff7d0e4ddaed053)
