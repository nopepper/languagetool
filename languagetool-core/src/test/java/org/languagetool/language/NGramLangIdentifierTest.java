package org.languagetool.language;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

//Can be used to manually run checks on the NGram language identifier and make sure it works in the live system
public class NGramLangIdentifierTest {

  private static final File ngramDir = new File("C:\\Users\\Robert\\Desktop\\ngram-language-identification\\model_compressed\\model_compressed.zip");
  private final NGramLangIdentifier ngram;
  private final boolean httpMode;
  private final static String CHECKING_URL = "http://localhost:8081/v2/check";

  public NGramLangIdentifierTest(boolean httpMode) throws IOException {
    this.httpMode = httpMode;
    if(!httpMode) {
      System.out.println("Loading ngrams...");
      this.ngram = new NGramLangIdentifier(ngramDir, 50);
      System.out.println("Loaded.");
    }
    else {
      System.out.println("Using HTTP server on " + CHECKING_URL);
      this.ngram = null;
    }
  }

  private void testChunk(List<String> sents, String expectedLang, boolean print_false, boolean print_false_if_zz) {
    int correct = 0;
    for(String sent: sents) {
      if(sent.length() > 50) {
        sent = sent.substring(0, 50);
      }
      String pred = getLang(sent);
      assert pred != null;
      if (!pred.equals(expectedLang)) {
        if(print_false || (print_false_if_zz && pred.equals("zz"))) {
          System.out.println("Expected " + expectedLang + " Predicted " + pred + " ---> " + sent);
        }
      }
      else {
        correct += 1;
      }
    }
    long perc = Math.round(((double)correct / sents.size()) * 100.0);
    System.out.println("Tested chunk for language \"" + expectedLang + "\" -> " + correct + " out of " + sents.size() + " correct (" + perc + "%)");
  }

  private void testThresholds(boolean print_false) {
    String[][] pairs = new String[][]
    {
      {"Vietnamese", "Tôi không", "Ô kìa, con bươm", "Đừng! Mày đang làm cô", "\"Tin tôi", "Tốt nhất l"},
      {"Korean", "생일 축하해 Muiriel!", "보고 싶", "만나서 반가워요.", "자리", "한테 인사할 필요가 없어."},
      {"Bulgarian", "Често чуваме", "Можеш ли да стиг", "Виж с ушите си.", "Върнах се у ", "Баща ми е в Токио и", "Тази къща изгоря."},
      {"Finnish", "Hänellä on etunaan", "Hän oli", "Järven läpimitta ", "Kuinka pian mekko", "Hinnat"},
      {"Turkish", "Bunlar benim kalemlerim.", "Mahjong dünyada", "John birçok", "Şu kız kim acaba.", "Tokyo'da."},
      {"Latin", "accipe hoc", "capax infiniti", "consuetudo pro lege servatur", "vita ante acta", "quem deus vult "},
      {"Gibberish", "andom jsla minso lonamld", "zzzzzzzz", ".s.s.s.s", "aaaaaaklp dsabs loinfpog sopo20kf", "7 31 75 925 02488 0295 82139 5781"},
      {"Control", "hin- und rückfahrt?", "qui est-ce", "it's a surprise", "gracias, es todo.", "mógłby pan wysłać"}
    };
    List<List<String>> arr = Arrays.stream(pairs).map(e -> Arrays.stream(e).collect(Collectors.toList())).collect(Collectors.toList());

    System.out.println("Testing thresholds.");
    for (List<String> list: arr) {
      String langName = list.get(0);
      List<String> sents = list.subList(1, list.size());
      for (String s : sents) {
        assert ngram.encode(s).size() >= 5;
      }
      testChunk(sents, "zz", print_false, print_false);
    }
  }

  public String getLang(String sent) {
    if(httpMode) {
      return getLangHttp(sent);
    }
    else {
      return getLangNgram(sent);
    }
  }

  public String getLangNgram(String sent) {
    Map<String, Double> probs = ngram.detectLanguages(sent, Collections.emptyList());
    double maxVal = 0.0;
    String maxLang = "zz";
    for (String k: probs.keySet()) {
      if (probs.get(k) > maxVal) {
        maxLang = k;
        maxVal = probs.get(k);
      }
    }
    return maxLang;
  }

  public List<String> readLines(String path) {
    ArrayList<String> result = new ArrayList<>();
    try {
      BufferedReader br = new BufferedReader(new FileReader(path));
      String line;
      while ((line = br.readLine()) != null) {
        result.add(line);
      }
    } catch(java.io.IOException e) {
      throw new RuntimeException(e);
    }
    return result;
  }

  public static String getLangHttp(String sent) {
    HttpURLConnection connection = null;
    String urlParameters;
    try{
      urlParameters = "language=auto&text=" + URLEncoder.encode(sent, StandardCharsets.UTF_8.toString());
    }
    catch (Exception e) {
      return null;
    }

    Pattern pattern = Pattern.compile("\"detectedLanguage\":\\{\"name\":\".*?\"code\":\"(.*?)\"");

    try {
      //Create connection
      URL url = new URL(CHECKING_URL);
      connection = (HttpURLConnection) url.openConnection();
      connection.setRequestMethod("POST");
      connection.setRequestProperty("Content-Type",
        "application/x-www-form-urlencoded");

      connection.setRequestProperty("Content-Length",
        Integer.toString(urlParameters.getBytes().length));
      connection.setRequestProperty("Content-Language", "en-US");

      connection.setUseCaches(false);
      connection.setDoOutput(true);

      //Send request
      DataOutputStream wr = new DataOutputStream (
        connection.getOutputStream());
      wr.writeBytes(urlParameters);
      wr.close();

      //Get Response
      InputStream is = connection.getInputStream();
      BufferedReader rd = new BufferedReader(new InputStreamReader(is));
      StringBuilder response = new StringBuilder(); // or StringBuffer if Java version 5+
      String line;
      while ((line = rd.readLine()) != null) {
        response.append(line);
        response.append('\r');
      }
      rd.close();
      Matcher m = pattern.matcher(response.toString());
      m.find();
      return m.group(1).split("-")[0];
    } catch (Exception e) {
      e.printStackTrace();
      return null;
    } finally {
      if (connection != null) {
        connection.disconnect();
      }
    }
  }

  public static void main(String[] args) throws IOException {
    String classPath = System.getProperty("java.class.path");
    if(!classPath.substring(0, classPath.indexOf(";")).contains("languagetool-standalone\\target\\test-classes")) {
      throw new RuntimeException("Please set languagetool-standalone as your class path");
    }
    NGramLangIdentifierTest test = new NGramLangIdentifierTest(false);

    System.out.println(test.getLangNgram("avoid"));

    String sents = "C:\\Users\\Robert\\Desktop\\ngram-language-identification\\data\\val\\eng.txt";
    test.testChunk(test.readLines(sents).subList(0, 100000), "en", false, false);

    sents = "C:\\Users\\Robert\\Desktop\\ngram-language-identification\\data\\foreign\\tur_sentences.txt";
    test.testChunk(test.readLines(sents).subList(0, 10000), "zz", false, false);

    sents = "C:\\Users\\Robert\\Desktop\\ngram-language-identification\\data\\foreign\\latin.txt";
    test.testChunk(test.readLines(sents).subList(0, 1000), "zz", false, false);

    test.getLangNgram("the kilauea volcano on hawaii's big island is erupting.");
    test.testThresholds(true);
  }

}
