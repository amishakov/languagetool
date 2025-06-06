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
package org.languagetool.rules;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.regex.Pattern;

import org.languagetool.AnalyzedSentence;
import org.languagetool.AnalyzedToken;
import org.languagetool.AnalyzedTokenReadings;
import org.languagetool.Language;
import org.languagetool.LinguServices;
import org.languagetool.UserConfig;

/**
 * An abstract rule checks the appearance of same words in a sentence or in two consecutive sentences.
 * The isTokenToCheck method can be used to check only specific words (e.g. substantive, verbs and adjectives).
 * This rule detects no grammar error but a stylistic problem (default off)
 * @author Fred Kruse
 * @since 4.1
 */
public abstract class AbstractStyleRepeatedWordRule  extends TextLevelRule {
  
  private static final Pattern OPENING_QUOTES = Pattern.compile("[\"“„»«]");
  private static final Pattern ENDING_QUOTES = Pattern.compile("[\"“”»«]");
  private static final Pattern SINGLE_QUOTES = Pattern.compile("[\'‚‘’'›‹]");
  private static final int MAX_TOKEN_TO_CHECK = 5;
  private static final int MAX_DISTANCE_OF_SENTENCES = 1;
  private static final boolean EXCLUDE_DIRECT_SPEECH = true;
  
  protected final LinguServices linguServices;
  protected final Language lang;
  
  protected int maxDistanceOfSentences = MAX_DISTANCE_OF_SENTENCES;
  protected boolean excludeDirectSpeech = EXCLUDE_DIRECT_SPEECH;

  public AbstractStyleRepeatedWordRule(ResourceBundle messages, Language lang, UserConfig userConfig) {
    super(messages);
    super.setCategory(Categories.STYLE.getCategory(messages));
    setLocQualityIssueType(ITSIssueType.Style);
    setDefaultOff();
    setOfficeDefaultOn();  // Default for LO/OO is always On
    this.lang = lang;
    if (userConfig != null) {
      linguServices = userConfig.getLinguServices();
      if (linguServices != null) {
        linguServices.setThesaurusRelevantRule(this);
      }
      Object[] cf = userConfig.getConfigValueByID(getId());
      if (cf != null) {
        if (cf != null && cf.length > 0 && cf[0] != null && cf[0] instanceof Integer) {
          maxDistanceOfSentences = (int) cf[0];
        }
        if (cf != null && cf.length > 1 && cf[1] != null && cf[1] instanceof Boolean) {
          excludeDirectSpeech = (boolean) cf[1];
        }
      }
    } else {
      linguServices = null;
    }
  }

  /**
   * Override this ID by adding a language acronym (e.g. STYLE_REPEATED_WORD_RULE_DE)
   * to use adjustment of maxWords by option panel
   * @since 4.1
   */   
  @Override
  public String getId() {
    return "STYLE_REPEATED_WORD_RULE";
  }

  @Override
  public String getDescription() {
    return "Repeated words in consecutive sentences";
  }
  
  /*
   * Message for repeated word in same sentence
   */
  protected abstract String messageSameSentence();
  
  /*
   * Message for repeated word in sentence before
   */
  protected abstract String messageSentenceBefore();
  
  /*
   * Message for repeated word in sentence after
   */
  protected abstract String messageSentenceAfter();
  
  /**
   *  give the user the possibility to configure the function
   */
  @Override
  public RuleOption[] getRuleOptions() {
    RuleOption[] ruleOptions = { new RuleOption(MAX_DISTANCE_OF_SENTENCES, messages.getString("guiStyleRepeatedWordText"), 0, 5),
                                 new RuleOption(EXCLUDE_DIRECT_SPEECH, messages.getString("guiStyleExcludeDirectSpeechText"), 0, 5)
                               };
    return ruleOptions;
  }

  /*
   * Check only special words (e.g substantive, verbs, adjectives)
   * (German example: return (token.matchesPosTagRegex("(SUB|EIG|VER|ADJ):.*") 
   *              && !token.matchesPosTagRegex("ART:.*|ADV:.*|VER:(AUX|MOD):.*"));
   */
  protected abstract boolean isTokenToCheck(AnalyzedTokenReadings token);
    
  /*
   * Is checked word part of pairs like "arm in arm", "side by side", etc. (exclude such pairs)
   */
  protected abstract boolean isTokenPair(AnalyzedTokenReadings[] tokens, int n, boolean before);
  
  /*
   * listings are excluded
   */
  private static boolean hasBreakToken(AnalyzedTokenReadings[] tokens) {
    for (int i = 0; i < tokens.length && i < MAX_TOKEN_TO_CHECK; i++) {
      if (tokens[i].getToken().equals("-") || tokens[i].getToken().equals("—") || tokens[i].getToken().equals("–")) {
        return true;
      }
    }
    return false;
  }
  
  /*
   * question - response - pairs are excluded
   */
  private static boolean isQuestionResponse(int nAct, int nTest, List<AnalyzedTokenReadings[]> tokenList) {
    int dist = nAct - nTest;
    if (dist != 1 && dist != -1) {
      return false;
    }
    AnalyzedTokenReadings[] actTokens = tokenList.get(nAct);
    AnalyzedTokenReadings[] testTokens = tokenList.get(nTest);
    if (actTokens.length < 2 || testTokens.length < 2) {
      return false;
    }
    String actToken;
    if (ENDING_QUOTES.matcher(actTokens[actTokens.length - 1].getToken()).matches()) {
      actToken = actTokens[actTokens.length - 2].getToken();
    } else {
      actToken = actTokens[actTokens.length - 1].getToken();
    }
    String testToken;
    if (ENDING_QUOTES.matcher(testTokens[testTokens.length - 1].getToken()).matches()) {
      testToken = testTokens[testTokens.length - 2].getToken();
    } else {
      testToken = testTokens[testTokens.length - 1].getToken();
    }
    return ((actToken.equals("?") && !testToken.equals("?")) || (testToken.equals("?") && !actToken.equals("?")));
  }
  
  private boolean isTokenInSentence(AnalyzedTokenReadings testToken, AnalyzedTokenReadings[] tokens, boolean isDirectSpeech) {
    return isTokenInSentence(testToken, tokens, -1, isDirectSpeech);
  }
  
  /* 
   *  true if token is part of composite word in sentence
   *  override for languages like German which contents composed words
   */
  protected boolean isPartOfWord(String testTokenText, String tokenText) {
    return false;
  }

  /* 
   *  true if is an exception of token pair
   *  note: method is called after two tokens are tested to share the same lemma
   */
  protected boolean isExceptionPair(AnalyzedTokenReadings token1, AnalyzedTokenReadings token2) {
    return false;
  }

  /* 
   * Set a URL to a synonym dictionary for a token
   */
  protected URL setURL(AnalyzedTokenReadings token ) throws MalformedURLException {
    return null;
  }
  
  /* 
   *  true if token is found in sentence
   */
  private boolean isTokenInSentence(AnalyzedTokenReadings testToken, AnalyzedTokenReadings[] tokens, 
      int notCheck, boolean isDirectSpeech) {
    if (testToken == null || tokens == null) {
      return false;
    }
    List<AnalyzedToken> readings = testToken.getReadings();
    List<String> lemmas = new ArrayList<>();
    for (AnalyzedToken reading : readings) {
      if (reading.getLemma() != null) {
        lemmas.add(reading.getLemma());
      }
    }
    for (int i = 0; i < tokens.length; i++) {
      if (excludeDirectSpeech && !isDirectSpeech && OPENING_QUOTES.matcher(tokens[i].getToken()).matches() 
          && i < tokens.length - 1 && !tokens[i + 1].isWhitespaceBefore()) {
        isDirectSpeech = true;
      } else if (excludeDirectSpeech && isDirectSpeech && ENDING_QUOTES.matcher(tokens[i].getToken()).matches() 
          && i > 1 && !tokens[i].isWhitespaceBefore()) {
        isDirectSpeech = false;
      } else if (i != notCheck && !isDirectSpeech && !isInQuotes(tokens, i) && isTokenToCheck(tokens[i])) {
        if ((!lemmas.isEmpty() && tokens[i].hasAnyLemma(lemmas.toArray(new String[0])) && !isExceptionPair(testToken, tokens[i])) 
            || isPartOfWord(testToken.getToken(), tokens[i].getToken())) {
          if (notCheck >= 0) {
            if (notCheck == i - 2) {
              return !isTokenPair(tokens, i, true);
            } else if (notCheck == i + 2) {
              return !isTokenPair(tokens, i, false);
            } else if ((notCheck == i + 1 || notCheck == i - 1) 
                && testToken.getToken().equals(tokens[i].getToken())) {
              return false;
            }
          }
          return true;
        }
      }
    }
    return false;
  }
  
  private boolean isInQuotes(AnalyzedTokenReadings[] tokens, int i) {
    return (i > 0 
            && (OPENING_QUOTES.matcher(tokens[i - 1].getToken()).matches() 
                || SINGLE_QUOTES.matcher(tokens[i - 1].getToken()).matches())
            && i < tokens.length - 1 
            && (ENDING_QUOTES.matcher(tokens[i + 1].getToken()).matches()
                || SINGLE_QUOTES.matcher(tokens[i + 1].getToken()).matches()));
  }
  
  private boolean getStartsWithDirectSpeech(int n, List<AnalyzedSentence> sentences, boolean isDirectSpeech) {
    if (!excludeDirectSpeech || n <= 0) {
      return false;
    }
    AnalyzedTokenReadings[] sentence = sentences.get(n - 1).getTokensWithoutWhitespace();
    for (int i = 0; i < sentence.length; i++) {
      AnalyzedTokenReadings token = sentence[i];
      if (!isDirectSpeech && OPENING_QUOTES.matcher(token.getToken()).matches() 
          && i < sentence.length - 1 && !sentence[i + 1].isWhitespaceBefore()) {
        isDirectSpeech = true;
      } else if (isDirectSpeech && ENDING_QUOTES.matcher(token.getToken()).matches() 
          && i > 1 && !sentence[i].isWhitespaceBefore()) {
        isDirectSpeech = false;
      }
    }
    return isDirectSpeech;
  }

  @Override
  public RuleMatch[] match(List<AnalyzedSentence> sentences) throws IOException {
    List<RuleMatch> ruleMatches = new ArrayList<>();
    List<AnalyzedTokenReadings[]> tokenList = new ArrayList<>();
    List<Boolean> isDSList = new ArrayList<>();
    int pos = 0;
    boolean startsWithDirectSpeech = false;
    for (int n = 0; n < maxDistanceOfSentences && n < sentences.size(); n++) {
      tokenList.add(sentences.get(n).getTokensWithoutWhitespace());
      startsWithDirectSpeech = getStartsWithDirectSpeech(n, sentences, startsWithDirectSpeech);
      isDSList.add(startsWithDirectSpeech);
    }
    boolean isDirectSpeech = false;
    for (int n = 0; n < sentences.size(); n++) {
      if (n + maxDistanceOfSentences < sentences.size()) {
        tokenList.add(sentences.get(n + maxDistanceOfSentences).getTokensWithoutWhitespace());
        startsWithDirectSpeech = getStartsWithDirectSpeech(n + maxDistanceOfSentences, sentences, startsWithDirectSpeech);
        isDSList.add(startsWithDirectSpeech);
      }
      if (tokenList.size() > 2 * maxDistanceOfSentences + 1) {
        tokenList.remove(0);
        isDSList.remove(0);
      }
      int nTok = maxDistanceOfSentences;
      if (n < maxDistanceOfSentences) {
        nTok = n;
      } else if (n >= sentences.size() - maxDistanceOfSentences) {
        nTok = tokenList.size() - (sentences.size() - n);
      }
      if (!hasBreakToken(tokenList.get(nTok))) {
        for (int i = 0; i < tokenList.get(nTok).length; i++) {
          AnalyzedTokenReadings token = tokenList.get(nTok)[i];
          if (excludeDirectSpeech && !isDirectSpeech && OPENING_QUOTES.matcher(token.getToken()).matches() 
              && i < tokenList.get(nTok).length - 1 && !tokenList.get(nTok)[i + 1].isWhitespaceBefore()) {
            isDirectSpeech = true;
          } else if (excludeDirectSpeech && isDirectSpeech && ENDING_QUOTES.matcher(token.getToken()).matches() 
              && i > 1 && !tokenList.get(nTok)[i].isWhitespaceBefore()) {
            isDirectSpeech = false;
          } else if (!isDirectSpeech && !isInQuotes(tokenList.get(nTok), i) && isTokenToCheck(token)) {
            int isRepeated = 0;
            if (isTokenInSentence(token, tokenList.get(nTok), i, isDSList.get(nTok))) {
              isRepeated = 1;
            }
            for(int j = nTok - 1; isRepeated == 0 && j >= 0 && j >= nTok - maxDistanceOfSentences; j--) {
              if (!isQuestionResponse(nTok, j, tokenList) && isTokenInSentence(token, tokenList.get(j), isDSList.get(j))) {
                isRepeated = 2;
              }
            }
            for(int j = nTok + 1; isRepeated == 0 && j < tokenList.size() && j <= nTok + maxDistanceOfSentences; j++) {
              if (!isQuestionResponse(nTok, j, tokenList) && isTokenInSentence(token, tokenList.get(j), isDSList.get(j))) {
                isRepeated = 3;
              }
            }
            if (isRepeated != 0) {
              String msg;
              if (isRepeated == 1) {
                msg = messageSameSentence();
              } else if (isRepeated == 2) {
                msg = messageSentenceBefore();
              } else {
                msg = messageSentenceAfter();
              }
              int startPos = pos + token.getStartPos();
              int endPos = pos + token.getEndPos();
              RuleMatch ruleMatch = new RuleMatch(this, null, startPos, endPos, msg);
              URL url = setURL(token);
              if(url != null) {
                ruleMatch.setUrl(url);
              }
              ruleMatches.add(ruleMatch);
            }
          } 
        }
      }
      pos += sentences.get(n).getCorrectedTextLength();
    }
    return toRuleMatchArray(ruleMatches);
  }
  
  @Override
  public int minToCheckParagraph() {
    return excludeDirectSpeech ? -1 : maxDistanceOfSentences;
  }
  
}
