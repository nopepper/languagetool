/* LanguageTool, a natural language style checker
 * Copyright (C) 2020 LanguageTooler GmbH
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301
 * USA
 */
package org.languagetool.language;

import org.languagetool.noop.NoopLanguage;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;

import static java.lang.StrictMath.*;
import static org.languagetool.language.LanguageIdentifier.canLanguageBeDetected;

public class NGramLangIdentifier {

  private final static double EPSILON = 1e-6;

  private final Map<String, Integer> vocab;
  protected final List<String[]> codes; // Elem format = {Name, 2-code (or "NULL"), 3-code}
  private final List<Integer> specialTokens;

  private final List<Map<String, Double>> knpBigramProbs;

  public final int maxLength;
  private final ZipFile zipFile;

  public NGramLangIdentifier(File sourceModelZip, int maxLength) throws IOException {
    if (maxLength < 1) {
      throw new IllegalArgumentException("maxLength must be at least 1");
    }

    this.maxLength = maxLength;
    this.zipFile = new ZipFile(sourceModelZip);

    //Load language codes - Line format = {Language Name}\t{2-code or "NULL"}\t{3-code}
    codes = new ArrayList<>();
    try (BufferedReader br = getReader("iso_codes.tsv")) {
      String line;
      while ((line = br.readLine()) != null) {
        String[] values = line.split("\t");
        if (values[3].equals("1")) {
          codes.add(values);
        }
      }
    }

    //Load vocab - Line format = {token}
    vocab = new HashMap<>();
    try (BufferedReader br = getReader("vocab.txt")) {
      String line;
      int i = 0;
      while ((line = br.readLine()) != null) {
        vocab.put(line.split("\t")[0].trim(), i);
        i++;
      }
    }

    specialTokens = new ArrayList<>();
    specialTokens.add(vocab.get("<unk>"));
    specialTokens.add(vocab.get("<s>"));
    specialTokens.add(vocab.get("<NUM>"));

    //Load transition matrices - Line format = {i} {j} {val}
    knpBigramProbs = expectedFiles().stream().map(this::readLines).parallel().map(NGramLangIdentifier::loadDict).collect(Collectors.toList());
  }

  public List<Double> predict(String text) {
    List<Double> finalProbs = new ArrayList<>();
    List<int[]> keys = keys(encode(text));

    for (int i = 0; i < codes.size(); i++) {
      double val = 0;
      for (int[] key: keys) {
        double prob = knpBigramProbs.get(i).getOrDefault(key[0] + "_" + key[1], EPSILON);
        if(specialTokens.contains(key[0]) && specialTokens.contains(key[1])) {
          prob = EPSILON;
        }
        val += log(prob);
      }
      finalProbs.add(val);
    }
    return finalProbs;
  }

  public Map<String, Double> detectLanguages(String text, List<String> additionalLanguageCodes) {
    if (additionalLanguageCodes == null) {
      additionalLanguageCodes = new ArrayList<>();
    }

    List<Double> finalProbs = predict(text);

    Map<String, Double> result = new HashMap<>();

    finalProbs = finalProbs.stream().map(StrictMath::exp).collect(Collectors.toList());
    finalProbs = normalize(finalProbs);
    for (int i = 0; i < codes.size(); i++) {
      String langCode = codes.get(i)[1].equals("NULL") ? codes.get(i)[2] : codes.get(i)[1]; //2-character code if possible
      if (canLanguageBeDetected(langCode, additionalLanguageCodes)) {
        result.put(langCode, finalProbs.get(i));
      }
    }

    //System.out.println("ngram result: " + result);
    return result;
  }

  private BufferedReader getReader(String fileName) throws IOException {
    InputStream is = this.zipFile.getInputStream(this.zipFile.getEntry(fileName));
    InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
    return new BufferedReader(isr);
  }

  private List<String> readLines(String path) {
    ArrayList<String> result = new ArrayList<>();
    try {
      BufferedReader br = getReader(path);
      String line;
      while ((line = br.readLine()) != null) {
        result.add(line);
      }
    } catch(java.io.IOException e) {
      throw new RuntimeException(e);
    }
    return result;
  }

  private static Map<String, Double> loadDict(List<String> lines)  {
    Map<String, Double> tm = new HashMap<>();
    for(String line : lines) {
      String[] parts = line.trim().split(" ");
      String key = String.join("_", Arrays.copyOfRange(parts, 0, parts.length-1));
      tm.put(key, Double.parseDouble(parts[parts.length-1]));
    }
    return tm;
  }

  private List<String> expectedFiles() {
    List<String> result = new ArrayList<>();
    for (int i = 0; i < codes.size(); i++) {
      String name = String.format("%02d.txt", i);
      result.add(name);
    }
    return result;
  }

  private final Pattern PUNCT = Pattern.compile("[\\u2000-\\u206F\\u2E00-\\u2E7F'!\"#$%&()*+,\\-./:;<=>?@\\[\\]^_`{|}~]+", Pattern.UNICODE_CHARACTER_CLASS);
  private final Pattern NUM = Pattern.compile("\\d+(\\s*\\d+)*", Pattern.UNICODE_CHARACTER_CLASS);
  private final Pattern KO = Pattern.compile("[\\uac00-\\ud7a3]", Pattern.UNICODE_CHARACTER_CLASS);
  private final Pattern JA = Pattern.compile("[\\u3040-\\u30ff]", Pattern.UNICODE_CHARACTER_CLASS);
  private final Pattern ZH = Pattern.compile("[\\u4e00-\\u9FFF]", Pattern.UNICODE_CHARACTER_CLASS);
  private final Pattern KM = Pattern.compile("[\\u1780-\\u17FF]", Pattern.UNICODE_CHARACTER_CLASS);
  private final Pattern TL = Pattern.compile("[\\u1700-\\u171F]", Pattern.UNICODE_CHARACTER_CLASS);
  private final Pattern HY = Pattern.compile("[\\u0530-\\u058F]", Pattern.UNICODE_CHARACTER_CLASS);
  private final Pattern EL = Pattern.compile("[\\u0370-\\u03FF]", Pattern.UNICODE_CHARACTER_CLASS);
  private final Pattern TA = Pattern.compile("[\\u0B80-\\u0BFF]", Pattern.UNICODE_CHARACTER_CLASS);
  private final Pattern SPACE = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);

  public List<Integer> encode(String text) {
    List<Integer> result = new ArrayList<>();
    result.add(1); //Start of sentence token
    if (text.length() > maxLength) {
      text = text.substring(0, maxLength);
    }
    text = Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase();
    text = PUNCT.matcher(text).replaceAll(" ");
    text = text.trim();
    text = NUM.matcher(text).replaceAll("<NUM>");
    text = KO.matcher(text).replaceAll("<KO>");
    text = JA.matcher(text).replaceAll("<JA>");
    text = ZH.matcher(text).replaceAll("<ZH>");
    text = KM.matcher(text).replaceAll("<KM>");
    text = TL.matcher(text).replaceAll("<TL>");
    text = HY.matcher(text).replaceAll("<HY>");
    text = EL.matcher(text).replaceAll("<EL>");
    text = TA.matcher(text).replaceAll("<TA>");
    text = SPACE.matcher(text).replaceAll("▁");
    if (text.length() == 0) {
      return result;
    }
    text = "▁" + text;
    List<String> toks = new ArrayList<>();
    int cur = 0;
    while (cur < text.length()) {
      String tok = "<unk>";
      int ci = 1;
      for (int i = cur + 1; i <= text.length(); i++) {
        if (vocab.containsKey(text.substring(cur, i))) {
          tok = text.substring(cur, i);
          ci = i - cur;
        }
        else if (text.substring(cur, i).startsWith("▁<") && vocab.containsKey(text.substring(cur + 1, i))){
          tok = text.substring(cur + 1, i);
          ci = tok.length() + 1;
        }
        else if (text.charAt(i-1) == '▁'){
          break;
        }
      }
      cur += ci;
      toks.add(tok);
    }

    List<List<String>> toks2d = new ArrayList<>();
    List<String> temp = new ArrayList<>();
    for (String tok : toks) {
      if (tok.charAt(0) == '▁' || (tok.charAt(0) == '<' && tok.charAt(tok.length()-1) == '>')){
        if (temp.size() > 0){
          toks2d.add(temp);
          temp = new ArrayList<>();
        }
      }
      temp.add(tok);
    }
    if (temp.size() > 0) {
      toks2d.add(temp);
    }
    toks.clear();
    for (List<String> arr : toks2d) {
      int wordlen = arr.stream().mapToInt(String::length).sum() - 1; //Always > 0
      if (wordlen > 1 && toks2d.size() > 2 && arr.size() == wordlen){
        arr.clear();
        arr.add("<unk>");
      }
      toks.addAll(arr);
    }
    result.addAll(toks.stream().mapToInt(vocab::get).boxed().collect(Collectors.toList()));
    return result;
  }

  private List<int[]> keys(List<Integer> enc) {
    //For now just bigrams
    List<int[]> result = new ArrayList<>();
    for (int i = 1; i < enc.size(); i++) {
      result.add(new int[]{enc.get(i-1), enc.get(i)});
    }
    return result;
  }

  private List<Double> normalize(List<Double> vals) {
    double tot = vals.stream().mapToDouble(f -> f).sum();
    return vals.stream().map(n -> n/tot).collect(Collectors.toList());
  }
}
