/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.google.template.soy.pomsgplugin;

import com.google.template.soy.internal.base.Pair;
import com.google.template.soy.msgs.SoyMsgBundle;
import com.google.template.soy.msgs.SoyMsgException;
import com.google.template.soy.msgs.restricted.SoyMsg;
import com.google.template.soy.msgs.restricted.SoyMsgBundleImpl;
import com.google.template.soy.msgs.restricted.SoyMsgPart;
import com.google.template.soy.msgs.restricted.SoyMsgPlaceholderPart;
import com.google.template.soy.msgs.restricted.SoyMsgPluralCaseSpec;
import com.google.template.soy.msgs.restricted.SoyMsgPluralPart;
import com.google.template.soy.msgs.restricted.SoyMsgRawTextPart;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.ArrayList;
import java.util.InputMismatchException;
import java.util.Scanner;
import java.util.regex.Pattern;

/**
 *
 * @author Stephen Searles <stephen@leapingbrain.com>
 */
public class PoParser {

  private PoParser() {}

  /**
   * Parses the content of a translated XLIFF file and creates a SoyMsgBundle.
   *
   * @param poContent The PO content to parse.
   * @return The resulting SoyMsgBundle.
   * @throws PoException If there's an error parsing the data.
   * @throws SoyMsgException If there's an error in parsing the data.
   */
  static SoyMsgBundle parsePoTargetMsgs(String poContent) {
    ArrayList messages = new ArrayList<SoyMsg>();

    Scanner scanner = new Scanner(poContent);
    scanner.useDelimiter("\n\n");

    while (scanner.hasNext()) {
      messages.add(parseMessage(scanner.next()));
    }

    return new SoyMsgBundleImpl("xx", messages);
  }

  private static SoyMsg parseMessage(String messageContent) {

    long id = 0;
    String locale = "xx";
    ArrayList<SoyMsgPart> parts = new ArrayList<SoyMsgPart>();
    boolean isPlural = false;
    String meaning = null;
    String desc = null;

    Scanner scanner = new Scanner(messageContent);

    StringBuilder pluralTranslationLines = new StringBuilder();

    while (scanner.hasNextLine()) {
      String line = scanner.nextLine();
      if (line.startsWith("#: id=")) {
        String idString = line.substring(6);
        id = Long.parseLong(idString);
      } else if (line.startsWith("msgid_plural")) {
        isPlural = true;
      } else if (line.startsWith("msgstr ")) {
        parseTranslationLine(line, parts);
      } else if (line.startsWith("msgstr[")) {
        pluralTranslationLines.append(line);
        pluralTranslationLines.append("\n");
      }
    }

    if (isPlural) {
      if (parts.size() > 0) {
        throw new PoException("Cannot mix plural and non-pluralized translations.");
      }
      parts.add(parsePluralTranslation(pluralTranslationLines.toString()));
    }

    return new SoyMsg(id, -1L, locale, meaning, desc, false, null, null, isPlural, parts);
  }

  private static SoyMsgPluralPart parsePluralTranslation(String translationLines) {

    ArrayList<Pair<SoyMsgPluralCaseSpec, ImmutableList<SoyMsgPart>>> cases
        = new ArrayList<Pair<SoyMsgPluralCaseSpec, ImmutableList<SoyMsgPart>>>();

    Scanner scanner = new Scanner(translationLines);
    scanner.useDelimiter("\n");

    while (scanner.hasNext()) {
      cases.add(parsePluralTranslationLine(scanner.nextLine()));
    }

    return new SoyMsgPluralPart("varName", 0, ImmutableList.copyOf(cases));
  }

  private static void parseTranslationLine(String translationLine, ArrayList<SoyMsgPart> parts) {
    Scanner scanner = new Scanner(translationLine);
    scanner.useDelimiter("'|\"");

    scanner.next();
    parseTranslationString(scanner.next(), parts);
  }

  private static void parseTranslationString(String translationString, ArrayList<SoyMsgPart> parts) {
    Scanner scanner = new Scanner(translationString);
    scanner.useDelimiter(Pattern.compile("\\{\\$|\\}"));
    boolean inVariableToken = false;

    while (scanner.hasNext()) {
      if (inVariableToken) {
        parts.add(new SoyMsgPlaceholderPart(scanner.next()));
      } else {
        parts.add(SoyMsgRawTextPart.of(scanner.next()));
      }
      inVariableToken = !inVariableToken;
    }

    if (scanner.hasNextLine()) {
      String remainder = scanner.nextLine();
      if (remainder.length() > 0) {
        parts.add(SoyMsgRawTextPart.of(remainder));
      }
    }
  }

  private static Pair<SoyMsgPluralCaseSpec, ImmutableList<SoyMsgPart>> parsePluralTranslationLine(String translationLine) {
    int n;
    Scanner scanner = new Scanner(translationLine);
    scanner.useDelimiter("\\[|\\]");
    try {
      scanner.next();
    } catch (InputMismatchException e) {
      throw new PoException("Incorrect plural translation line: ".concat(translationLine));
    }
    n = scanner.nextInt();
    SoyMsgPluralCaseSpec caseSpec = new SoyMsgPluralCaseSpec(n);
    ArrayList<SoyMsgPart> parts = new ArrayList<SoyMsgPart>();

    parseTranslationLine(scanner.nextLine(), parts);

    return new Pair<SoyMsgPluralCaseSpec, ImmutableList<SoyMsgPart>>(caseSpec, ImmutableList.copyOf(parts));
  }
}
