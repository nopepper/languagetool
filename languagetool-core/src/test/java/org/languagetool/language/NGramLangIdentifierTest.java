package org.languagetool.language;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class NGramLangIdentifierTest {

  private static final File ngramDir = new File("C:\\Users\\Robert\\Desktop\\RobertAIStuff\\language_identification\\prod\\model_compressed\\model_compressed.zip");
  private final NGramLangIdentifier ngram;

  public NGramLangIdentifierTest() throws IOException {
    System.out.println("Loading ngrams...");
    this.ngram = new NGramLangIdentifier(ngramDir, 50);
    System.out.println("Loaded.");
  }

  private int testChunk(String[] sents, String expectedLang, boolean print_false) {
    int correct = 0;
    for(String sent: sents) {
      Map<String, Double> probs = ngram.detectLanguages(sent, Collections.emptyList());
      Double prob = probs.getOrDefault(expectedLang, 0.0);
      double maxVal = prob;
      String maxLang = "zz";
      for (String k: probs.keySet()) {
        if (probs.get(k) > maxVal) {
          maxLang = k;
          maxVal = probs.get(k);
        }
      }
      if (maxVal > prob) {
        if(print_false) {
          System.out.println("Expected " + expectedLang + " Predicted " + maxLang + " with confidence " + Math.round(maxVal * 10000) / 10000.0 + " ---> " + sent);
        }
      }
      else {
        correct += 1;
      }
    }
    return correct;
  }

  public void testThresholds(boolean print_false) {
    String[][] pairs = {
      {"Vietnamese", "Tôi không", "Ô kìa, con bươm", "Đừng! Mày đang làm cô", "\"Tin tôi", "Tốt nhất l"},
      {"Korean", "생일 축하해 Muiriel!", "보고 싶", "만나서 반가워요.", "자리", "한테 인사할 필요가 없어."},
      {"Bulgarian", "Често чуваме", "Можеш ли да стиг", "Виж с ушите си.", "Върнах се у ", "Баща ми е в Токио и", "Тази къща изгоря."},
      {"Finnish", "Hänellä on etunaan", "Hän oli", "Järven läpimitta ", "Kuinka pian mekko", "Hinnat"},
      {"Turkish", "Bunlar benim kalemlerim.", "Mahjong dünyada", "John birçok", "Şu kız kim acaba.", "Tokyo'da."},
      {"Latin", "accipe hoc", "capax infiniti", "consuetudo pro lege servatur", "vita ante acta", "quem deus vult "},
      {"Gibberish", "andom jsla minso lonamld", "zzzzzzzz", ".s.s.s.s", "aaaaaaklp dsabs loinfpog sopo20kf", "7 31 75 925 02488 0295 82139 5781"},
      {"Control", "hin- und rückfahrt?", "qui est-ce", "it's a surprise", "gracias, es todo.", "mógłby pan wysłać"}
    };

    System.out.println("Testing thresholds.");
    for (String[] list: pairs) {
      String langName = list[0];
      String[] sents = Arrays.copyOfRange(list, 1, list.length);
      for (String s : sents) {
        assert ngram.encode(s).size() >= 5;
      }
      System.out.println(langName + ": " + testChunk(sents, "zz", print_false) + " out of " + sents.length + " correct");
      System.out.println();
    }
  }

  public static void main(String[] args) throws IOException {
    new NGramLangIdentifierTest().testThresholds(true);
  }

}
