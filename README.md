# summarybox
Extractive summarization, and keyword identification... as a service

## Download
We use Word2Vec through deeplearning4j to do keyword identification. At the moment, we use the pre-trained model from dl4j which is 1.5G and can be downloaded here:
https://s3.amazonaws.com/dl4j-distribution/GoogleNews-vectors-negative300.bin.gz

This should go into the `summarybox/sbtk/src/main/resources/` directory.
