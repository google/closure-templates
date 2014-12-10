package com.google.template.soy.pomsgplugin;

import com.google.template.soy.internal.base.Pair;
import com.google.template.soy.msgs.SoyMsgBundle;
import com.google.template.soy.msgs.restricted.SoyMsg;
import com.google.template.soy.msgs.restricted.SoyMsgPart;
import com.google.template.soy.msgs.restricted.SoyMsgPlaceholderPart;
import com.google.template.soy.msgs.restricted.SoyMsgPluralCaseSpec;
import com.google.template.soy.msgs.restricted.SoyMsgRawTextPart;
import com.google.template.soy.msgs.restricted.SoyMsgPluralPart;
import com.google.template.soy.msgs.restricted.SoyMsgSelectPart;
import java.util.List;

import javax.annotation.Nullable;

/**
 * Generates gettext-format PO files from SoyMsgBundles.
 *
 * See http://www.gnu.org/software/gettext/manual/html_node/PO-Files.html
 *
 * @author Stephen Searles <stephen@leapingbrain.com>
 */
public class PoGenerator {

   private PoGenerator() {}


  /**
   * Generates the output PO file content for a given SoyMsgBundle.
   *
   * @param msgBundle The SoyMsgBundle to process.
   * @param sourceLocaleString The source language/locale string of the messages.
   * @param targetLocaleString The target language/locale string of the messages (optional). If
   *     specified, the resulting PO file will specify this target language and will contain
   *     empty 'target' tags. If not specified, the resulting PO file will not contain target
   *     language and will not contain 'target' tags.
   * @return The generated PO file content.
   */
  static CharSequence generatePo(
      SoyMsgBundle msgBundle,
      String sourceLocaleString,
      @Nullable String targetLocaleString) {

    StringBuilder poBuilder = new StringBuilder();

    for (SoyMsg msg : msgBundle) {
      poBuilder.append(generateMessage(msg));
    }

    return poBuilder;
  }

  /**
   * Generate an individual message
   */
  private static String generateMessage(SoyMsg msg) {
    StringBuilder msgBuilder = new StringBuilder();
    String meaning = msg.getMeaning();
    if (meaning != null && meaning.length() > 0) {
      msgBuilder.append("# Meaning: ").append(meaning).append("\n");
    }

    msgBuilder.append("#: id=").append(msg.getId()).append("\n");
    msgBuilder.append("#: type=").append(msg.getContentType()).append("\n");

    SoyMsgPart firstPart = msg.getParts().get(0);
    if (firstPart instanceof SoyMsgPluralPart && msg.getParts().size() > 1) {
      throw new PoException(
              "No message content is allowed before or after a "
              + "plural block. Found: " + msg.getParts().get(1).toString());
    } else if (firstPart instanceof SoyMsgPluralPart) {
      msgBuilder.append(generatePluralVar((SoyMsgPluralPart)firstPart));
      msgBuilder.append(generateDescription(msg));
      msgBuilder.append(generatePluralMessage((SoyMsgPluralPart)firstPart));
    } else {
      msgBuilder.append(generateDescription(msg));
      msgBuilder.append("msgid \"");
      msgBuilder.append(generateSingularMessage(msg));
      msgBuilder.append("\"\n");
    }

    // Add an empty translation
    msgBuilder.append("msgstr \"\"\n\n");
    return msgBuilder.toString();
  }

  /**
   * Generate a non-plural message.
   *
   * @throws PoException Thrown when a plural block is found.
   */
  private static String generateSingularMessage(SoyMsg msg) {
    StringBuilder msgBuilder = new StringBuilder();
    for (SoyMsgPart msgPart : msg.getParts()) {
      if (msgPart instanceof SoyMsgPluralPart) {
        throw new PoException("No message content is allowed before or after a "
                + "plural block. Found: " + msgPart);
      }
      msgBuilder.append(message(msgPart));
    }
    return msgBuilder.toString();
  }

  /**
   * Generate a comment line designating the variable controlling pluralization.
   *
   * E.g. => #: pluralVar=NUM_ITEMS_1
   *
   * The syntax of this line is a use of the PO automatic comments feature to
   * pass on the plural variable that will be parsed and used by the
   * SoyToJsSrcCompiler and the Closure Compiler when inserting translated
   * messages into usable source code.
   *
   */
  private static String generatePluralVar(SoyMsgPluralPart pluralPart) {
    return "#: pluralVar=" + pluralPart.getPluralVarName() + "\n";
  }

  /**
   * Generate a description line.
   *
   * Returns an empty string if the message has no description.
   */
  private static String generateDescription(SoyMsg msg) {
    String desc = msg.getDesc();
    if (desc != null && desc.length() > 0) {
      return "msgctxt \"" + desc + "\"\n";
    }
    return "";
  }

  /**
   * Extracts a message from a SoyMsgPart into PO format.
   *
   * Newlines and backslashes are escaped.
   * This method does not handle {plural} or {select}.
   *
   */
  static String message(SoyMsgPart msgPart) {
    if (msgPart instanceof SoyMsgRawTextPart) {
      return escapeString(((SoyMsgRawTextPart) msgPart).getRawText());
    } else if (msgPart instanceof SoyMsgPluralPart) {
      throw new PoException("PO generation does not support embedded {plural}."
              + " {plural} must wrap entire message.");
    } else if (msgPart instanceof SoyMsgSelectPart) {
      throw new PoException("PO generation does not support {select}.");
    } else if (msgPart instanceof SoyMsgPlaceholderPart) {
      String placeholderName =
              ((SoyMsgPlaceholderPart) msgPart).getPlaceholderName();
      return "{$" + placeholderName + "}";
    } else {
      throw new PoException("Unexpected message part type: " + msgPart);
    }
  }

  static String escapeString(String s) {
    return s.replace("\"", "\\\"").replace("\n","\\n\"\n\"");
  }

  /**
   * Extracts the PO style plural message from the SoyMsgPluralPart.
   * @throws PoException
   */
  static String generatePluralMessage(SoyMsgPluralPart msgPart)
          throws PoException {
    StringBuilder msgBuilder = new StringBuilder();

    for (Pair<SoyMsgPluralCaseSpec, List<SoyMsgPart>> pluralPart :
            msgPart.getCases()) {

      if (pluralPart.first.getType() == SoyMsgPluralCaseSpec.Type.EXPLICIT &&
          pluralPart.first.getExplicitValue() == 1) {
        msgBuilder.append("msgid \"");
        for (SoyMsgPart pluralSubPart : pluralPart.second) {
            msgBuilder.append(message(pluralSubPart));
        }
        msgBuilder.append("\"\n");
      } else if (pluralPart.first.getType() == SoyMsgPluralCaseSpec.Type.OTHER) {
        msgBuilder.append("msgid_plural \"");
        for (SoyMsgPart pluralSubPart : pluralPart.second) {
          msgBuilder.append(message(pluralSubPart));
        }
        msgBuilder.append("\"\n");
      } else {
        throw new PoException("PO only supports explicit 1 and N variants,"
                + " {case 1} and {default}, respectively.");
      }
    }
    return msgBuilder.toString();
  }

}
