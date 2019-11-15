/*
 * Copyright 2013 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.template.soy.shared.internal;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CaseFormat;
import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import com.google.template.soy.shared.internal.EscapingConventions.EscapingLanguage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import javax.annotation.ParametersAreNonnullByDefault;
import org.apache.tools.ant.Task;

/**
 * Abstract class for generating code relied upon by escaping directives.
 *
 */
@ParametersAreNonnullByDefault
public abstract class AbstractGenerateSoyEscapingDirectiveCode extends Task {

  // Ant looks for a mutator like createFoo methods when it hits a <foo>.
  // Below we define adders for these inner classes.
  // See http://ant.apache.org/manual/develop.html for more details.

  /**
   * A file reference like {@code <input path="foo.txt"/>}. This is the basis for the {@code
   * <input/>} and {@code <output/>} child elements of the Ant task.
   */
  public static final class FileRef {
    /** True iff the file must exist before we can execute the task. */
    private final boolean isInput;
    /** The file that is to be read from or written to. */
    private File file;

    public FileRef(boolean isInput) {
      this.isInput = isInput;
    }

    /** Invoked reflectively by Ant when it sees a {@code path="..."} attribute. */
    public void setPath(String path) throws IOException {
      file = new File(path);
      if (isInput) {
        if (!file.isFile() || !file.canRead()) {
          throw new IOException("Missing input file " + path);
        }
      } else if (file.isDirectory() || !file.getParentFile().isDirectory()) {
        throw new IOException("Cannot write output file " + path);
      }
    }
  }

  /**
   * A wrapper around a library function name predicate like {@code <libdefined name="goog.*"/>}.
   */
  public static final class FunctionNamePredicate {
    /** A regular expression derived from a glob specified in the Ant build file. */
    private Pattern namePattern;

    /**
     * Called reflectively by Ant with the value of the {@code pattern="<glob>"} attribute of the
     * {@code <libdefined>} element.
     */
    public void setPattern(String s) {
      // \Q starts a RegExp literal block, and \E ends one.
      String regex = "\\Q" + s.replace("*", "\\E\\w+\\Q") + "\\E";
      // E.g. "foo.*.bar" -> "\Qfoo.\E\w+\Q.bar\E"
      // which will match anything starting with the literal "foo.", then some identifier chars,
      // then ending with the literal ".bar".
      namePattern = Pattern.compile(regex);
    }
  }

  /** JavaScript source files that use the generated helper functions. */
  private List<FileRef> inputs = Lists.newArrayList();

  /** A file which receives the JavaScript source from the inputs and the generated helpers. */
  private FileRef output;

  /**
   * Matches functions available in the environment in which output will be run including things
   * like {@link EscapingConventions.CrossLanguageStringXform#getLangFunctionNames}.
   */
  protected Predicate<String> availableIdentifiers = functionName -> functionName.indexOf('.') < 0;

  /**
   * A matcher for functions available in the environment in which output will be run including
   * things like {@link EscapingConventions.CrossLanguageStringXform#getLangFunctionNames}.
   */
  public Predicate<String> getAvailableIdentifiers() {
    return availableIdentifiers;
  }

  /**
   * Called reflectively when Ant sees {@code <input>} to specify a file that uses the generated
   * helper functions.
   */
  public FileRef createInput() {
    FileRef ref = new FileRef(true);
    inputs.add(ref);
    return ref;
  }

  /**
   * Called reflectively when Ant sees {@code <output>} to specify the file that should receive the
   * output.
   */
  public FileRef createOutput() {
    if (output != null) {
      throw new IllegalStateException("Too many <output>s");
    }
    output = new FileRef(false);
    return output;
  }

  /** Called reflectively when Ant sees {@code <libdefined>}. */
  public void addConfiguredLibdefined(FunctionNamePredicate p) {
    final Pattern namePattern = p.namePattern;
    if (namePattern == null) {
      throw new IllegalStateException("Please specify a pattern attribute for <libdefined>");
    }
    availableIdentifiers =
        availableIdentifiers.or(identifierName -> namePattern.matcher(identifierName).matches());
  }

  /**
   * Setup method to read arguments and setup initial configuration.
   *
   * @param args Main method input arguments.
   */
  protected void configure(String[] args) throws IOException {
    for (String arg : args) {
      if (arg.startsWith("--input=")) {
        FileRef ref = createInput();
        ref.setPath(arg.substring(arg.indexOf('=') + 1));
      } else if (arg.startsWith("--output=")) {
        FileRef ref = createOutput();
        ref.setPath(arg.substring(arg.indexOf('=') + 1));
      } else if (arg.startsWith("--libdefined=")) {
        FunctionNamePredicate libdefined = new FunctionNamePredicate();
        libdefined.setPattern(arg.substring(arg.indexOf('=') + 1));
        addConfiguredLibdefined(libdefined);
      } else {
        throw new IllegalArgumentException(arg);
      }
    }
  }

  /** Called to actually build the output by Ant. */
  @Override
  public void execute() {
    super.execute();
    if (output == null) {
      System.err.println(
          "Please add an <output> for the <" + getTaskName() + "> at " + this.getLocation());
      return;
    }

    // Gather output in a buffer rather than generating a bad file with a valid timestamp.
    StringBuilder sb = new StringBuilder();

    // Output the source files that use the helper functions first, so we get the appropriate file
    // overviews and copyright headers.
    for (FileRef input : inputs) {
      try {
        boolean inGeneratedCode = false;
        for (String line : Files.readLines(input.file, Charsets.UTF_8)) {
          // Skip code between generated code markers so that this transformation is idempotent.
          // We can run an old output through this class, and get the latest version out.
          if (inGeneratedCode) {
            if (generatedCodeEndMarker.equals(line.trim())) {
              inGeneratedCode = false;
            }
          } else if (generatedCodeStartMarker.equals(line.trim())) {
            inGeneratedCode = true;
          } else {
            sb.append(line).append('\n');
          }
        }
      } catch (IOException ex) {
        System.err.println("Failed to read " + input.file);
        ex.printStackTrace();
        return;
      }
    }

    // Generate helper functions for escape directives.
    generateCode(availableIdentifiers, sb);

    // Output a file now that we know generation hasn't failed.
    try {
      Files.asCharSink(output.file, Charsets.UTF_8).write(sb);
    } catch (IOException ex) {
      // Make sure an abortive write does not leave a file w
      output.file.delete();
    }
  }

  /** A line that precedes the rest of the generated code. */
  public final String generatedCodeStartMarker =
      getLineCommentSyntax() + " START GENERATED CODE FOR ESCAPERS.";

  /** A line that follows the rest of the generated code. */
  public final String generatedCodeEndMarker = getLineCommentSyntax() + " END GENERATED CODE";

  /**
   * Appends Code to the given buffer.
   *
   * <p>The output Code contains symbol definitions in the appropriate scope (the soy namespace in
   * JavaScript or a module in Python).
   *
   * <pre>
   *   ...ESCAPES_FOR_ESCAPE_HTML_  = { ... }  // Maps of characters to escaped versions
   *   ...MATCHER_FOR_ESCAPE_HTML_  = &lt;regex&gt;  // A single character matching RegExp
   *   ...REPLACER_FOR_ESCAPE_HTML_ = &lt;function&gt;  // Usable with replace functions
   *   ...FILTER_FOR_ESCAPE_HTML_ = &lt;regex&gt;  // Optional regular expression that vets values.
   *   // A function that uses the above definitions.
   *   ...escapeHtmlHelper = &lt;function&gt;
   * </pre>
   *
   * <p>There is not necessarily a one-to-one relationship between any of the symbols above and
   * escape directives except for the {@code ...escape...Helper} function.
   *
   * @param availableIdentifiers Determines whether a qualified identifier, like {@code
   *     goog.foo.Bar}, is available.
   * @param outputCode Receives output code.
   */
  @VisibleForTesting
  public void generateCode(Predicate<String> availableIdentifiers, StringBuilder outputCode) {

    outputCode.append(generatedCodeStartMarker).append('\n');

    // Before entering the real logic, generate any needed prefix.
    generatePrefix(outputCode);

    // First we collect all the side tables.

    // Like { '\n': '\\n', ... } that map characters to escape.
    List<Map<Character, String>> escapeMaps = Lists.newArrayList();
    // Mangled directive names corresponding to escapeMaps used to generate <namespace>..._ names.
    List<String> escapeMapNames = Lists.newArrayList();
    // Like /[\n\r'"]/g or r'[\n\r\'"]'that match all the characters that need escaping.
    List<String> matchers = Lists.newArrayList();
    // Mangled directive names corresponding to matchers.
    List<String> matcherNames = Lists.newArrayList();
    // RegExps that vet input values.
    List<String> filters = Lists.newArrayList();
    // Mangled directive names corresponding to filters.
    List<String> filterNames = Lists.newArrayList();
    // Bundles of directiveNames and indices into escapeMaps, matchers, etc.
    List<DirectiveDigest> digests = Lists.newArrayList();

    escaperLoop:
    for (EscapingConventions.CrossLanguageStringXform escaper :
        EscapingConventions.getAllEscapers()) {
      // "|escapeHtml" -> "escapeHtml"
      String escapeDirectiveIdent = escaper.getDirectiveName().substring(1);
      // "escapeHtml" -> "ESCAPE_HTML"
      String escapeDirectiveUIdent =
          CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_UNDERSCORE, escapeDirectiveIdent);

      // If there is an existing function, use it.
      for (String existingFunction : escaper.getLangFunctionNames(getLanguage())) {
        if (availableIdentifiers.test(existingFunction)) {
          useExistingLibraryFunction(outputCode, escapeDirectiveIdent, existingFunction);
          continue escaperLoop;
        }
      }

      // Else generate definitions for side tables.
      int escapesVar = -1;
      int matcherVar = -1;
      if (!escaper.getEscapes().isEmpty()) {
        Map<Character, String> escapeMap = Maps.newTreeMap();
        StringBuilder matcherRegexBuf = new StringBuilder(getRegexStart() + "[");
        int lastCodeUnit = Integer.MIN_VALUE;
        int rangeStart = Integer.MIN_VALUE;
        for (EscapingConventions.Escape esc : escaper.getEscapes()) {
          char ch = esc.getPlainText();
          if (ch == lastCodeUnit) {
            throw new IllegalStateException(
                "Ambiguous escape " + esc.getEscaped() + " for " + escapeDirectiveIdent);
          }
          escapeMap.put(ch, esc.getEscaped());
          if (ch != lastCodeUnit + 1) {
            if (rangeStart != Integer.MIN_VALUE) {
              escapeRegexpRangeOnto((char) rangeStart, (char) lastCodeUnit, matcherRegexBuf);
            }
            rangeStart = ch;
          }
          lastCodeUnit = ch;
        }
        if (rangeStart < 0) {
          throw new IllegalStateException();
        }
        escapeRegexpRangeOnto((char) rangeStart, (char) lastCodeUnit, matcherRegexBuf);
        matcherRegexBuf.append("]").append(getRegexEnd());

        // See if we can reuse an existing map.
        int numEscapeMaps = escapeMaps.size();
        for (int i = 0; i < numEscapeMaps; ++i) {
          if (mapsHaveCompatibleOverlap(escapeMaps.get(i), escapeMap)) {
            escapesVar = i;
            break;
          }
        }
        if (escapesVar == -1) {
          escapesVar = numEscapeMaps;
          escapeMaps.add(escapeMap);
          escapeMapNames.add(escapeDirectiveUIdent);
        } else {
          escapeMaps.get(escapesVar).putAll(escapeMap);
          // ESCAPE_JS -> ESCAPE_JS_STRING__AND__ESCAPE_JS_REGEX
          escapeMapNames.set(
              escapesVar, escapeMapNames.get(escapesVar) + "__AND__" + escapeDirectiveUIdent);
        }

        String matcherRegex = matcherRegexBuf.toString();
        matcherVar = matchers.indexOf(matcherRegex);
        if (matcherVar < 0) {
          matcherVar = matchers.size();
          matchers.add(matcherRegex);
          matcherNames.add(escapeDirectiveUIdent);
        } else {
          matcherNames.set(
              matcherVar, matcherNames.get(matcherVar) + "__AND__" + escapeDirectiveUIdent);
        }
      }

      // Find a suitable filter or add one to filters.
      int filterVar = -1;
      Pattern filterPatternJava = escaper.getValueFilter();
      if (filterPatternJava != null) {
        // This is an approximate translation from Java patterns to JavaScript patterns.
        String filterPattern = convertFromJavaRegex(filterPatternJava);
        filterVar = filters.indexOf(filterPattern);
        if (filterVar == -1) {
          filterVar = filters.size();
          filters.add(filterPattern);
          filterNames.add(escapeDirectiveUIdent);
        } else {
          filterNames.set(
              filterVar, filterNames.get(filterVar) + "__AND__" + escapeDirectiveUIdent);
        }
      }

      digests.add(
          new DirectiveDigest(
              escapeDirectiveIdent,
              escapesVar,
              matcherVar,
              filterVar,
              escaper.getNonAsciiPrefix(),
              escaper.getInnocuousOutput()));
    }

    // TODO(msamuel): Maybe use java Soy templates to generate the JS?

    // Output the tables.
    for (int i = 0; i < escapeMaps.size(); ++i) {
      Map<Character, String> escapeMap = escapeMaps.get(i);
      String escapeMapName = escapeMapNames.get(i);
      generateCharacterMapSignature(outputCode, escapeMapName);
      outputCode.append(" = {");
      for (Map.Entry<Character, String> e : escapeMap.entrySet()) {
        outputCode.append("\n  ");
        writeUnsafeStringLiteral(e.getKey(), outputCode);
        outputCode.append(": ");
        writeStringLiteral(e.getValue(), outputCode);
        outputCode.append(",");
      }
      outputCode.append("\n}").append(getLineEndSyntax()).append("\n");

      generateReplacerFunction(outputCode, escapeMapName);
    }

    for (int i = 0; i < matchers.size(); ++i) {
      String matcherName = matcherNames.get(i);
      String matcher = matchers.get(i);
      generateMatcher(outputCode, matcherName, matcher);
    }

    for (int i = 0; i < filters.size(); ++i) {
      String filterName = filterNames.get(i);
      String filter = filters.get(i);
      generateFilter(outputCode, filterName, filter);
    }

    // Finally, define the helper functions that use the escapes, filters, matchers, etc.
    for (DirectiveDigest digest : digests) {
      digest.updateNames(escapeMapNames, matcherNames, filterNames);
      generateHelperFunction(outputCode, digest);
    }

    // Emit patterns and constants needed by escaping functions that are not part of any one
    // escaping convention.
    generateCommonConstants(outputCode);

    outputCode.append('\n').append(generatedCodeEndMarker).append('\n');
  }

  /**
   * True if the two maps have at least one (key, value) pair in common, and no pairs with the same
   * key but different values according to {@link Object#equals}.
   */
  private static <K, V> boolean mapsHaveCompatibleOverlap(Map<K, V> a, Map<K, V> b) {
    if (b.size() < a.size()) {
      Map<K, V> t = a;
      a = b;
      b = t;
    }
    boolean overlap = false;
    for (Map.Entry<K, V> e : a.entrySet()) {
      V value = b.get(e.getKey());
      if (value != null) {
        if (!value.equals(e.getValue())) {
          return false;
        }
        overlap = true;
      } else if (b.containsKey(e.getKey())) {
        if (e.getValue() != null) {
          return false;
        }
        overlap = true;
      }
    }
    return overlap;
  }

  /** Appends a string literal with the given value onto the given buffer. */
  protected void writeStringLiteral(String value, StringBuilder out) {
    out.append('\'').append(escapeOutputString(value)).append('\'');
  }

  /**
   * Appends a string literal which may not be printable with the given value onto the given buffer.
   */
  private void writeUnsafeStringLiteral(char value, StringBuilder out) {
    if (!isPrintable(value)) {
      // Don't emit non-Latin characters or control characters since they don't roundtrip well.
      out.append(String.format(value >= 0x100 ? "'\\u%04x'" : "'\\x%02x'", (int) value));
    } else {
      out.append('\'').append(escapeOutputString(String.valueOf(value))).append('\'');
    }
  }

  /**
   * Appends a RegExp character range set onto the given buffer. E.g. given the letters 'a' and 'z'
   * as start and end, appends {@code a-z}. These are meant to be concatenated to create character
   * sets like {@code /[a-zA-Z0-9]/}. This method will omit unnecessary ends or range separators.
   */
  private static void escapeRegexpRangeOnto(char start, char end, StringBuilder out) {
    if (!isPrintable(start)) {
      out.append(String.format(start >= 0x100 ? "\\u%04x" : "\\x%02x", (int) start));
    } else {
      out.append(EscapingConventions.EscapeJsRegex.INSTANCE.escape(String.valueOf(start)));
    }
    if (start != end) {
      // If end - start is 1, then don't bother to put a dash.  [a-b] is the same as [ab].
      if (end - start > 1) {
        out.append('-');
      }
      if (!isPrintable(end)) {
        out.append(String.format(end >= 0x100 ? "\\u%04x" : "\\x%02x", (int) end));
      } else {
        out.append(EscapingConventions.EscapeJsRegex.INSTANCE.escape(String.valueOf(end)));
      }
    }
  }

  /** True iff ch is not a control character or non-Latin character. */
  private static boolean isPrintable(char ch) {
    return 0x20 <= ch && ch <= 0x7e;
  }

  /**
   * Get the language being generated here.
   *
   * @return The language being generated.
   */
  protected abstract EscapingLanguage getLanguage();

  /**
   * Return the syntax for starting a one line comment.
   *
   * @return The syntax for starting a one line comment.
   */
  protected abstract String getLineCommentSyntax();

  /**
   * Return the syntax for ending a line.
   *
   * @return The syntax for ending a line.
   */
  protected abstract String getLineEndSyntax();

  /**
   * Return the syntax for starting a regex string.
   *
   * @return The syntax for starting a regex string.
   */
  protected abstract String getRegexStart();

  /**
   * Return the syntax for ending a regex string.
   *
   * @return The syntax for ending a regex string.
   */
  protected abstract String getRegexEnd();

  /**
   * Escape a generated string being outputted based on the current language being generated.
   *
   * @return The input string escaped for the current output language.
   */
  protected abstract String escapeOutputString(String input);

  /**
   * Converts the given pattern from a Java Pattern syntax to the language specific syntax.
   *
   * @param javaPattern The Java regular expression to convert.
   * @return A string representing the given pattern syntax in the specific language.
   */
  protected abstract String convertFromJavaRegex(Pattern javaPattern);

  /**
   * Generate any prefix needed to precede the escapers. This may include library imports and other
   * similar setup tasks.
   *
   * @param outputCode The StringBuilder where generated code should be appended.
   * @return A string containing the prefix for the generated sanitizer.
   */
  protected void generatePrefix(StringBuilder outputCode) {
    /* NOOP by default. */
  }

  /**
   * Generate the signature to a map object to hold character mapping. All necessary comments and
   * variable declarations should be included in the signature. The map assignment will be appended
   * to the signature.
   *
   * <p>For example. In Javascript the following should be returned:
   *
   * <pre>
   *   /**
   *    * Maps characters to the escaped versions for the named escape directives.\n")
   *    * @type {Object<string, string>}\n")
   *    * @private\n")
   *    *\/
   *     soy.esc.$$ESCAPE_MAP_FOR_&lt;NAME&gt;_
   * </pre>
   *
   * @param outputCode The StringBuilder where generated code should be appended.
   * @param mapName The name of this map.
   */
  protected abstract void generateCharacterMapSignature(StringBuilder outputCode, String mapName);

  /**
   * Generate the constant to store the given matcher regular expression.
   *
   * @param outputCode The StringBuilder where generated code should be appended.
   * @param name The name of the current matcher.
   * @param matcher The regular expression string.
   */
  protected abstract void generateMatcher(StringBuilder outputCode, String name, String matcher);

  /**
   * Generate the constant to store the given filter regular expression.
   *
   * @param outputCode The StringBuilder where generated code should be appended.
   * @param name The name of the current filter.
   * @param filter The regular expression string.
   */
  protected abstract void generateFilter(StringBuilder outputCode, String name, String filter);

  /**
   * Generate the function to handle replacement of a given character.
   *
   * @param outputCode The StringBuilder where generated code should be appended.
   * @param mapName The name of the map to use for replacement.
   */
  protected abstract void generateReplacerFunction(StringBuilder outputCode, String mapName);

  /**
   * Use an existing library function to execute the escaping, filtering, and replacing.
   *
   * @param outputCode The StringBuilder where generated code should be appended.
   * @param identifier The identifier of the escape directive.
   * @param existingFunction The existing function to reuse.
   */
  protected abstract void useExistingLibraryFunction(
      StringBuilder outputCode, String identifier, String existingFunction);

  /**
   * Generate the helper function to execute the escaping, filtering, and replacing.
   *
   * @param outputCode The StringBuilder where generated code should be appended.
   * @param digest The DirectiveDigest which contains the appropriate patterns and replacer keys.
   */
  protected abstract void generateHelperFunction(StringBuilder outputCode, DirectiveDigest digest);

  /**
   * Generate common constants used elsewhere by the utility.
   *
   * @param outputCode The StringBuilder where generated code should be appended.
   */
  protected abstract void generateCommonConstants(StringBuilder outputCode);
}
