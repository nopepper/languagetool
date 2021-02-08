package org.languagetool.language;

import org.junit.Before;
import org.junit.Test;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;

import static org.junit.Assert.*;
import static org.junit.Assert.assertTrue;

public class NGramLangIdTest {
  public NGramLangIdTest() {
  }

  private class ProcessedSentence {
    public final String sentence;
    public final List<Integer> encoding;
    public final List<Double> pred;

    public ProcessedSentence(String part1, String part2, String part3) {
     sentence = part1;
     encoding = Arrays.stream(part2.split(" ")).map(Integer::parseInt).collect(Collectors.toList());
     pred = Arrays.stream(part3.split(" ")).map(Double::parseDouble).collect(Collectors.toList());
    }
  }
  //Make sure classpath is languagetool-core
  private final File TESTER_MODEL = new File("C:\\Users\\Robert\\Desktop\\ngram-language-identification\\model_cur\\model_cur.zip");
  private final File TESTER_DATA = new File("C:\\Users\\Robert\\Desktop\\ngram-language-identification\\testing_model\\data\\data.zip");

  private NGramLangIdentifier loadedModel;
  private List<ProcessedSentence> encPredTestItems;
  private List<String> engSentences;
  private List<String> deuSentences;
  private List<String> fraSentences;
  private List<String> nldSentences;
  private List<String> spaSentences;

  private List<String> loadLines(InputStream is) throws IOException {
    InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
    BufferedReader br = new BufferedReader(isr);
    List<String> result = new ArrayList<>();
    String line;
    while ((line = br.readLine()) != null) {
      result.add(line);
    }
    return result;
  }

  @Before
  public void loadVariables() throws IOException {
    loadedModel = new NGramLangIdentifier(TESTER_MODEL, 50);

    ZipFile testerDataZip = new ZipFile(TESTER_DATA);
    InputStream is = testerDataZip.getInputStream(testerDataZip.getEntry("enc_pred_test.txt"));
    InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
    BufferedReader br = new BufferedReader(isr);

    encPredTestItems = new ArrayList<>();
    String line;
    List<String> lines = new ArrayList<>();
    while ((line = br.readLine()) != null) {
      lines.add(line);
      if(lines.size() == 3) {
        encPredTestItems.add(new ProcessedSentence(lines.get(0), lines.get(1), lines.get(2)));
        lines.clear();
      }
    }
    engSentences = loadLines(testerDataZip.getInputStream(testerDataZip.getEntry("eng.txt")));
    deuSentences = loadLines(testerDataZip.getInputStream(testerDataZip.getEntry("deu.txt")));
    fraSentences = loadLines(testerDataZip.getInputStream(testerDataZip.getEntry("fra.txt")));
    nldSentences = loadLines(testerDataZip.getInputStream(testerDataZip.getEntry("nld.txt")));
    spaSentences = loadLines(testerDataZip.getInputStream(testerDataZip.getEntry("spa.txt")));
  }

  @Test
  public void testModelCompletenessCheck() {
    //Make sure model checks for completeness
    try {
      new NGramLangIdentifier(null, 50);
      fail();
    }
    catch (Exception e) { }
    try {
      new NGramLangIdentifier(TESTER_DATA, 50);
      fail();
    }
    catch (Exception e) { }
    try {
      new NGramLangIdentifier(TESTER_MODEL, 0);
      fail();
    }
    catch (Exception e) { }
  }

  @Test
  public void testLoadSpeed() throws IOException {
    //Make sure the model takes a reasonable time to load
    long zipSizeKb = TESTER_MODEL.length() / 1024;

    long start = System.nanoTime();
    new NGramLangIdentifier(TESTER_MODEL, 50);
    long loadTimeMs = (System.nanoTime() - start) / 1000000;
    double msPerKb = (double) loadTimeMs / zipSizeKb;
    if(msPerKb > 0.2) {
      System.out.println("Tiny ngram model seems to be loading too slowly: " + msPerKb + "ms per kb");
    }
    if(loadTimeMs > 500) {
      System.out.println("Tiny ngram model took more than 500ms to load!");
    }
  }

  @Test
  public void testEncoding() {
    // Make sure the encoding is the same as in Python
    for (ProcessedSentence p : encPredTestItems) {
      assertEquals(p.encoding, loadedModel.encode(p.sentence));
    }
  }

  @Test
  public void testPrediction() {
    //Make sure predictions match Python results
    for (ProcessedSentence p : encPredTestItems) {
      List<Double> modelPred = loadedModel.predict(p.sentence);
      assertEquals(modelPred.size(), p.pred.size());
      for(int i = 0; i < modelPred.size(); i++) {
        // If this fails, make sure the epsilon values match
        assertTrue(Math.abs(modelPred.get(i) - p.pred.get(i)) < 0.1); //Rounding threshold
      }
    }
  }

  private boolean matchesExpected(String sent, String expectedLang, int maxLen) {
    int expectedArgMax = -1;
    for (int i = 0; i < loadedModel.codes.size(); i++) {
      if (loadedModel.codes.get(i)[2].equals(expectedLang)) {
        expectedArgMax = i;
        break;
      }
    }
    assertTrue(expectedArgMax >= 0);
    if(sent.length() > maxLen){
      sent = sent.substring(0, maxLen);
    }
    List<Double> pred = loadedModel.predict(sent);
    return pred.stream().max(Comparator.naturalOrder()).get().equals(pred.get(expectedArgMax));
  }

  private double getAccuracyPercentage(List<String> inputs, String expectedLang, int maxLen) {
    int correct = 0;
    for (String sent : inputs) {
      if(matchesExpected(sent, expectedLang, maxLen)){
        correct += 1;
      }
    }
    double result = 100.0 * correct / inputs.size();
    System.out.println("Accuracy for " + expectedLang + " length " + maxLen + ": " + result);
    return result;
  }

  private double getRobustnessScore(List<String> inputs, String expectedLang) {
    List<Double> results = new ArrayList<>();
    for (String sent: inputs) {
      double mistakes = 0;
      for (int i = 10; i <= loadedModel.maxLength; i++) {
        if (!matchesExpected(sent, expectedLang, i)) {
          mistakes += 1;
        }
      }
      //double possibleMistakes = 0.5 * (loadedModel.maxLength * (loadedModel.maxLength + 1)) - 15;
      //results.add(1 - mistakes / possibleMistakes);
      results.add(1 - mistakes / (loadedModel.maxLength - 5));
    }
    double result = results.stream().reduce(Double::sum).get() / results.size();
    System.out.println("Robustness score for " + expectedLang + ": " + result);
    return result;
  }

  @Test
  public void testAccuracy() {
    //Match current best model at least

    assertTrue(getAccuracyPercentage(engSentences, "eng", 10) > 75);
    assertTrue(getAccuracyPercentage(engSentences, "eng", 50) > 95);

    assertTrue(getAccuracyPercentage(deuSentences, "deu", 10) > 75);
    assertTrue(getAccuracyPercentage(deuSentences, "deu", 50) > 95);

    assertTrue(getAccuracyPercentage(nldSentences, "nld", 10) > 75);
    assertTrue(getAccuracyPercentage(nldSentences, "nld", 50) > 95);

    assertTrue(getAccuracyPercentage(fraSentences, "fra", 10) > 75);
    assertTrue(getAccuracyPercentage(fraSentences, "fra", 50) > 95);

    assertTrue(getAccuracyPercentage(spaSentences, "spa", 10) > 70);
    assertTrue(getAccuracyPercentage(spaSentences, "spa", 50) > 93);
  }

  @Test
  public void testRobustness() {
    double eng = getRobustnessScore(engSentences, "eng");
    double deu = getRobustnessScore(deuSentences, "deu");
    double nld = getRobustnessScore(nldSentences, "nld");
    double fra = getRobustnessScore(fraSentences, "fra");
    double spa = getRobustnessScore(spaSentences, "spa");

    assert eng > 0.95;
    assert deu > 0.95;
    assert nld > 0.95;
    assert fra > 0.95;
    assert spa > 0.9;
  }
}
