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
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.template.soy.base.BaseUtils;
import com.google.template.soy.base.IndentedLinesBuilder;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.sharedpasses.FindIndirectParamsVisitor;
import com.google.template.soy.sharedpasses.FindIndirectParamsVisitor.IndirectParamsInfo;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.TemplateNode.SoyDocParam;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
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
 * @author Kai Huang
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

  /** Map from template name to TemplateNode used by FindTransitiveCalleesVisitor. */
  private Map<String, TemplateNode> templateNameToNodeMap;

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


  @Override protected void setup() {
    generatedFiles = Maps.newLinkedHashMap();
    ilb = null;
  }


  @Override protected ImmutableMap<String, String> getResult() {
    return ImmutableMap.copyOf(generatedFiles);
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for concrete classes.


  @Override protected void visitInternal(SoyFileSetNode node) {

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

    // Build templateNameToNodeMap.
    templateNameToNodeMap = Maps.newHashMap();
    for (SoyFileNode soyFile : node.getChildren()) {
      for (TemplateNode template : soyFile.getChildren()) {
        templateNameToNodeMap.put(template.getTemplateName(), template);
      }
    }

    // Run the pass.
    for (SoyFileNode soyFile : node.getChildren()) {
      try {
        visit(soyFile);
      } catch (SoySyntaxException sse) {
        throw sse.setFilePath(soyFile.getFilePath());
      }
    }
  }


  @Override protected void visitInternal(SoyFileNode node) {

    if (node.getFilePath() == null) {
      throw new SoySyntaxException(
          "In order to generate parse info, all Soy files must have paths (file name is" +
          " extracted from the path).");
    }

    String javaClassName = soyFileToJavaClassNameMap.get(node);

    // Collect the following:
    // + all the partial template names of non-private templates,
    // + all the param keys from all templates (including private),
    // + for each param key, the list of templates that list it directly.
    List<String> nonprivateTemplatePartialNames = Lists.newArrayList();
    Set<String> allParamKeys = Sets.newHashSet();
    LinkedHashMultimap<String, TemplateNode> paramKeyToTemplatesMultimap =
        LinkedHashMultimap.create();
    for (TemplateNode template : node.getChildren()) {
      if (!template.isPrivate()) {
        nonprivateTemplatePartialNames.add(template.getPartialTemplateName());
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

    // ------ Header ------
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
    ilb.appendLine(
        "import static ",
        "com.google.template.soy.parseinfo.SoyTemplateInfo.ParamRequisiteness.OPTIONAL;");
    ilb.appendLine(
        "import static ",
        "com.google.template.soy.parseinfo.SoyTemplateInfo.ParamRequisiteness.REQUIRED;");

    // ------ Class start ------
    ilb.appendLine();
    ilb.appendLine();
    appendJavadoc(ilb, "Soy parse info for " + node.getFileName() + ".", true, false);
    ilb.appendLine("public class ", javaClassName, " extends SoyFileInfo {");
    ilb.increaseIndent();

    // ------ Params ------
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
        javadocSb.append(template.getPartialTemplateName());
        if (template.isPrivate()) {
          javadocSb.append(" (private)");
        }
      }
      javadocSb.append(".");
      appendJavadoc(ilb, javadocSb.toString(), false, true);

      ilb.appendLine("public static final String ", upperUnderscoreKey, " = \"", key, "\";");
    }

    ilb.decreaseIndent();
    ilb.appendLine("}");

    // ------ Templates ------
    for (TemplateNode template : node.getChildren()) {
      try {
        visit(template);
      } catch (SoySyntaxException sse) {
        throw sse.setTemplateName(template.getTemplateName());
      }
    }

    // ------ File info ------
    ilb.appendLine();
    ilb.appendLine();
    // Constructor.
    ilb.appendLine("private ", javaClassName, "() {");
    ilb.increaseIndent();
    ilb.appendLine("super(\"", node.getFileName(), "\",");
    ilb.setIndentLen(ilb.getCurrIndentLen() + 6);
    ilb.appendLine("\"", node.getNamespace(), "\",");

    ilb.appendLine("ImmutableSortedSet.<String>of(");
    ilb.increaseIndent();
    ilb.increaseIndent();
    boolean isFirst = true;
    for (String upperUnderscoreKey : allParamKeysMap.keySet()) {
      if (isFirst) {
        isFirst = false;
      } else {
        ilb.append(",\n");
      }
      ilb.appendIndent().append("Param.").append(upperUnderscoreKey);
    }
    ilb.append("),").appendLineEnd();
    ilb.decreaseIndent();
    ilb.decreaseIndent();

    ilb.appendLine("ImmutableList.<SoyTemplateInfo>of(");
    ilb.increaseIndent();
    ilb.increaseIndent();
    isFirst = true;
    for (String partialTemplateName : nonprivateTemplatePartialNames) {
      if (isFirst) {
        isFirst = false;
      } else {
        ilb.append(",\n");
      }
      ilb.appendIndent().append(convertToUpperUnderscore(partialTemplateName.substring(1)));
    }
    ilb.append("));").appendLineEnd();
    ilb.decreaseIndent();
    ilb.decreaseIndent();

    ilb.setIndentLen(ilb.getCurrIndentLen() - 6);

    ilb.decreaseIndent();
    ilb.appendLine("}");

    ilb.appendLine();
    ilb.appendLine("private static final ", javaClassName, " __INSTANCE__ = new ", javaClassName,
                   "();");
    ilb.appendLine();
    ilb.appendLine("public static ", javaClassName, " getInstance() {");
    ilb.increaseIndent();
    ilb.appendLine("return __INSTANCE__;");
    ilb.decreaseIndent();
    ilb.appendLine("}");

    // ------ Class end ------
    ilb.appendLine();
    ilb.decreaseIndent();
    ilb.appendLine("}");

    generatedFiles.put(javaClassName + ".java", ilb.toString());
    ilb = null;
  }


  @Override protected void visitInternal(TemplateNode node) {

    // Don't generate anything for private templates.
    if (node.isPrivate()) {
      return;
    }

    // First build list of all transitive params (direct and indirect).
    IndirectParamsInfo ipi =
        (new FindIndirectParamsVisitor(false, true, templateNameToNodeMap)).exec(node);
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
      ilb.increaseIndent();
      ilb.increaseIndent();

      ilb.appendLine("\"", node.getTemplateName(), "\",");
      ilb.appendLine("ImmutableMap.<String, ParamRequisiteness>of());");

      ilb.decreaseIndent();
      ilb.decreaseIndent();

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
      ilb.appendLine("      ImmutableMap.<String, ParamRequisiteness>builder()");
      for (SoyDocParam param : transitiveParams.values()) {
        ilb.appendLine("      .put(\"", param.key, "\", ",
                       (param.isRequired ? "REQUIRED" : "OPTIONAL"), ")");
      }
      ilb.appendLine("      .build());");

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

          StringBuilder javadocSb = new StringBuilder();
          javadocSb.append("Listed by ");
          boolean isFirst = true;
          for (TemplateNode transitiveCallee : ipi.paramKeyToCalleesMultimap.get(param.key)) {
            if (isFirst) {
              isFirst = false;
            } else {
              javadocSb.append(", ");
            }
            if (transitiveCallee.getParent() == node.getParent()) {
              // Transitive callee is in the same file.
              javadocSb.append(transitiveCallee.getPartialTemplateName());
            } else {
              // Transitive callee is in a different file.
              javadocSb.append(transitiveCallee.getTemplateName());
            }
            if (transitiveCallee.isPrivate()) {
              javadocSb.append(" (private)");
            }
          }
          javadocSb.append(".");
          appendJavadoc(ilb, javadocSb.toString(), false, true);
        }
        ilb.appendLine("public final String ", convertToUpperUnderscore(param.key),
                       " = \"", param.key, "\";");
      }

      ilb.decreaseIndent();
      ilb.appendLine("}");
    }
  }


  /**
   * Private helper for visitInternal(SoyFileNode) and visitInternal(TemplateNode) to convert an
   * identifier to upper underscore format.
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
   * Private helper for visitInternal(SoyFileNode) and visitInternal(TemplateNode) to append a
   * Javadoc comment to the code being built.
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

}
