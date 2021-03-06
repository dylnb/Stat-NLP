package nlp.assignments;


import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Collection;
import java.util.ArrayList;
import java.util.Arrays;

import nlp.assignments.MaximumEntropyClassifier.EncodedDatum;
import nlp.assignments.MaximumEntropyClassifier.Encoding;
import nlp.assignments.MaximumEntropyClassifier.IndexLinearizer;
import nlp.classify.*;
import nlp.math.DifferentiableFunction;
import nlp.math.DoubleArrays;
import nlp.math.GradientMinimizer;
import nlp.math.LBFGSMinimizer;
import nlp.util.Counter;
import nlp.util.Indexer;
import nlp.util.Pair;

/**
 * Maximum entropy classifier for assignment 2.  You will have to fill in the code gaps marked by TODO flags.  To test
 * whether your classifier is functioning correctly, you can invoke the main method of this class using
 * <p/>
 * java edu.berkeley.nlp.assignments.PerceptronClassifier
 * <p/>
 * This will run a toy test classification.
 */
public class PerceptronClassifier <I,F,L> implements ProbabilisticClassifier<I, L> {

  /**
   * Factory for training PerceptronClassifiers.
   */
  public static class Factory <I,F,L> implements ProbabilisticClassifierFactory<I,L> {

    double sigma;
    int iterations;
    FeatureExtractor<I, F> featureExtractor;

    public ProbabilisticClassifier<I, L> trainClassifier(List<LabeledInstance<I, L>> trainingData) {
      // build data encodings so the inner loops can be efficient
      Encoding<F, L> encoding = buildEncoding(trainingData);
      IndexLinearizer indexLinearizer = buildIndexLinearizer(encoding);
      double[] initialWeights = buildInitialWeights(indexLinearizer);
      EncodedDatum[] data = encodeData(trainingData, encoding);
      // learn our voting weights
      double[] weights = null;
			try {
				weights = learnWeights(initialWeights, encoding, data, indexLinearizer);
			}
			catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
      // build a classifer using these weights (and the data encodings)
      return new PerceptronClassifier<I,F,L>(weights, encoding, indexLinearizer, featureExtractor);
    }

    private double[] buildInitialWeights(IndexLinearizer indexLinearizer) {
      return DoubleArrays.constantArray(0.0, indexLinearizer.getNumLinearIndexes());
    }

    private IndexLinearizer buildIndexLinearizer(Encoding<F, L> encoding) {
      return new IndexLinearizer(encoding.getNumFeatures(), encoding.getNumLabels());
    }

    private Encoding<F, L> buildEncoding(List<LabeledInstance<I,L>> data) {
      Indexer<F> featureIndexer = new Indexer<F>();
      Indexer<L> labelIndexer = new Indexer<L>();
      for (LabeledInstance<I,L> labeledInstance : data) {
        L label = labeledInstance.getLabel();
        Counter<F> features = featureExtractor.extractFeatures(labeledInstance.getInput());
        LabeledFeatureVector<F,L> labeledDatum = new BasicLabeledFeatureVector<F,L>(label, features);
        labelIndexer.add(labeledDatum.getLabel());
        for (F feature : labeledDatum.getFeatures().keySet()) {
          featureIndexer.add(feature);
        }
      }
      return new Encoding<F, L>(featureIndexer, labelIndexer);
    }

    private EncodedDatum[] encodeData(List<LabeledInstance<I,L>> data, Encoding<F, L> encoding) {
      EncodedDatum[] encodedData = new EncodedDatum[data.size()];
      for (int i = 0; i < data.size(); i++) {
        LabeledInstance<I,L> labeledInstance = data.get(i);
        L label = labeledInstance.getLabel();
        Counter<F> features = featureExtractor.extractFeatures(labeledInstance.getInput());
        LabeledFeatureVector<F,L> labeledFeatureVector = new BasicLabeledFeatureVector<F, L>(label, features);
        encodedData[i] = EncodedDatum.encodeLabeledDatum(labeledFeatureVector, encoding);
      }
      return encodedData;
    }
    
    private double[] learnWeights(double[] weights, Encoding<F, L> encoding, EncodedDatum[] data, IndexLinearizer iL) throws IOException {
      FileWriter fstream = new FileWriter("/Users/bumford/Desktop/out.txt");
      BufferedWriter out = new BufferedWriter(fstream);
      
      List<EncodedDatum> datumList = Arrays.asList(data);
      Collections.shuffle(datumList);
    	
    	for (EncodedDatum datum : datumList) {
	    	int gold_label = datum.getLabelIndex();
	    	Counter<Integer> scores = getScores(datum, weights, encoding, iL);
	    	out.write("gold: "+encoding.getLabel(gold_label)+", ");
//	    	System.out.println(scores);
	    	out.write("scores: "+scores + ", ");
	    	Integer response_label = scores.argMax();
	    	out.write("response: "+encoding.getLabel(response_label) + "\n");
	    	if (gold_label != response_label) {
	    		out.write("false\n");
	    		for (int i=0; i < datum.getNumActiveFeatures(); i++) {
	    			int goldWeightIndex = iL.getLinearIndex(datum.getFeatureIndex(i), gold_label);
	    			weights[goldWeightIndex] += datum.getFeatureCount(i);
	    			int responseWeightIndex = iL.getLinearIndex(datum.getFeatureIndex(i), response_label);
	    			weights[responseWeightIndex] -= datum.getFeatureCount(i);
	    		}
	    	}
//	    	System.out.println(Arrays.toString(weights));
	  	}
    	out.close();
	    return weights;
    }
    
    public Counter<Integer> getScores(EncodedDatum datum, double[] weights, Encoding<F, L> encoding, IndexLinearizer iL) {
    	Counter<Integer> scores = new Counter<Integer>();
      for (L label : encoding.getLabels()) {
      	int labelIndex = encoding.getLabelIndex(label);
      	List<Integer> relevantWeightIndexes = getWeightIndexes(labelIndex, datum, encoding, iL);
      	double[] relevantWeights = getWeights(relevantWeightIndexes, weights);

//  	    System.out.println(relevantWeightIndexes);
  	    double score = DoubleArrays.innerProduct(datum.featureCounts, relevantWeights);
//  	    System.out.println(score);
  	    scores.setCount(labelIndex, score);
      }
      return scores;
    }
    
    public List<Integer> getWeightIndexes(int labelIndex, EncodedDatum datum, Encoding<F,L> encoding, IndexLinearizer iL) {
    	List<Integer> relevantWeightIndexes = new ArrayList<Integer>();
    	for (int featureIndex : datum.featureIndexes) {
    		relevantWeightIndexes.add(iL.getLinearIndex(featureIndex, labelIndex));
    	}
    	return relevantWeightIndexes;
    }
    
    public double[] getWeights(List<Integer> relevantWeightIndexes, double[] weights) {
	    double[] relevantWeights = new double[relevantWeightIndexes.size()];
	    int counter = 0;
	    for (int relevantIndex : relevantWeightIndexes) {
	    	relevantWeights[counter] = (weights[relevantIndex]);
	    	counter++;
	    }
	    return relevantWeights;
    }

    public Factory(FeatureExtractor<I,F> featureExtractor) {
      this.featureExtractor = featureExtractor;
    }
  }

//  /**
//   * This is the MaximumEntropy objective function: the (negative) log conditional likelihood of the training data,
//   * possibly with a penalty for large weights.  Note that this objective get MINIMIZED so it's the negative of the
//   * objective we normally think of.
//   */
//  public static class ObjectiveFunction <F,L> implements DifferentiableFunction {
//    IndexLinearizer indexLinearizer;
//    Encoding<F, L> encoding;
//    EncodedDatum[] data;
//
//    double sigma;
//
//    double lastValue;
//    double[] lastDerivative;
//    double[] lastX;
//
//    public int dimension() {
//      return indexLinearizer.getNumLinearIndexes();
//    }
//
//    public double valueAt(double[] x) {
//      ensureCache(x);
//      return lastValue;
//    }
//
//    public double[] derivativeAt(double[] x) {
//      ensureCache(x);
//      return lastDerivative;
//    }
//
//    private void ensureCache(double[] x) {
//      if (requiresUpdate(lastX, x)) {
//        Pair<Double, double[]> currentValueAndDerivative = calculate(x);
//        lastValue = currentValueAndDerivative.getFirst();
//        lastDerivative = currentValueAndDerivative.getSecond();
//        lastX = x;
//      }
//    }
//
//    private boolean requiresUpdate(double[] lastX, double[] x) {
//      if (lastX == null) return true;
//      for (int i = 0; i < x.length; i++) {
//        if (lastX[i] != x[i])
//          return true;
//      }
//      return false;
//    }
//
//    /**
//     * The most important part of the classifier learning process!  This method determines, for the given weight vector
//     * x, what the (negative) log conditional likelihood of the data is, as well as the derivatives of that likelihood
//     * wrt each weight parameter.
//     */
//    private Pair<Double, double[]> calculate(double[] x) {
//      double objective = 0.0;
//      double[] derivatives = DoubleArrays.constantArray(0.0, dimension());
//      
//      // compute the objective and its derivatives
//      
//      for (EncodedDatum datum : data) {
//      	int labelIndex = datum.getLabelIndex();
//      	objective += getLogProbabilities(datum, x, encoding, indexLinearizer)[labelIndex];
//      }
//      
////      System.out.println("objective: " + objective);
//
//      // logProb
//       
//      for (L y : encoding.getLabels()) {
//      	int loop_labelIndex = encoding.getLabelIndex(y);
//      	for (EncodedDatum datum : data) {
//      		double[] partialVector = DoubleArrays.constantArray(0.0, datum.getNumActiveFeatures());
//      		int gold_labelIndex = datum.getLabelIndex();
//      		if (gold_labelIndex == loop_labelIndex) {
//      			partialVector = DoubleArrays.add(partialVector, datum.featureCounts);
//      		}
//    			double prob_term = Math.exp(getLogProbabilities(datum, x, encoding, indexLinearizer)[loop_labelIndex]);
//    			double[] subtraction_term = DoubleArrays.multiply(datum.featureCounts, prob_term);
//    			partialVector = DoubleArrays.subtract(partialVector, subtraction_term);
//    			for (int i=0; i < datum.getNumActiveFeatures(); i++) {
//    				int d_index = indexLinearizer.getLinearIndex(datum.getFeatureIndex(i), loop_labelIndex);
//    				derivatives[d_index] += partialVector[i];
//    			}
//      	}
//      }
//
//      // incorporate penalty terms into the objective and derivatives penalties
//      
//      objective -= 0.5 * Math.pow(DoubleArrays.vectorLength(x) / sigma, 2);
//      DoubleArrays.add(derivatives, DoubleArrays.multiply(x, - 1 / Math.pow(sigma, 2)));
//
//      return new Pair<Double, double[]>(-objective, DoubleArrays.multiply(derivatives, -1));
//    }
//
//    public ObjectiveFunction(Encoding<F, L> encoding, EncodedDatum[] data, IndexLinearizer indexLinearizer, double sigma) {
//      this.indexLinearizer = indexLinearizer;
//      this.encoding = encoding;
//      this.data = data;
//      this.sigma = sigma;
//    }
//  }

  /**
   * EncodedDatums are sparse representations of (labeled) feature count vectors for a given data point.  Use
   * getNumActiveFeatures() to see how many features have non-zero count in a datum.  Then, use getFeatureIndex() and
   * getFeatureCount() to retreive the number and count of each non-zero feature.  Use getLabelIndex() to get the
   * label's number.
   */
  public static class EncodedDatum {

    public static <F,L> EncodedDatum encodeDatum(FeatureVector<F> featureVector, Encoding<F, L> encoding) {
      Counter<F> features = featureVector.getFeatures();
      Counter<F> knownFeatures = new Counter<F>();
      for (F feature : features.keySet()) {
        if (encoding.getFeatureIndex(feature) < 0)
          continue;
        knownFeatures.incrementCount(feature, features.getCount(feature));
      }
      int numActiveFeatures = knownFeatures.keySet().size();
      int[] featureIndexes = new int[numActiveFeatures];
      double[] featureCounts = new double[knownFeatures.keySet().size()];
      int i = 0;
      for (F feature : knownFeatures.keySet()) {
        int index = encoding.getFeatureIndex(feature);
        double count = knownFeatures.getCount(feature);
        featureIndexes[i] = index;
        featureCounts[i] = count;
        i++;
      }
      EncodedDatum encodedDatum = new EncodedDatum(-1, featureIndexes, featureCounts);
      return encodedDatum;
    }

    public static <F,L> EncodedDatum encodeLabeledDatum(LabeledFeatureVector<F, L> labeledDatum, Encoding<F, L> encoding) {
      EncodedDatum encodedDatum = encodeDatum(labeledDatum, encoding);
      encodedDatum.labelIndex = encoding.getLabelIndex(labeledDatum.getLabel());
      return encodedDatum;
    }

    int labelIndex;
    int[] featureIndexes;
    double[] featureCounts;

    public int getLabelIndex() {
      return labelIndex;
    }

    public int getNumActiveFeatures() {
      return featureCounts.length;
    }

    public int getFeatureIndex(int num) {
      return featureIndexes[num];
    }

    public double getFeatureCount(int num) {
      return featureCounts[num];
    }

    public EncodedDatum(int labelIndex, int[] featureIndexes, double[] featureCounts) {
      this.labelIndex = labelIndex;
      this.featureIndexes = featureIndexes;
      this.featureCounts = featureCounts;
    }
  }

  /**
   * The Encoding maintains correspondences between the various representions of the data, labels, and features.  The
   * external representations of labels and features are object-based.  The functions getLabelIndex() and
   * getFeatureIndex() can be used to translate those objects to integer representatiosn: numbers between 0 and
   * getNumLabels() or getNumFeatures() (exclusive).  The inverses of this map are the getLabel() and getFeature()
   * functions.
   */
  public static class Encoding <F,L> {
    Indexer<F> featureIndexer;
    Indexer<L> labelIndexer;

    public int getNumFeatures() {
      return featureIndexer.size();
    }

    public int getFeatureIndex(F feature) {
      return featureIndexer.indexOf(feature);
    }

    public F getFeature(int featureIndex) {
      return featureIndexer.get(featureIndex);
    }

    public int getNumLabels() {
      return labelIndexer.size();
    }

    public int getLabelIndex(L label) {
      return labelIndexer.indexOf(label);
    }

    public L getLabel(int labelIndex) {
      return labelIndexer.get(labelIndex);
    }
    
    public List<L> getLabels() {
    	List<L> labelList = new ArrayList<L>();
    	for (int i=0; i < labelIndexer.size(); i++) {
    		labelList.add(getLabel(i));
    	}
    	return labelList;
    }

    public Encoding(Indexer<F> featureIndexer, Indexer<L> labelIndexer) {
      this.featureIndexer = featureIndexer;
      this.labelIndexer = labelIndexer;
    }
  }

  /**
   * The IndexLinearizer maintains the linearization of the two-dimensional features-by-labels pair space.  This is
   * because, while we might think about lambdas and derivatives as being indexed by a feature-label pair, the
   * optimization code expects one long vector for lambdas and derivatives.  To go from a pair featureIndex, labelIndex
   * to a single pairIndex, use getLinearIndex().
   */
  public static class IndexLinearizer {
    int numFeatures;
    int numLabels;

    public int getNumLinearIndexes() {
      return numFeatures * numLabels;
    }

    public int getLinearIndex(int featureIndex, int labelIndex) {
      return labelIndex + featureIndex * numLabels;
    }

    public int getFeatureIndex(int linearIndex) {
      return linearIndex / numLabels;
    }

    public int getLabelIndex(int linearIndex) {
      return linearIndex % numLabels;
    }

    public IndexLinearizer(int numFeatures, int numLabels) {
      this.numFeatures = numFeatures;
      this.numLabels = numLabels;
    }
  }


  private double[] weights;
  private Encoding<F,L> encoding;
  private IndexLinearizer indexLinearizer;
  private FeatureExtractor<I,F> featureExtractor;

  /**
   * Calculate the log probabilities of each class, for the given datum (feature bundle).  Note that the weighted votes
   * (refered to as activations) are *almost* log probabilities, but need to be normalized.
   */
  private static <F,L> double[] getLogProbabilities(EncodedDatum datum, double[] weights, Encoding<F, L> encoding, IndexLinearizer indexLinearizer) {
  	
//  	System.out.println(Arrays.toString(datum.featureIndexes));
//  	System.out.println(Arrays.toString(datum.featureCounts));
//  	System.out.println(encoding.getNumFeatures());
  	
  	Counter<Integer> scores = new Counter<Integer>();
  	
    for (L label : encoding.getLabels()) {
    	int labelIndex = encoding.getLabelIndex(label);
    	List<Integer> relevantWeightIndexes = new ArrayList<Integer>();
    	for (int featureIndex : datum.featureIndexes) {
    		relevantWeightIndexes.add(indexLinearizer.getLinearIndex(featureIndex, labelIndex));
    	}
    
	    double[] relevantWeights = new double[relevantWeightIndexes.size()];
	    int counter = 0;
	    for (int relevantIndex : relevantWeightIndexes) {
	    	relevantWeights[counter] = (weights[relevantIndex]);
	    	counter++;
	    }
//	    System.out.println(relevantWeightIndexes);
	    double score = DoubleArrays.innerProduct(datum.featureCounts, relevantWeights);
//	    System.out.println(score);
	    scores.setCount(labelIndex, score);
    }
    
    double denom_term = 0.0;
    for (L label : encoding.getLabels()) {
    	int labelIndex = encoding.getLabelIndex(label);
    	denom_term += Math.exp(scores.getCount(labelIndex));
    }
    
    double[] logProbabilities = DoubleArrays.constantArray(Double.NEGATIVE_INFINITY, encoding.getNumLabels());
    for (L label : encoding.getLabels()) {
    	int labelIndex = encoding.getLabelIndex(label);
    	double logprob = scores.getCount(labelIndex) - Math.log(denom_term);
    	logProbabilities[labelIndex] = logprob;
    }
    
    return logProbabilities;

//    // dummy code
//    double[] logProbabilities = DoubleArrays.constantArray(Double.NEGATIVE_INFINITY, encoding.getNumLabels());
//    logProbabilities[0] = 0.0;
//    return logProbabilities;
//    // end dummy code


  }

  public Counter<L> getProbabilities(I input) {
    FeatureVector<F> featureVector = new BasicFeatureVector<F>(featureExtractor.extractFeatures(input));
    return getProbabilities(featureVector);
  }

  private Counter<L> getProbabilities(FeatureVector<F> featureVector) {
    EncodedDatum encodedDatum = EncodedDatum.encodeDatum(featureVector, encoding);
    double[] logProbabilities = getLogProbabilities(encodedDatum, weights, encoding, indexLinearizer);
    return logProbabiltyArrayToProbabiltyCounter(logProbabilities);
  }

  private Counter<L> logProbabiltyArrayToProbabiltyCounter(double[] logProbabilities) {
    Counter<L> probabiltyCounter = new Counter<L>();
    for (int labelIndex = 0; labelIndex < logProbabilities.length; labelIndex++) {
      double logProbability = logProbabilities[labelIndex];
      double probability = Math.exp(logProbability);
      L label = encoding.getLabel(labelIndex);
      probabiltyCounter.setCount(label, probability);
    }
    return probabiltyCounter;
  }

  public L getLabel(I input) {
    return getProbabilities(input).argMax();
  }

  public PerceptronClassifier(double[] weights, Encoding<F, L> encoding, IndexLinearizer indexLinearizer, FeatureExtractor<I,F> featureExtractor) {
    this.weights = weights;
    this.encoding = encoding;
    this.indexLinearizer = indexLinearizer;
    this.featureExtractor = featureExtractor;
  }

  public static void main(String[] args) {
    // create datums
    LabeledInstance<String[], String> datum1 = new LabeledInstance<String[], String>("cat", new String[]{"fuzzy", "claws", "small"});
    LabeledInstance<String[], String> datum2 = new LabeledInstance<String[], String>("bear", new String[]{"fuzzy", "claws", "big"});
    LabeledInstance<String[], String> datum3 = new LabeledInstance<String[], String>("cat", new String[]{"claws", "medium"});
    LabeledInstance<String[], String> datum4 = new LabeledInstance<String[], String>("cat", new String[]{"claws", "small"});

    // create training set
    List<LabeledInstance<String[], String>> trainingData = new ArrayList<LabeledInstance<String[], String>>();
    trainingData.add(datum1);
    trainingData.add(datum2);
    trainingData.add(datum3);

    // create test set
    List<LabeledInstance<String[], String>> testData = new ArrayList<LabeledInstance<String[], String>>();
    testData.add(datum4);

    // build classifier
    FeatureExtractor<String[], String> featureExtractor = new FeatureExtractor<String[], String>() {
      public Counter<String> extractFeatures(String[] featureArray) {
        return new Counter<String>(Arrays.asList(featureArray));
      }
    };
    PerceptronClassifier.Factory<String[], String, String> perceptronClassifierFactory = new PerceptronClassifier.Factory<String[], String, String>(featureExtractor);
    ProbabilisticClassifier<String[], String> perceptronClassifier = perceptronClassifierFactory.trainClassifier(trainingData);
    System.out.println("Probabilities on test instance: " + perceptronClassifier.getProbabilities(datum4.getInput()));
  }
}
