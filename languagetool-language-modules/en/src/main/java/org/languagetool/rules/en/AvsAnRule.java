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
package org.languagetool.rules.en;

import org.languagetool.AnalyzedSentence;
import org.languagetool.AnalyzedTokenReadings;
import org.languagetool.rules.Category;
import org.languagetool.rules.Example;
import org.languagetool.rules.ITSIssueType;
import org.languagetool.rules.RuleMatch;
import org.languagetool.tools.StringTools;

import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

import static org.languagetool.rules.en.AvsAnData.getWordsRequiringA;
import static org.languagetool.rules.en.AvsAnData.getWordsRequiringAn;

/**
 * Check if the determiner (if any) preceding a word is:
 * <ul>
 *   <li><i>an</i> if the next word starts with a vowel
 *   <li><i>a</i> if the next word does not start with a vowel
 * </ul>
 *  This rule loads some exceptions from external files (e.g. <i>an hour</i>).
 * 
 * @author Daniel Naber
 */
public class AvsAnRule extends EnglishRule {

  public AvsAnRule(final ResourceBundle messages) {
    super.setCategory(new Category(messages.getString("category_misc")));
    setLocQualityIssueType(ITSIssueType.Misspelling);
    addExamplePair(Example.wrong("The train arrived <marker>a hour</marker> ago."),
                   Example.fixed("The train arrived <marker>an hour</marker> ago."));
  }

  @Override
  public String getId() {
    return "EN_A_VS_AN";
  }

  @Override
  public String getDescription() {
    return "Use of 'a' vs. 'an'";
  }

  @Override
  public RuleMatch[] match(final AnalyzedSentence sentence) {
    final List<RuleMatch> ruleMatches = new ArrayList<>();
    final AnalyzedTokenReadings[] tokens = sentence.getTokensWithoutWhitespace();
    String prevToken = "";
    int prevPos = 0;
    //ignoring token 0, i.e., SENT_START
    for (int i = 1; i < tokens.length; i++) {
      String token = tokens[i].getToken();
      boolean doesRequireA = false;
      boolean doesRequireAn = false;
      // check for exceptions:
      boolean isException = false;
      final String[] parts = token.split("[-']");  // for example, in "one-way" only "one" is relevant
      if (parts.length >= 1 && !parts[0].equalsIgnoreCase("a")) {  // avoid false alarm on "A-levels are..."
        token = parts[0];
      }
      if (tokens[i].isWhitespaceBefore() || !"-".equals(token)) { //e.g., 'a- or anti- are prefixes'
        token = token.replaceAll("[^αa-zA-Z0-9\\.;,:']", "");         // e.g. >>an "industry party"<<
        if (StringTools.isEmpty(token)) {
          continue;
        }
      }
      final char tokenFirstChar = token.charAt(0);
      if (getWordsRequiringA().contains(token.toLowerCase()) || getWordsRequiringA().contains(token)) {
        isException = true;
        doesRequireA = true;
      }
      if (getWordsRequiringAn().contains(token.toLowerCase()) || getWordsRequiringAn().contains(token)) {
        if (isException) {
          // some words allow both 'a' and 'an', e.g. 'historical':
          isException = true;
          doesRequireA = false;
          doesRequireAn = false;
        } else {
          isException = true;
          doesRequireAn = true;
        }
      }

      if (!isException) {
        if (StringTools.isAllUppercase(token) || StringTools.isMixedCase(token)) {
          // we don't know how all-uppercase and mixed case words (often abbreviations) are pronounced,
          // so never complain about these:
          doesRequireAn = false;
          doesRequireA = false;
        } else if (isVowel(tokenFirstChar)) {
          doesRequireAn = true;
        } else {
          doesRequireA = true;
        }
      }
      String msg = null;
      if (prevToken.equalsIgnoreCase("a") && doesRequireAn) {
        String replacement = "an";
        if (prevToken.equals("A")) {
          replacement = "An";
        }
        msg = "Use <suggestion>" + replacement + "</suggestion> instead of '" + prevToken + "' if the following "+
            "word starts with a vowel sound, e.g. 'an article', 'an hour'";
      } else if (prevToken.equalsIgnoreCase("an") && doesRequireA) {
        String replacement = "a";
        if (prevToken.equals("An")) {
          replacement = "A";
        }
        msg = "Use <suggestion>" + replacement + "</suggestion> instead of '" + prevToken + "' if the following "+
            "word doesn't start with a vowel sound, e.g. 'a sentence', 'a university'";
      }
      if (msg != null) {
        final RuleMatch ruleMatch = new RuleMatch(this, prevPos, prevPos + prevToken.length(), msg, "Wrong article");
        ruleMatches.add(ruleMatch);
      }
      if (tokens[i].hasPosTag("DT")) {
        prevToken = token;
        prevPos = tokens[i].getStartPos();
      } else {
        prevToken = "";
      }
    }
    return toRuleMatchArray(ruleMatches);
  }

  /**
   * Adds "a" or "an" to the English noun. Used for suggesting the proper form of the indefinite article.
   * @param noun Word that needs an article.
   * @return String containing the word with a determiner, or just the word if the word is an abbreviation.
   */
  public final String suggestAorAn(final String noun) {
    String word = noun;
    boolean doesRequireA = false;
    boolean doesRequireAn = false;
    // check for exceptions:
    boolean isException = false;
    final String[] parts = word.split("[-']");  // for example, in "one-way" only "one" is relevant
    if (parts.length >= 1 &&
        !parts[0].equalsIgnoreCase("a")) {  // avoid false alarm on "A-levels are..."
      word = parts[0];
    }
    if (StringTools.isEmpty(word)) {
      return word;
    }
    final char tokenFirstChar = word.charAt(0);
    if (getWordsRequiringA().contains(word.toLowerCase()) || getWordsRequiringA().contains(word)) {
      isException = true;
      doesRequireA = true;
    }
    if (getWordsRequiringAn().contains(word.toLowerCase()) || getWordsRequiringAn().contains(word)) {
      if (isException) {
        throw new IllegalStateException(word + " is listed in both det_a.txt and det_an.txt");
      }
      isException = true;
      doesRequireAn = true;
    }
    if (!isException) {
      if (StringTools.isAllUppercase(word) || StringTools.isMixedCase(word)) {
        // we don't know how all-uppercase words (often abbreviations) are pronounced,
        // so never complain about these:
        doesRequireAn = false;
        doesRequireA = false;
      } else if (isVowel(tokenFirstChar)) {
        doesRequireAn = true;
      } else {
        doesRequireA = true;
      }
    }
    if (doesRequireA) {
      return "a " + noun;
    } else if (doesRequireAn) {
      return "an " + noun;
    } else {
      return noun;
    }
  }

  private static boolean isVowel(char c) {
    char lc = Character.toLowerCase(c);
    return lc == 'a' || lc == 'e' || lc == 'i' || lc == 'o' || lc == 'u';
  }

  @Override
  public void reset() {
    // nothing
  }

}
