/*
 * Copyright 2008 Google Inc.
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

package com.google.template.soy.parseinfo.passes;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CaseFormat;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.template.soy.base.BaseUtils;
import com.google.template.soy.base.IndentedLinesBuilder;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.internal.base.Pair;
import com.google.template.soy.parseinfo.SoyFileInfo.CssTagsPrefixPresence;
import com.google.template.soy.sharedpasses.FindIndirectParamsVisitor;
import com.google.template.soy.sharedpasses.FindIndirectParamsVisitor.IndirectParamsInfo;
import com.google.template.soy.sharedpasses.FindUsedIjParamsVisitor;
import com.google.template.soy.sharedpasses.FindUsedIjParamsVisitor.UsedIjParamsInfo;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.CssNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.TemplateBasicNode;
import com.google.template.soy.soytree.TemplateDelegateNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.TemplateNode.SoyDocParam;
import com.google.template.soy.soytree.TemplateRegistry;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Visitor for generating Java classes containing the parse info.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * <p> {@link #exec} should be called on a full parse tree.
 *
 * <p> For an example Soy file and its corresponding generated code, see
 * <pre>
 *     [tests_dir]/com/google/template/soy/test_data/AaaBbbCcc.soy
 *     [tests_dir]/com/google/template/soy/test_data/AaaBbbCccSoyInfo.java
 * </pre>
 *
 */
public class GenerateParseInfoVisitor extends AbstractSoyNodeVisitor<ImmutableMap<String, String>> {


  /**
   * Represents the source of the generated Java class names.
   */
  @VisibleForTesting static enum JavaClassNameSource {

    /** AaaBbb.soy or aaa_bbb.soy --> AaaBbbSoyInfo. */
    SOY_FILE_NAME,

    /** boo.foo.aaaBbb --> AaaBbbSoyInfo. */
    SOY_NAMESPACE_LAST_PART,

    /** File1SoyInfo, File2SoyInfo, etc. */
    GENERIC;


    /** Pattern for an all-upper-case word in a file name or identifier. */
    private static final Pattern ALL_UPPER_WORD =
        Pattern.compile("(?<= [^A-Za-z] | ^)  [A-Z]+  (?= [^A-Za-z] | $)", Pattern.COMMENTS);

    /** Pattern for an all-lower-case word in a file name or identifier. */
    // Note: Char after an all-lower word can be an upper letter (e.g. first word of camel case).
    private static final Pattern ALL_LOWER_WORD =
        Pattern.compile("(?<= [^A-Za-z] | ^)  [a-z]+  (?= [^a-z] | $)", Pattern.COMMENTS);

    /** Pattern for a character that's not a letter nor a digit. */
    private static final Pattern NON_LETTER_DIGIT = Pattern.compile("[^A-Za-z0-9]");


    /**
     * Generates the base Java class name for the given Soy file.
     * @param soyFile The Soy file.
     * @return The generated base Java class name (without any suffixes).
     */
    @VisibleForTesting String generateBaseClassName(SoyFileNode soyFile) {

      switch (this) {

        case SOY_FILE_NAME:
          String fileName = soyFile.getFileName();
          if (fileName == null) {
            throw new IllegalArgumentException(
                "Trying to generate Java class name based on Soy file name, but Soy file name was" +
                " not provided.");
          }
          if (fileName.toLowerCase().endsWith(".soy")) {
            fileName = fileName.substring(0, fileName.length() - 4);
          }
          return makeUpperCamelCase(fileName);

        case SOY_NAMESPACE_LAST_PART:
          String namespace = soyFile.getNamespace();
          String namespaceLastPart = namespace.substring(namespace.lastIndexOf('.') + 1);
          return makeUpperCamelCase(namespaceLastPart);

        case GENERIC:
          return "File";

        default:
          throw new AssertionError();
      }
    }


    /**
     * Creates the upper camel case version of the given string (can be file name or identifier).
     * @param str The string to turn into upper camel case.
     * @return The upper camel case version of the string.
     */
    private static String makeUpperCamelCase(String str) {

      str = makeWordsCapitalized(str, ALL_UPPER_WORD);
      str = makeWordsCapitalized(str, ALL_LOWER_WORD);
      str = NON_LETTER_DIGIT.matcher(str).replaceAll("");
      return str;
    }


    /**
     * Makes all the words in the given string into capitalized format (first letter capital, rest
     * lower case). Words are defined by the given regex pattern.
     * @param str The string to process.
     * @param wordPattern The regex pattern for matching a word.
     * @return The resulting string with all words in capitalized format.
     */
    private static String makeWordsCapitalized(String str, Pattern wordPattern) {

      StringBuffer sb = new StringBuffer();

      Matcher wordMatcher = wordPattern.matcher(str);
      while (wordMatcher.find()) {
        String oldWord =  wordMatcher.group();
        StringBuilder newWord = new StringBuilder();
        for (int i = 0, n = oldWord.length(); i < n; i++) {
          if (i == 0) {
            newWord.append(Character.toUpperCase(oldWord.charAt(i)));
          } else {
            newWord.append(Character.toLowerCase(oldWord.charAt(i)));
          }
        }
        wordMatcher.appendReplacement(sb, Matcher.quoteReplacement(newWord.toString()));
      }
      wordMatcher.appendTail(sb);

      return sb.toString();
    }
  }


  /** The package name of the generated files. */
  private final String javaPackage;

  /** The source of the generated Java class names. */
  private final JavaClassNameSource javaClassNameSource;

  /** Map from Soy file node to generated Java class name (built at start of pass). */
  private Map<SoyFileNode, String> soyFileToJavaClassNameMap;

  /** Registry of all templates in the Soy tree. */
  private TemplateRegistry templateRegistry;

  /** Cache for results of calls to {@code Utils.convertToUpperUnderscore()}. */
  private final Map<String, String> convertedIdents = Maps.newHashMap();

  /** The contents of the generated JS files. */
  private LinkedHashMap<String, String> generatedFiles;

  /** Builder for the generated code. */
  private IndentedLinesBuilder ilb;


  /**
   * @param javaPackage The Java package for the generated classes.
   * @param javaClassNameSource Source of the generated class names. Must be one of "filename",
   *     "namespace", or "generic".
   */
  public GenerateParseInfoVisitor(String javaPackage, String javaClassNameSource) {

    this.javaPackage = javaPackage;

    if (javaClassNameSource.equals("filename")) {
      this.javaClassNameSource = JavaClassNameSource.SOY_FILE_NAME;
    } else if (javaClassNameSource.equals("namespace")) {
      this.javaClassNameSource = JavaClassNameSource.SOY_NAMESPACE_LAST_PART;
    } else if (javaClassNameSource.equals("generic")) {
      this.javaClassNameSource = JavaClassNameSource.GENERIC;
    } else {
      throw new IllegalArgumentException(
          "Invalid value for javaClassNameSource \"" + javaClassNameSource + "\"" +
          " (valid values are \"filename\", \"namespace\", and \"generic\").");
    }
  }


  @Override public ImmutableMap<String, String> exec(SoyNode node) {
    generatedFiles = Maps.newLinkedHashMap();
    ilb = null;
    visit(node);
    return ImmutableMap.copyOf(generatedFiles);
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for specific nodes.


  @Override protected void visitSoyFileSetNode(SoyFileSetNode node) {

    // Figure out the generated class name for each Soy file, including adding number suffixes
    // to resolve collisions, and then adding the common suffix "SoyInfo".
    Multimap<String, SoyFileNode> baseGeneratedClassNameToSoyFilesMap = HashMultimap.create();
    for (SoyFileNode soyFile : node.getChildren()) {
      baseGeneratedClassNameToSoyFilesMap.put(
          javaClassNameSource.generateBaseClassName(soyFile), soyFile);
    }
    soyFileToJavaClassNameMap = Maps.newHashMap();
    for (String baseClassName : baseGeneratedClassNameToSoyFilesMap.keySet()) {
      Collection<SoyFileNode> soyFiles = baseGeneratedClassNameToSoyFilesMap.get(baseClassName);
      if (soyFiles.size() == 1) {
        for (SoyFileNode soyFile : soyFiles) {
          soyFileToJavaClassNameMap.put(soyFile, baseClassName + "SoyInfo");
        }
      } else {
        int numberSuffix = 1;
        for (SoyFileNode soyFile : soyFiles) {
          soyFileToJavaClassNameMap.put(soyFile, baseClassName + numberSuffix + "SoyInfo");
          numberSuffix++;
        }
      }
    }

    // Build template registry.
    templateRegistry = new TemplateRegistry(node);

    // Run the pass.
    for (SoyFileNode soyFile : node.getChildren()) {
      try {
        visit(soyFile);
      } catch (SoySyntaxException sse) {
        throw sse.setFilePath(soyFile.getFilePath());
      }
    }
  }


  @Override protected void visitSoyFileNode(SoyFileNode node) {

    if (node.getFilePath() == null) {
      throw new SoySyntaxException(
          "In order to generate parse info, all Soy files must have paths (file name is" +
          " extracted from the path).");
    }

    String javaClassName = soyFileToJavaClassNameMap.get(node);

    // Collect the following:
    // + all the partial template names of public basic templates (non-private, non-delegate),
    // + all the param keys from all templates (including private),
    // + for each param key, the list of templates that list it directly.
    List<String> publicBasicTemplatePartialNames = Lists.newArrayList();
    Set<String> allParamKeys = Sets.newHashSet();
    LinkedHashMultimap<String, TemplateNode> paramKeyToTemplatesMultimap =
        LinkedHashMultimap.create();
    for (TemplateNode template : node.getChildren()) {
      if (!template.isPrivate() && template instanceof TemplateBasicNode) {
        publicBasicTemplatePartialNames.add(template.getPartialTemplateName());
      }
      for (SoyDocParam param : template.getSoyDocParams()) {
        allParamKeys.add(param.key);
        paramKeyToTemplatesMultimap.put(param.key, template);
      }
    }
    // allParamKeysMap is a map from upper-underscore key to original key.
    SortedMap<String, String> allParamKeysMap = Maps.newTreeMap();
    for (String key : allParamKeys) {
      String upperUnderscoreKey = convertToUpperUnderscore(key);
      if (allParamKeysMap.containsKey(upperUnderscoreKey)) {
        throw new SoySyntaxException(
            "Cannot generate parse info because two param keys '" +
            allParamKeysMap.get(upperUnderscoreKey) + "' and '" + key +
            "' generate the same upper-underscore name '" + upperUnderscoreKey + "'.");
      }
      allParamKeysMap.put(upperUnderscoreKey, key);
    }

    ilb = new IndentedLinesBuilder(2);

    // ------ Header. ------
    ilb.appendLine("// This file was automatically generated from ", node.getFileName(), ".");
    ilb.appendLine("// Please don't edit this file by hand.");

    ilb.appendLine();
    ilb.appendLine("package ", javaPackage, ";");
    ilb.appendLine();
    ilb.appendLine("import com.google.common.collect.ImmutableList;");
    ilb.appendLine("import com.google.common.collect.ImmutableMap;");
    ilb.appendLine("import com.google.common.collect.ImmutableSortedSet;");
    ilb.appendLine("import com.google.template.soy.parseinfo.SoyFileInfo;");
    ilb.appendLine("import com.google.template.soy.parseinfo.SoyTemplateInfo;");
    ilb.appendLine("import com.google.template.soy.parseinfo.SoyTemplateInfo.ParamRequisiteness;");

    // ------ Class start. ------
    ilb.appendLine();
    ilb.appendLine();
    appendJavadoc(ilb, "Soy parse info for " + node.getFileName() + ".", true, false);
    ilb.appendLine("public class ", javaClassName, " extends SoyFileInfo {");
    ilb.increaseIndent();

    // ------ Params. ------
    ilb.appendLine();
    ilb.appendLine();
    ilb.appendLine("public static class Param {");
    ilb.increaseIndent();
    ilb.appendLine("private Param() {}");
    ilb.appendLine();

    for (Map.Entry<String, String> paramEntry : allParamKeysMap.entrySet()) {
      String upperUnderscoreKey = paramEntry.getKey();
      String key = paramEntry.getValue();

      StringBuilder javadocSb = new StringBuilder();
      javadocSb.append("Listed by ");
      boolean isFirst = true;
      for (TemplateNode template : paramKeyToTemplatesMultimap.get(key)) {
        if (isFirst) {
          isFirst = false;
        } else {
          javadocSb.append(", ");
        }
        javadocSb.append(buildTemplateNameForJavadoc(node, template));
      }
      javadocSb.append(".");
      appendJavadoc(ilb, javadocSb.toString(), false, true);

      ilb.appendLine("public static final String ", upperUnderscoreKey, " = \"", key, "\";");
    }

    ilb.decreaseIndent();
    ilb.appendLine("}");

    // ------ Templates. ------
    for (TemplateNode template : node.getChildren()) {
      try {
        visit(template);
      } catch (SoySyntaxException sse) {
        throw sse.setTemplateName(template.getTemplateNameForUserMsgs());
      }
    }

    // ------ Constructor. ------
    ilb.appendLine();
    ilb.appendLine();

    ilb.appendLine("private ", javaClassName, "() {");
    ilb.increaseIndent();
    ilb.appendLine("super(\"", node.getFileName(), "\",");
    ilb.setIndentLen(ilb.getCurrIndentLen() + 6);
    ilb.appendLine("\"", node.getNamespace(), "\",");

    // Params from all templates.
    List<String> itemSnippets = Lists.newArrayList();
    for (String upperUnderscoreKey : allParamKeysMap.keySet()) {
      itemSnippets.add("Param." + upperUnderscoreKey);
    }
    appendImmutableSortedSet(ilb, "<String>", itemSnippets);
    ilb.append(",\n");

    // Templates.
    itemSnippets = Lists.newArrayList();
    for (String partialTemplateName : publicBasicTemplatePartialNames) {
      itemSnippets.add(convertToUpperUnderscore(partialTemplateName.substring(1)));
    }
    appendImmutableList(ilb, "<SoyTemplateInfo>", itemSnippets);
    ilb.append(",\n");

    // CSS names.
    SortedMap<String, CssTagsPrefixPresence> cssNamesMap =
        (new CollectCssNamesVisitor()).exec(node);
    List<Pair<String, String>> entrySnippetPairs = Lists.newArrayList();
    for (Map.Entry<String, CssTagsPrefixPresence> entry : cssNamesMap.entrySet()) {
      entrySnippetPairs.add(Pair.of(
          "\"" + entry.getKey() + "\"",
          "CssTagsPrefixPresence." + entry.getValue().name()));
    }
    appendImmutableMap(ilb, "<String, CssTagsPrefixPresence>", entrySnippetPairs);
    ilb.append(");\n");

    ilb.setIndentLen(ilb.getCurrIndentLen() - 6);

    ilb.decreaseIndent();
    ilb.appendLine("}");

    // ------ Singleton instance and its getter. ------
    ilb.appendLine();
    ilb.appendLine();
    ilb.appendLine(
        "private static final ", javaClassName, " __INSTANCE__ = new ", javaClassName, "();");
    ilb.appendLine();
    ilb.appendLine();
    ilb.appendLine("public static ", javaClassName, " getInstance() {");
    ilb.increaseIndent();
    ilb.appendLine("return __INSTANCE__;");
    ilb.decreaseIndent();
    ilb.appendLine("}");

    // ------ Class end. ------
    ilb.appendLine();
    ilb.decreaseIndent();
    ilb.appendLine("}");

    generatedFiles.put(javaClassName + ".java", ilb.toString());
    ilb = null;
  }


  @Override protected void visitTemplateNode(TemplateNode node) {

    // Don't generate anything for private or delegate templates.
    if (node.isPrivate() || node instanceof TemplateDelegateNode) {
      return;
    }

    // First build list of all transitive params (direct and indirect).
    IndirectParamsInfo ipi = (new FindIndirectParamsVisitor(templateRegistry)).exec(node);
    LinkedHashMap<String, SoyDocParam> transitiveParams = Maps.newLinkedHashMap();
    // Direct params.
    for (SoyDocParam param : node.getSoyDocParams()) {
      transitiveParams.put(param.key, param);
    }
    // Indirect params.
    for (SoyDocParam param : ipi.indirectParams.values()) {
      SoyDocParam existingParam = transitiveParams.get(param.key);
      if (existingParam == null) {
        // Note: We don't list the SoyDoc description for indirect params.
        transitiveParams.put(param.key, new SoyDocParam(param.key, param.isRequired, null));
      }
    }

    String upperUnderscoreName =
        convertToUpperUnderscore(node.getPartialTemplateName().substring(1));

    if (transitiveParams.size() == 0) {
      // ------ Generate code for template with no params (direct or indirect). ------

      ilb.appendLine();
      ilb.appendLine();
      appendJavadoc(ilb, node.getSoyDocDesc(), true, false);
      ilb.appendLine("public static final SoyTemplateInfo ", upperUnderscoreName,
                     " = new SoyTemplateInfo(");
      ilb.increaseIndent(2);

      ilb.appendLine("\"", node.getTemplateName(), "\",");
      ilb.appendLine("ImmutableMap.<String, ParamRequisiteness>of(),");
      appendUsedIjParams(ilb, node);
      ilb.append(");\n");

      ilb.decreaseIndent(2);

    } else {
      // ------ Generate code for template with params. ------

      String templateInfoClassName =
          CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, upperUnderscoreName) +
          "SoyTemplateInfo";

      ilb.appendLine();
      ilb.appendLine();
      appendJavadoc(ilb, node.getSoyDocDesc(), true, false);
      ilb.appendLine("public static final ", templateInfoClassName, " ", upperUnderscoreName, " =");
      ilb.appendLine("    new ", templateInfoClassName, "();");

      ilb.appendLine();
      ilb.appendLine("public static class ", templateInfoClassName, " extends SoyTemplateInfo {");
      ilb.increaseIndent();

      ilb.appendLine("private ", templateInfoClassName, "() {");
      ilb.increaseIndent();

      ilb.appendLine("super(\"", node.getTemplateName(), "\",");
      ilb.increaseIndent(3);

      List<Pair<String, String>> entrySnippetPairs = Lists.newArrayList();
      for (SoyDocParam param : transitiveParams.values()) {
        entrySnippetPairs.add(Pair.of(
            "\"" + param.key + "\"",
            param.isRequired ? "ParamRequisiteness.REQUIRED" : "ParamRequisiteness.OPTIONAL"));
      }
      appendImmutableMap(ilb, "<String, ParamRequisiteness>", entrySnippetPairs);
      ilb.append(",\n");

      appendUsedIjParams(ilb, node);

      ilb.append(");\n");
      ilb.decreaseIndent(3);

      ilb.decreaseIndent();
      ilb.appendLine("}");

      boolean hasSwitchedToIndirectParams = false;
      for (SoyDocParam param : transitiveParams.values()) {

        if (param.desc != null) {
          // Direct param.
          ilb.appendLine();
          appendJavadoc(ilb, param.desc, false, false);

        } else {
          // Indirect param.
          if (!hasSwitchedToIndirectParams) {
            ilb.appendLine();
            ilb.appendLine("// Indirect params.");
            hasSwitchedToIndirectParams = true;
          }

          // Get the list of all transitive callee names as they will appear in the generated
          // Javadoc (possibly containing both partial and full names) and sort them before
          // generating the Javadoc.
          SortedSet<String> sortedJavadocCalleeNames = Sets.newTreeSet();
          for (TemplateNode transitiveCallee : ipi.paramKeyToCalleesMultimap.get(param.key)) {
            String javadocCalleeName =
                buildTemplateNameForJavadoc((SoyFileNode) node.getParent(), transitiveCallee);
            sortedJavadocCalleeNames.add(javadocCalleeName);
          }

          // Generate the Javadoc.
          StringBuilder javadocSb = new StringBuilder();
          javadocSb.append("Listed by ");
          boolean isFirst = true;
          for (String javadocCalleeName : sortedJavadocCalleeNames) {
            if (isFirst) {
              isFirst = false;
            } else {
              javadocSb.append(", ");
            }
            javadocSb.append(javadocCalleeName);
          }
          javadocSb.append(".");
          appendJavadoc(ilb, javadocSb.toString(), false, true);
        }

        // The actual param field.
        ilb.appendLine("public final String ", convertToUpperUnderscore(param.key),
                       " = \"", param.key, "\";");
      }

      ilb.decreaseIndent();
      ilb.appendLine("}");
    }
  }


  /**
   * Private helper for visitSoyFileNode() and visitTemplateNode() to convert an identifier to upper
   * underscore format.
   *
   * We simply dispatch to Utils.convertToUpperUnderscore() to do the actual conversion. The reason
   * for the existence of this method is that we cache all results of previous invocations in this
   * pass because this method is expected to be called for the same identifier multiple times.
   *
   * @param ident The identifier to convert.
   * @return The identifier in upper underscore format.
   */
  private String convertToUpperUnderscore(String ident) {

    String result = convertedIdents.get(ident);
    if (result == null) {
      result = BaseUtils.convertToUpperUnderscore(ident);
      convertedIdents.put(ident, result);
    }
    return result;
  }


  /**
   * Private helper for visitSoyFileNode() and visitTemplateNode() to append a Javadoc comment to
   * the code being built.
   *
   * @param ilb The builder for the code.
   * @param doc The doc string to append as the content of a Javadoc comment. The Javadoc format
   *     will follow the usual conventions. Important: If the doc string is multiple lines, the
   *     line separator must be '\n'.
   * @param forceMultiline If true, we always generate a multiline Javadoc comment even if the doc
   *     string only has one line. If false, we generate either a single line or multiline Javadoc
   *     comment, depending on the doc string.
   * @param wrapAt100Chars If true, wrap at 100 chars.
   */
  private static void appendJavadoc(
      IndentedLinesBuilder ilb, String doc, boolean forceMultiline, boolean wrapAt100Chars) {

    if (wrapAt100Chars) {
      // Actual wrap length is less because of indent and because of space used by Javadoc chars.
      int wrapLen = 100 - ilb.getCurrIndentLen() - 7;
      List<String> wrappedLines = Lists.newArrayList();
      for (String line : Splitter.on('\n').split(doc)) {
        while (line.length() > wrapLen) {
          int spaceIndex = line.lastIndexOf(' ', wrapLen);
          wrappedLines.add(line.substring(0, spaceIndex));
          line = line.substring(spaceIndex + 1);
        }
        wrappedLines.add(line);
      }
      doc = Joiner.on("\n").join(wrappedLines);
    }

    if (doc.contains("\n") || forceMultiline) {
      // Multiline.
      ilb.appendLine("/**");
      for (String line : Splitter.on('\n').split(doc)) {
        ilb.appendLine(" * ", line);
      }
      ilb.appendLine(" */");

    } else {
      // One line.
      ilb.appendLine("/** ", doc, " */");
    }
  }


  /**
   * Private helper for visitTemplateNode() to append the set of used injected params.
   *
   * @param ilb The builder for the code.
   * @param template The template to generate code for.
   */
  private void appendUsedIjParams(IndentedLinesBuilder ilb, TemplateNode template) {

    UsedIjParamsInfo uipi = (new FindUsedIjParamsVisitor(templateRegistry)).exec(template);

    List<String> itemSnippets = Lists.newArrayList();
    for (String paramKey : ImmutableSortedSet.copyOf(uipi.usedIjParamToCalleesMultimap.keySet())) {
      itemSnippets.add("\"" + paramKey + "\"");
    }
    appendImmutableSortedSet(ilb, "<String>", itemSnippets);
  }


  // -----------------------------------------------------------------------------------------------
  // General helpers.


  /**
   * Private helper to build the human-readable string for referring to a template in the generated
   * code's javadoc.
   * @param currSoyFile The current Soy file for which we're generating parse-info code.
   * @param template The template that we want to refer to in the generated javadoc. Note that this
   *     template may not be in the current Soy file.
   * @return The human-readable string for referring to the given template in the generated code's
   *     javadoc.
   */
  private static String buildTemplateNameForJavadoc(
      SoyFileNode currSoyFile, TemplateNode template) {

    StringBuilder resultSb = new StringBuilder();

    if (template.getParent() == currSoyFile && ! (template instanceof TemplateDelegateNode)) {
      resultSb.append(template.getPartialTemplateName());
    } else {
      resultSb.append(template.getTemplateNameForUserMsgs());
    }

    if (template.isPrivate()) {
      resultSb.append(" (private)");
    }
    if (template instanceof TemplateDelegateNode) {
      resultSb.append(" (delegate)");
    }

    return resultSb.toString();
  }


  /**
   * Private helper to append an ImmutableList to the code.
   *
   * @param ilb The builder for the code.
   * @param typeParamSnippet The type parameter for the ImmutableList.
   * @param itemSnippets Code snippets for the items to put into the ImmutableList.
   */
  private static void appendImmutableList(
      IndentedLinesBuilder ilb, String typeParamSnippet, Collection<String> itemSnippets) {
    appendListOrSetHelper(
        ilb, "ImmutableList." + typeParamSnippet + "of", itemSnippets);
  }


  /**
   * Private helper to append an ImmutableSortedSet to the code.
   *
   * @param ilb The builder for the code.
   * @param typeParamSnippet The type parameter for the ImmutableSortedSet.
   * @param itemSnippets Code snippets for the items to put into the ImmutableSortedSet.
   */
  private static void appendImmutableSortedSet(
      IndentedLinesBuilder ilb, String typeParamSnippet, Collection<String> itemSnippets) {
    appendListOrSetHelper(
        ilb, "ImmutableSortedSet." + typeParamSnippet + "of", itemSnippets);
  }


  /**
   * Private helper for appendImmutableList() and appendImmutableSortedSet().
   *
   * @param ilb The builder for the code.
   * @param creationFunctionSnippet Code snippet for the qualified name of the list or set creation
   *     function (without trailing parentheses).
   * @param itemSnippets Code snippets for the items to put into the list or set.
   */
  private static void appendListOrSetHelper(
      IndentedLinesBuilder ilb, String creationFunctionSnippet, Collection<String> itemSnippets) {

    if (itemSnippets.size() == 0) {
      ilb.appendIndent().appendParts(creationFunctionSnippet, "()");

    } else {
      ilb.appendLine(creationFunctionSnippet, "(");
      boolean isFirst = true;
      for (String item : itemSnippets) {
        if (isFirst) {
          isFirst = false;
        } else {
          ilb.append(",\n");
        }
        ilb.appendIndent().appendParts("    ", item);
      }
      ilb.append(")");
    }
  }


  /**
   * Private helper to append an ImmutableMap to the code.
   *
   * @param ilb The builder for the code.
   * @param typeParamSnippet The type parameter for the ImmutableMap.
   * @param entrySnippetPairs Pairs of (key, value) code snippets for the entries to put into the
   *     ImmutableMap.
   */
  private static void appendImmutableMap(
      IndentedLinesBuilder ilb, String typeParamSnippet,
      Collection<Pair<String, String>> entrySnippetPairs) {

    if (entrySnippetPairs.size() == 0) {
      ilb.appendIndent().appendParts("ImmutableMap.", typeParamSnippet, "of()");

    } else {
      ilb.appendLine("ImmutableMap.", typeParamSnippet, "builder()");
      for (Pair<String, String> entrySnippetPair : entrySnippetPairs) {
        ilb.appendLine("    .put(", entrySnippetPair.first, ", ", entrySnippetPair.second, ")");
      }
      ilb.appendIndent().append("    .build()");
    }
  }


  // -----------------------------------------------------------------------------------------------
  // Helper visitor to collect CSS names.


  /**
   * Private helper class for visitSoyFileNode() to collect all the CSS names appearing in a file.
   *
   * The return value of exec() is a map from each CSS name appearing in the given node's subtree to
   * its CssTagsPrefixPresence state.
   */
  private static class CollectCssNamesVisitor
      extends AbstractSoyNodeVisitor<SortedMap<String, CssTagsPrefixPresence>> {

    /** Map from each CSS name to its CssTagsPrefixPresence state. */
    private SortedMap<String, CssTagsPrefixPresence> cssNamesMap;

    public CollectCssNamesVisitor() {
      cssNamesMap = Maps.newTreeMap();
    }

    @Override public SortedMap<String, CssTagsPrefixPresence> exec(SoyNode node) {
      visit(node);
      return cssNamesMap;
    }

    @Override protected void visitCssNode(CssNode node) {

      String cssName = node.getSelectorText();
      CssTagsPrefixPresence existingCssTagsPrefixPresence = cssNamesMap.get(cssName);
      CssTagsPrefixPresence additionalCssTagsPrefixPresence =
          (node.getComponentNameExpr() == null) ?
              CssTagsPrefixPresence.NEVER : CssTagsPrefixPresence.ALWAYS;

      if (existingCssTagsPrefixPresence == null) {
        cssNamesMap.put(cssName, additionalCssTagsPrefixPresence);
      } else if (existingCssTagsPrefixPresence != additionalCssTagsPrefixPresence) {
        cssNamesMap.put(cssName, CssTagsPrefixPresence.SOMETIMES);
      } else {
        // Nothing to change.
      }
    }

    @Override protected void visitSoyNode(SoyNode node) {
      if (node instanceof ParentSoyNode<?>) {
        visitChildren((ParentSoyNode<?>) node);
      }
    }
  }

}
