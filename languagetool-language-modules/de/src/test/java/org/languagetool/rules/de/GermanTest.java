/* LanguageTool, a natural language style checker
 * Copyright (C) 2019 Daniel Naber (http://www.danielnaber.de)
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
package org.languagetool.rules.de;

import org.junit.Test;
import org.languagetool.*;
import org.languagetool.language.German;
import org.languagetool.rules.Rule;
import org.languagetool.rules.patterns.AbstractPatternRule;
import org.languagetool.rules.patterns.PatternRuleLoader;
import org.languagetool.rules.patterns.PatternToken;
import org.languagetool.rules.patterns.RegexPatternRule;
import org.languagetool.tagging.disambiguation.rules.DisambiguationPatternRule;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import org.languagetool.rules.FakeRule;
import org.languagetool.rules.RuleMatch;
import static org.junit.Assert.assertEquals;

public class GermanTest extends LanguageSpecificTest {
  
  @Test
  public void testLanguage() throws IOException {
    // NOTE: this text needs to be kept in sync with config.ts -> DEMO_TEXTS:
    String s = "Schreiben Sie in diesem Textfeld oder fügen Sie einen Text ein. Ihr Text wird kontinuierlich über prüft und Fehler werden farbig unterstrichen. Rechtshcreibfehler werden rot markirt, Grammatikfehler werden gelb hervor gehoben und Stilfehler werden, anders wie die anderen Fehler, blau unterstrichen. wussten Sie dass Synonyme per Doppelklick auf ein Wort aufgerufen werden können? Nutzen sie LanguageTool in allen Lebenslagen, etwa wenn Sie am Donnerstag, dem 13. Mai 2022, einen Basketballkorb in 10 Fuß Höhe montieren möchten.";
    Language lang = Languages.getLanguageForShortCode("de-DE");
    testDemoText(lang, s,
      Arrays.asList("ZUSAMMENSCHREIBUNG_UEBER", "GERMAN_SPELLER_RULE", "GERMAN_SPELLER_RULE", "ZUSAMMENSCHREIBUNG_HER", "KOMP_WIE", "UPPERCASE_SENTENCE_START", "KOMMA_ZWISCHEN_HAUPT_UND_NEBENSATZ_2", "AUFFORDERUNG_SIE", "DE_DATE_WEEKDAY", "EINHEITEN_METRISCH")
    );
    runTests(lang, null, "_");
  }

  @Test
  public void testMessageCoherency() {
    Language lang = Languages.getLanguageForShortCode("de-DE");
    JLanguageTool lt = new JLanguageTool(lang);
    for (Rule rule : lt.getAllRules()) {
      if (rule instanceof AbstractPatternRule patternRule) {
        String message = patternRule.getMessage();
        String origWord = null;
        String suggWord = null;
        if (message.contains("Kommata")) {
          origWord = "Kommata";
          suggWord = "Kommas";
        }
        if (message.contains("Präfix")) {
          origWord = "Präfix";
          suggWord = "Vorsilbe";
        }
        if (message.contains("Suffix")) {
          origWord = "Suffix";
          suggWord = "Nachsilbe";
        }
        if (message.contains("Substantiv")) {
          origWord = "Substantiv";
          suggWord = "Nomen";
        }
        if (message.toLowerCase().contains("substantiviert")) {
          origWord = "substantiviert";
          suggWord = "nominalisiert";
        }
        if (message.toLowerCase().contains("substantivisch")) {
          origWord = "substantivisch";
          suggWord = "nominalisiert";
        }
        if (message.contains("Genetiv")) {
          origWord = "Genetiv";
          suggWord = "Genitiv";
        }
        if (message.contains("Partizip 1")) {
          origWord = "Partizip 1";
          suggWord = "Partizip I";
        }
        if (message.contains("Partizip 2")) {
          origWord = "Partizip 2";
          suggWord = "Partizip II";
        }
        if (message.contains(" fordert ")) {
          origWord = "fordert";
          suggWord = "erfordert";
        }
        if (message.contains("auseinandergeschrieben")) {
          origWord = "auseinandergeschrieben";
          suggWord = "auseinander geschrieben";
        }
        if (message.contains("getrenntgeschrieben")) {
          origWord = "getrenntgeschrieben";
          suggWord = "getrennt geschrieben";
        }
        if (message.contains("Meinten sie")) {
          origWord = "Meinten sie";
          suggWord = "Meinten Sie";
        }
        if (message.contains("meinten sie")) {
          origWord = "meinten sie";
          suggWord = "meinten Sie";
        }
        if (message.toLowerCase().contains("wollen sie")) {
          origWord = "Wollen Sie";
          suggWord = "Möchten Sie";
        }
        if (origWord != null) {
          System.err.println("WARNING: Aus Gründen der Einheitlichkeit bitte '" + suggWord + "' nutzen statt '" + origWord + "' in der Regel " + patternRule.getFullId() + ", message: '" + message + "'");
        }
      }
    }
  }
  
  @Test
  public void testGenderCharsAgainstAllRules() throws IOException {
    Language lang = Languages.getLanguageForShortCode("de-DE");
    JLanguageTool lt = new JLanguageTool(lang);
    assertThat(lt.check("Die Jurist_innenausbildung ist schwer.").size(), is(0));
    assertThat(lt.check("Die Jurist*innenausbildung ist schwer.").size(), is(0));
    assertThat(lt.check("Die Jurist:innenausbildung ist schwer.").size(), is(0));
    assertThat(lt.check("Die Datei heißt Juriest_innenausbieldung.mfgjg und ist leer.").size(), is(0));
    assertThat(lt.check("Alles richtig_stimmt.").size(), is(0)); //not treated by GermanSpellerRule.removeGenderCompoundMatches
    assertThat(lt.check("Das ist flasch_nittrichtig.").size(), is(2)); //not treated by GermanSpellerRule.removeGenderCompoundMatches
  }

  @Test
  public void testMergingOfGrammarCorrections() throws IOException {
    Language lang = new German();
    JLanguageTool lt = new JLanguageTool(lang);
    AnalyzedSentence analyzedSentence = lt.getAnalyzedSentence("Diese Mail ist z Hd Frau Müllers.");

    // Mocking two adjacent grammar issues
    RuleMatch ruleMatch1 = new RuleMatch(new FakeRule("AI_DE_GGEC_TEST"), analyzedSentence, 16, 24, "Adjektivfehler");
    ruleMatch1.setSuggestedReplacement("absolute");
    RuleMatch ruleMatch2 = new RuleMatch(new FakeRule("AI_DE_GGEC_TEST2"), analyzedSentence, 25, 28, "Adjektivfehler");
    ruleMatch2.setSuggestedReplacement("sich");

    List<RuleMatch> ruleMatches = new ArrayList<>();
    ruleMatches.add(ruleMatch1);
    ruleMatches.add(ruleMatch2);

    // Process the rule matches
    List<RuleMatch> processedMatches = lang.filterRuleMatches(ruleMatches, null, null);

    // Asserts
    assertEquals("absolute sich", processedMatches.get(0).getSuggestedReplacements().get(0));
    assertEquals("AI_DE_MERGED_MATCH", processedMatches.get(0).getSpecificRuleId());
  }

  // test that patterns with 'ß' also contain that pattern with 'ss' so the rule can match for de-CH users
  @Test
  public void testSwissSpellingVariants() throws IOException {
    Language lang = Languages.getLanguageForShortCode("de-DE");
    String dirBase = JLanguageTool.getDataBroker().getRulesDir() + "/" + lang.getShortCode() + "/";
    List<String> ignoreIds = Arrays.asList("SCHARFES_SZ", "APOSTROPH_S", "EMPFOHLENE_ZUSAMMENSCHREIBUNG");
    List<String> warnings = new ArrayList<>();
    for (String ruleFileName : lang.getRuleFileNames()) {
      int i = 0;
      InputStream is = this.getClass().getResourceAsStream(ruleFileName);
      List<AbstractPatternRule> rules = new PatternRuleLoader().getRules(is, dirBase + "/" + ruleFileName, lang);
      for (AbstractPatternRule rule : rules) {
        if (ignoreIds.contains(rule.getId())) {
          continue;
        }
        for (DisambiguationPatternRule antiPattern : rule.getAntiPatterns()) {
          for (PatternToken patternToken : antiPattern.getPatternTokens()) {
            String pattern = patternToken.getString();
            if (lacksSwitzerlandSpelling(pattern)) {
              warnings.add(rule.getFullId() + ": " + pattern + " [antipattern]");
              i++;
            }
          }
        }
        if (rule instanceof RegexPatternRule) {
          String pattern = ((RegexPatternRule) rule).getPattern().toString();
          if (lacksSwitzerlandSpelling(pattern)) {
            warnings.add(rule.getFullId() + ": " + pattern);
            i++;
          }
        } else {
          List<PatternToken> patternTokens = rule.getPatternTokens();
          if (patternTokens != null) {
            for (PatternToken patternToken : patternTokens) {
              String pattern = patternToken.getString();
              if (lacksSwitzerlandSpelling(pattern)) {
                warnings.add(rule.getFullId() + ": " + pattern);
                i++;
              }
            }
          }
        }
      }
      if (warnings.size() > 0) {
        System.err.println("*** WARNING: " + ruleFileName + ": " + i + " <token>s with 'ß' but not 'ss' - these will not match for users of German (Switzerland):");
        for (String warning : warnings) {
          System.err.println("  " + warning);
        }
      }
    }
  }

  private boolean lacksSwitzerlandSpelling(String pattern) {
    return pattern != null && pattern.contains("ß") 
      && !pattern.contains("(ß|ss)") 
      && !pattern.contains("(ss|ß)")
      && !containsSwitzerlandSpelling(pattern)
      && !allInBrackets('ß', pattern);
  }

  // only works for e.g.: foo|baß|bla
  private boolean containsSwitzerlandSpelling(String pattern) {
    String[] parts = pattern.split("[|()]");
    for (String part : parts) {
      if (part.contains("ß")) {
        if (!cleanSyntax(pattern).contains(cleanSyntax(part).replace("ß", "ss"))) {
          return false;
        }
      }
    }
    return true;
  }

  private String cleanSyntax(String part) {
    return part.replaceAll("[()]", "");
  }

  private boolean allInBrackets(char searchChar, String pattern) {
    boolean inBrackets = false;
    for (int i = 0; i < pattern.length(); i++) {
      char c = pattern.charAt(i);
      if (c == '[') {
        inBrackets = true;
      } else if (c == ']') {
        inBrackets = false;
      } else if (c == searchChar && !inBrackets) {
        return false;
      }
    }
    return true;
  }

}
