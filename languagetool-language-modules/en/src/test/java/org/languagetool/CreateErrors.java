/* LanguageTool, a natural language style checker
 * Copyright (C) 2005 Daniel Naber (http://www.danielnaber.de)
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
package org.languagetool;

import javafx.util.Pair;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import org.junit.Test;
import org.languagetool.language.English;
import org.languagetool.tagging.en.EnglishTagger;
import org.languagetool.tokenizers.SRXSentenceTokenizer;
import org.languagetool.tokenizers.SentenceTokenizer;

import java.io.*;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;



@SuppressWarnings("ConstantConditions")
public class CreateErrors {

  private final SentenceTokenizer stokenizer = new SRXSentenceTokenizer(new English());
  private final EnglishTagger tagger = new EnglishTagger();
  private final String basePath = "D:\\Desktop\\5-grams\\errors\\";
  private final JLanguageTool lt = new JLanguageTool(new English());

  private AnalyzedTokenReadings[] origTokens;

  private class UniqueArr extends ArrayList<String>{
    String origLine;
    UniqueArr(String origLine){
      this.origLine = origLine;
    }
    public boolean add(String s){
      s = s.replaceAll("\\s+", " ").trim();
      if(!s.equals(origLine)){
         super.add(s);
         return true;
      }
      return false;
    }
    public void removeDuplicates(){
      List<String> deduped = this.stream().distinct().collect(Collectors.toList());
      this.clear();
      this.addAll(deduped);
    }
  }



  private UniqueArr CreateErrorSentences(String origLine){
    UniqueArr result = new UniqueArr(origLine);
    result.add("this is a test");


    result.removeDuplicates();
    return result;
  }

  public void listf(String directoryName, ArrayList<File> files) {
    File directory = new File(directoryName);

    // get all the files from a directory
    File[] fList = directory.listFiles();
    for (File file : fList) {
      if (file.isFile()) {
        files.add(file);
      } else if (file.isDirectory()) {
        listf(file.getAbsolutePath(), files);
      }
    }
  }

  private ArrayList<String> GetMatches(String pattern, String strToMatch){
    ArrayList<String> allMatches = new ArrayList<String>();
    Matcher m = Pattern.compile(pattern)
            .matcher(strToMatch);
    while (m.find()) {
      allMatches.add(m.group());
    }
    return allMatches;
  }

  private String GetMatch(String pattern, String strToMatch){
    String match = "";
    Matcher m = Pattern.compile(pattern)
            .matcher(strToMatch);
    if(m.find() && m.groupCount() > 0)
      return m.group(1);
    else if(m.find())
            return m.group(0);
    return match;
  }

  private ArrayList<Pair<String, String>> GetVariantsSpecialDataSet(String line){
    ArrayList<Pair<String, String>> result = new ArrayList<Pair<String, String>>();
    line = line.trim();
    if(line.startsWith("<p>") && line.contains("<NS")){
      line = line.replace("<p>", "").replace("</p>", "");
      for(String match : GetMatches("<NS.*?>.*?<\\/NS>" ,line)){
          String error = GetMatch("<i.*?>(.*?)<\\/i>", line);
          String replacement = GetMatch("<c.*?>(.*?)<\\/c>", line);
          String correct = line.replace(match, replacement);
          String wrong = line.replace(match, error);
          boolean correctC = GetMatches("<NS.*?>.*?<\\/NS>" ,correct).size() == 0;
          boolean wrongC = GetMatches("<NS.*?>.*?<\\/NS>" ,wrong).size() == 0;
          if(!correctC)
          {
            result.addAll(GetVariantsSpecialDataSet(correct));
          }
          if(!wrongC)
          {
            result.addAll(GetVariantsSpecialDataSet(wrong));
          }
          if(correctC && wrongC && correct.length() > 25 && !(correct.equals(correct.toUpperCase()))){
            result.add(new Pair<>(
                    correct.trim(), //Correct first
                    wrong.trim() //Then false
            ));
          }
      }
    }
    return result;
  }

  @Test
  public void TestRV() throws IOException{
    ReplaceVariants("I like bananas.");
  }

  private class ReplacementItem{
     UniqueArr replacements;
    AnalyzedTokenReadings[] tokens;
    String correctLine;
    boolean regFirst = false;
    boolean regAll = false;
    boolean wordPadding = false;
     private Pair<String, String> getGroupPOSCombo(String POSTag){
       Matcher m = Pattern.compile("(\\w.*?)\\[(\\d)\\]")
               .matcher(POSTag);
       if(m.find()){
         return new Pair<String, String>(m.group(2), m.group(1));
       }
       else return new Pair<String, String>("[ALL]", POSTag);
     }

     private boolean isValid(Matcher m, List<Pair<String, String>> pos, String word){
       if(pos.size() == 0) return true;
       try{
         for(int i = 0; i < tokens.length; i++){
           for (Pair<String, String> indexedTag : pos) {
             String wrd;
             if(tokens[i].getToken().equals(indexedTag.getKey())
                     && !tokens[i].matchesPosTagRegex(".*" + indexedTag.getValue() + ".*"))
               return false;
           }
         }
       }
       catch (Exception ex){
         return  false;
       }
       return true;
     }

     private String getReplacementSafe(String findingTok, String replacingTok, List<Pair<String, String>> pos){
       String rgx = String.format("(^| )%s( |$)", findingTok);
       Matcher m = Pattern.compile(rgx)
               .matcher(correctLine);
       if(m.find() && isValid(m, pos, findingTok)){
         return correctLine.replaceAll(rgx, m.group(1) + replacingTok + m.group(2));
       }
       return correctLine;
     }
     private String getReplacementRegex(String findingTok, String replacingTok, boolean replAll, List<Pair<String, String>> pos){
       Matcher m = Pattern.compile(findingTok)
               .matcher(correctLine);
       if(m.find()){
         if(wordPadding){
           for(int j = 1; j < m.groupCount(); j++){
             if(m.group(j) != null){
               replacingTok = replacingTok.replace("[" + (j-1) + "]", m.group(j));
               for(int i = 0; i < pos.size();i++){
                 if(pos.get(i).getKey().equals(String.valueOf(j-1))){
                   pos.set(i, new Pair<>(m.group(j), pos.get(i).getValue()));
                 }
                 if(pos.get(i).getKey().equals("[ALL]")){
                   pos.set(i, new Pair<>(m.group(), pos.get(i).getValue()));
                 }
               }
               if(replacingTok.contains("[" + (j-1) +"L" + "]")){
                 try{
                   //AnalyzedTokenReadings[] atc = lt.analyzeText(m.group(j)).get(0).getTokensWithoutWhitespace();
                   String lemma = m.group(j);
                   for(String lem : lt.analyzeText(m.group(j)).get(0).getLemmaSet()){
                     if(!lem.trim().isEmpty() && !lem.equals(lemma))
                     {
                       lemma = lem;
                       break;
                     }
                   }
                   replacingTok = replacingTok.replace("[" + (j-1) +"L" + "]", lemma);
                 }catch (Exception ex){

                 }
               }
             }
             else
               return correctLine;
           }
           replacingTok = replacingTok.replace("[LAST]", m.group(m.groupCount()));
         }
         else{
           for(int j = 0; j <= m.groupCount(); j++){
             if(m.group(j) != null){
               replacingTok = replacingTok.replace("[" + j + "]", m.group(j));
               for(int i = 0; i < pos.size();i++){
                 if(pos.get(i).getKey().equals(String.valueOf(j))){
                   pos.set(i, new Pair<>(m.group(j), pos.get(i).getValue()));
                 }
                 if(pos.get(i).getKey().equals("[ALL]")){
                   pos.set(i, new Pair<>(m.group(), pos.get(i).getValue()));
                 }
               }
               if(replacingTok.contains("[" + (j) +"L" + "]")){
                 try{
                   String lemma = lt.analyzeText(m.group(j)).get(0).getTokens()[1].getAnalyzedToken(0).getLemma();
                   replacingTok = replacingTok.replace("[" + (j) +"L" + "]", lemma);
                 }catch (Exception ex){

                 }
               }
             }
             else
               return correctLine;
           }
         }
         if(m.group() == null) return "";
         if(!isValid(m, pos, m.group())) return correctLine;
         if(replAll)
            return correctLine.replace(m.group(), replacingTok);
         else
           return correctLine.replaceFirst(Pattern.quote(m.group()), replacingTok);
       }
       return correctLine;
     }

     public UniqueArr GetReplacements() {
       replacements.removeDuplicates();
       return replacements;
     }

     public ReplacementItem(String correctLine, String replaceLine, AnalyzedTokenReadings[] tokens) throws IOException{
       replacements = new UniqueArr(correctLine);
       this.tokens = tokens;
       this.correctLine = correctLine;
       List<Pair<String, String>> pos = new ArrayList<>(); //regexes
       boolean matchesPOS = true;
       String findingTok;
       String replacingTok;
       //Get Params
       Matcher m = Pattern.compile("(^.*):")
               .matcher(replaceLine);
       if(m.find()) {
         String params = m.group(1);
         replaceLine = replaceLine.substring(m.group().length());
         for (String s : params.split("\\|")) {
           if (s.equals("RF")) regFirst = true;
           else if (s.equals("RA")) regAll = true;
           else if (s.equals("WP")) wordPadding = true;
           else {
             pos.add(getGroupPOSCombo(s));
           }
         }
       }
       String[] split = replaceLine.split("ǁ");
       ArrayList<String> s =  new ArrayList<String>(Arrays.asList(split));
       if(!(regAll || regFirst)){
         for(int a = 0; a < s.size(); a++){
           ArrayList<String> s2 =  new ArrayList<String>(s);
           Collections.swap(s2, 0, a);
           findingTok = s2.get(0);
           for (int i = 1; i < s2.size(); i++) {
             replacingTok = s2.get(i);
             if (!regFirst && !regAll) {
               replacements.add(getReplacementSafe(findingTok, replacingTok, pos));
             } else
               replacements.add(getReplacementRegex(findingTok, replacingTok, regAll, pos));
           }
         }
       }
       else{
         //String[] splits = replaceLine.split("ǁ");
         ArrayList<String> ss =  new ArrayList<String>(Arrays.asList(split));
         findingTok = ss.get(0);
         if(wordPadding) findingTok = String.format("(^| )%s( |$)", findingTok);
         for (int i = 1; i < ss.size(); i++) {
           replacingTok = ss.get(i);
           if(wordPadding) replacingTok = String.format("[0]%s[LAST]", replacingTok);
           if (!regFirst && !regAll) {
             replacements.add(getReplacementSafe(findingTok, replacingTok, pos));
           } else
             replacements.add(getReplacementRegex(findingTok, replacingTok, regAll, pos));
         }
       }

     }
  }

  private UniqueArr ReplaceVariants(String line) throws IOException{
    UniqueArr result = new UniqueArr(line);
      AnalyzedTokenReadings[] readings = lt.getAnalyzedSentence(line).getTokens();
    LineIterator it = FileUtils.lineIterator(new java.io.File(basePath + "replaceErrors.txt"));
    try {
      while (it.hasNext()) {
        String replaceLine = it.nextLine();
        result.addAll(new ReplacementItem(line, replaceLine, readings).GetReplacements());
      }
    } finally {
      LineIterator.closeQuietly(it);
    }
    return result;
  }

  private UniqueArr SwapVariants(String line) throws IOException{
    UniqueArr result = new UniqueArr(line);
    result.addAll(ReplaceVariants(line));
    return result;
  }

  private UniqueArr GetVariantsNormal(String line) throws IOException{
    UniqueArr result = new UniqueArr(line);
    result.addAll(ReplaceVariants(line));
    result.removeDuplicates();
    return result;
  }

  @Test
  public void makeErrorsFromDataSet() throws IOException{
    ArrayList<File> files = new ArrayList<File>();
    ArrayList<Pair<String, String>> result = new ArrayList<Pair<String, String>>();
    listf(basePath + "dataset", files);
    Writer outCorrect = new BufferedWriter(new FileWriter(basePath + "sent_right.txt"));
    Writer outFalse = new BufferedWriter(new FileWriter(basePath + "sent_wrong.txt"));
    for(File f : files){
      LineIterator it = FileUtils.lineIterator(f);
      try {
        while (it.hasNext()) {
          result.addAll(GetVariantsSpecialDataSet(it.nextLine()));
        }
      }
      finally {
        LineIterator.closeQuietly(it);
      }
    }
    for (Pair<String, String> p : result){
      outCorrect.append(p.getKey()).append("\r\n");
      outFalse.append(p.getValue()).append("\r\n");
    }
    outCorrect.flush();
    outCorrect.close();
    outFalse.flush();
    outFalse.close();
  } //Already done

  @Test
  public void TestErrorMaking() throws IOException{
    AnalyzedTokenReadings[] atr = lt.getAnalyzedSentence("This is an apple").getTokens();
    ReplacementItem ri = new ReplacementItem("This is an apple", "RF|NN:\\b\\w+$ǁ[0]s", atr);

    AnalyzedTokenReadings[] atr2 = lt.getAnalyzedSentence("This is is a nice house").getTokens();
    ReplacementItem ri2 = new ReplacementItem("This is is a nice house", "RF|VB.*[1]|VB[2]: (is) (is)ǁ is", atr2);

    AnalyzedTokenReadings[] atr3 = lt.getAnalyzedSentence("This has been a nice evening").getTokens();
    ReplacementItem ri3 = new ReplacementItem("This has been a nice evening", "has beenǁhave beenǁare beingǁwere being", atr3);

    AnalyzedTokenReadings[] atr4 = lt.getAnalyzedSentence("This is being a nice evening").getTokens();
    ReplacementItem ri4 = new ReplacementItem("This is being a nice evening", "RA:has beenǁhave beenǁ(are|is) beingǁwere being", atr4);

    AnalyzedTokenReadings[] atr5 = lt.getAnalyzedSentence("He wanted to improve his strength gradually by increasing the weight").getTokens();
    ReplacementItem ri5 = new ReplacementItem("He wanted to improve his strength gradually by increasing the weight", "RF|RB[3]:(to )(\\w.* )(\\w.*ly )ǁ[1][3][2]", atr5);

    AnalyzedTokenReadings[] atr6 = lt.getAnalyzedSentence("I have visited Niagara Falls last weekend.").getTokens();
    ReplacementItem ri6 = new ReplacementItem("I have visited Niagara Falls last weekend.", "RF|VBN[1]:have (\\w+)ǁ[1]", atr6);

    AnalyzedTokenReadings[] atr7 = lt.getAnalyzedSentence("I must call him immediately.").getTokens();
    ReplacementItem ri7 = new ReplacementItem("I must call him immediately.", "RA|VB[2]:(must|got|have|should) (\\w+)ǁ[1] to [2]", atr7);

    AnalyzedTokenReadings[] atr8 = lt.getAnalyzedSentence("Every student likes the teacher.").getTokens();
    ReplacementItem ri8 = new ReplacementItem("Every student likes the teacher.", "RF|NN[1]|VB[2]:(\\w+[^s]) (\\w+)sǁ[1]s [2]", atr8);

    AnalyzedTokenReadings[] atr9 = lt.getAnalyzedSentence("How many children do you have?").getTokens();
    ReplacementItem ri9 = new ReplacementItem("How many children do you have?", "RA|WP:many (\\w+[^s])ǁmany [1]s", atr9);

    AnalyzedTokenReadings[] atr10 = lt.getAnalyzedSentence("19 Action News compiled the all of the documents, which can be viewed below").getTokens();
    ReplacementItem ri10 = new ReplacementItem("19 Action News compiled the all of the documents, which can be viewed below", "RA|VB[1]:(\\w+) toǁ[1]", atr10);

    AnalyzedTokenReadings[] atr11 = lt.getAnalyzedSentence("another time").getTokens();
    ReplacementItem ri11 = new ReplacementItem("another time", "RA|WP|NN[1]:another (\\w+)ǁanother [1L]s", atr11);

    AnalyzedTokenReadings[] atr12 = lt.getAnalyzedSentence("Diamond ore").getTokens();
    ReplacementItem ri12 = new ReplacementItem("Diamond ore", "aweǁorǁoarǁore", atr12);

    AnalyzedTokenReadings[] atr13 = lt.getAnalyzedSentence("How will they be getting here?").getTokens();
    ReplacementItem ri13 = new ReplacementItem("How will they be getting here?", "RF|WP|VB.*[4]:(is|are|will be|will (\\w+) be)( \\w+|) (\\w+ing)ǁ[1][3] [4L]", atr13);

    AnalyzedTokenReadings[] atr14 = lt.getAnalyzedSentence("In the 50s").getTokens();
    ReplacementItem ri14 = new ReplacementItem("In the 50s", "RA|WP:(\\d\\d)sǁ[1]'sǁ'[1]s", atr14);
  }

  @Test
  public void makeErrorsFromBatch() throws IOException, InterruptedException {
    LineIterator it = FileUtils.lineIterator(new java.io.File(basePath + "10k-train-example.txt"), "UTF-8");
    Writer outWrong = new BufferedWriter(new FileWriter(basePath + "train.wrong"));
    Writer outRight = new BufferedWriter(new FileWriter(basePath + "train.correct"));
    int iter = 0;
    int maxDodec = 0;
    int dodec = 0;
    ExecutorService es = Executors.newFixedThreadPool(5);
    List<Callable<Void>> todo = new ArrayList<Callable<Void>>();
    class toRun implements Callable<Void> {
      String input;
      public toRun(String in) {input = in;}
      @Override
      public Void call() {
        try{
          if(".,?!:-;".contains(input.substring(input.length() - 1))) input = input.substring(0, input.length() - 1);
          for(String artificialError : GetVariantsNormal(input)){
            outRight.append(input).append("\r\n");
            outWrong.append(artificialError).append("\r\n");
          }
        }
        catch (Exception ex){
          System.console().writer().write("Error: " + ex.getMessage());
        }
        return null;
      }
    }
    try {
      while (it.hasNext()) {
        String in = it.nextLine().replaceAll("\\s+", " ").trim();
        in = Character.toLowerCase(in.charAt(0)) + in.substring(1);
        todo.add(new toRun(in));
        iter++;
        if(iter == 20){
          iter = 0;
          es.invokeAll(todo);
          todo.clear();
          dodec++;
        }
        if(maxDodec > 0 && dodec >= maxDodec)
          break;
      }
    } finally {
      if(iter != 0){
        es.invokeAll(todo);
      }
      LineIterator.closeQuietly(it);
      outWrong.flush();
      outWrong.close();
      outRight.flush();
      outRight.close();
      es.shutdown();
      es.awaitTermination(100, TimeUnit.SECONDS);
    }
  }
}
