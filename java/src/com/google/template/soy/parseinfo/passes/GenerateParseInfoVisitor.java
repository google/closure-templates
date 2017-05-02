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
import com.google.common.base.Optional;
import com.google.common.base.Splitter;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.template.soy.base.internal.BaseUtils;
import com.google.template.soy.base.internal.IndentedLinesBuilder;
import com.google.template.soy.base.internal.LegacyInternalSyntaxException;
import com.google.template.soy.base.internal.SoyFileKind;
import com.google.template.soy.exprtree.FieldAccessNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.GlobalNode;
import com.google.template.soy.exprtree.ProtoInitNode;
import com.google.template.soy.exprtree.StringNode;
import com.google.template.soy.internal.base.Pair;
import com.google.template.soy.parseinfo.SoyFileInfo.CssTagsPrefixPresence;
import com.google.template.soy.passes.FindIjParamsVisitor;
import com.google.template.soy.passes.FindIjParamsVisitor.IjParamsInfo;
import com.google.template.soy.passes.FindIndirectParamsVisitor;
import com.google.template.soy.passes.FindIndirectParamsVisitor.IndirectParamsInfo;
import com.google.template.soy.shared.internal.BuiltinFunction;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.CssNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TemplateBasicNode;
import com.google.template.soy.soytree.TemplateDelegateNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.TemplateRegistry;
import com.google.template.soy.soytree.Visibility;
import com.google.template.soy.soytree.defn.HeaderParam;
import com.google.template.soy.soytree.defn.TemplateParam;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.aggregate.ListType;
import com.google.template.soy.types.aggregate.MapType;
import com.google.template.soy.types.aggregate.RecordType;
import com.google.template.soy.types.aggregate.UnionType;
import com.google.template.soy.types.proto.ProtoUtils;
import com.google.template.soy.types.proto.SoyProtoEnumType;
import com.google.template.soy.types.proto.SoyProtoType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Visitor for generating Java classes containing the parse info.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * <p>{@link #exec} should be called on a full parse tree.
 *
 * <p>For an example Soy file and its corresponding generated code, see
 *
 * <pre>
 *     [tests_dir]/com/google/template/soy/test_data/AaaBbbCcc.soy
 *     [tests_dir]/com/google/template/soy/test_data/AaaBbbCccSoyInfo.java
 * </pre>
 *
 */
public final class GenerateParseInfoVisitor
    extends AbstractSoyNodeVisitor<ImmutableMap<String, String>> {

  /** Represents the source of the generated Java class names. */
  @VisibleForTesting
  enum JavaClassNameSource {
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
     *
     * @param soyFile The Soy file.
     * @return The generated base Java class name (without any suffixes).
     */
    @VisibleForTesting
    String generateBaseClassName(SoyFileNode soyFile) {
      switch (this) {
        case SOY_FILE_NAME:
          String fileName = soyFile.getFileName();
          if (fileName == null) {
            throw new IllegalArgumentException(
                "Trying to generate Java class name based on Soy file name, but Soy file name was"
                    + " not provided.");
          }
          if (fileName.toLowerCase().endsWith(".soy")) {
            fileName = fileName.substring(0, fileName.length() - 4);
          }
          return makeUpperCamelCase(fileName);

        case SOY_NAMESPACE_LAST_PART:
          String namespace = soyFile.getNamespace();
          assert namespace != null; // suppress warnings
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
     *
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
     *
     * @param str The string to process.
     * @param wordPattern The regex pattern for matching a word.
     * @return The resulting string with all words in capitalized format.
     */
    private static String makeWordsCapitalized(String str, Pattern wordPattern) {
      StringBuffer sb = new StringBuffer();

      Matcher wordMatcher = wordPattern.matcher(str);
      while (wordMatcher.find()) {
        String oldWord = wordMatcher.group();
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
  private final TemplateRegistry templateRegistry;

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
  public GenerateParseInfoVisitor(
      String javaPackage, String javaClassNameSource, TemplateRegistry registry) {
    this.javaPackage = javaPackage;
    this.templateRegistry = registry;

    switch (javaClassNameSource) {
      case "filename":
        this.javaClassNameSource = JavaClassNameSource.SOY_FILE_NAME;
        break;
      case "namespace":
        this.javaClassNameSource = JavaClassNameSource.SOY_NAMESPACE_LAST_PART;
        break;
      case "generic":
        this.javaClassNameSource = JavaClassNameSource.GENERIC;
        break;
      default:
        throw new IllegalArgumentException(
            "Invalid value for javaClassNameSource \""
                + javaClassNameSource
                + "\""
                + " (valid values are \"filename\", \"namespace\", and \"generic\").");
    }
  }

  @Override
  public ImmutableMap<String, String> exec(SoyNode node) {
    generatedFiles = Maps.newLinkedHashMap();
    ilb = null;
    visit(node);
    return ImmutableMap.copyOf(generatedFiles);
  }

  // -----------------------------------------------------------------------------------------------
  // Implementations for specific nodes.

  @Override
  protected void visitSoyFileSetNode(SoyFileSetNode node) {
    // Figure out the generated class name for each Soy file, including adding number suffixes
    // to resolve collisions, and then adding the common suffix "SoyInfo".
    Multimap<String, SoyFileNode> baseGeneratedClassNameToSoyFilesMap = HashMultimap.create();
    for (SoyFileNode soyFile : node.getChildren()) {
      if (soyFile.getSoyFileKind() == SoyFileKind.SRC) {
        baseGeneratedClassNameToSoyFilesMap.put(
            javaClassNameSource.generateBaseClassName(soyFile), soyFile);
      }
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

    // Run the pass.
    for (SoyFileNode soyFile : node.getChildren()) {
      visit(soyFile);
    }
  }

  @Override
  protected void visitSoyFileNode(SoyFileNode node) {
    if (node.getSoyFileKind() != SoyFileKind.SRC) {
      return; // don't generate code for deps
    }

    if (node.getFilePath() == null) {
      throw LegacyInternalSyntaxException.createWithMetaInfo(
          "In order to generate parse info, all Soy files must have paths (file name is"
              + " extracted from the path).",
          node.getSourceLocation());
    }

    String javaClassName = soyFileToJavaClassNameMap.get(node);

    // Collect the following:
    // + all the public basic templates (non-private, non-delegate) in a map from the
    //   upper-underscore template name to the template's node,
    // + all the param keys from all templates (including private),
    // + for each param key, the list of templates that list it directly.
    // + for any params whose type is a proto, get the proto name and Java class name.
    LinkedHashMap<String, TemplateNode> publicBasicTemplateMap = Maps.newLinkedHashMap();
    List<String> deltemplates = new ArrayList<>();
    Set<String> allParamKeys = Sets.newHashSet();
    SetMultimap<String, TemplateNode> paramKeyToTemplatesMultimap = LinkedHashMultimap.create();
    SortedSet<String> protoTypes = Sets.newTreeSet();
    for (TemplateNode template : node.getChildren()) {
      if (template.getVisibility() == Visibility.PUBLIC && template instanceof TemplateBasicNode) {
        publicBasicTemplateMap.put(
            convertToUpperUnderscore(template.getPartialTemplateName().substring(1)), template);
      }
      if (template instanceof TemplateDelegateNode) {
        deltemplates.add("\"" + template.getTemplateName() + "\"");
      }
      for (TemplateParam param : template.getAllParams()) {
        if (!param.isInjected()) {
          allParamKeys.add(param.name());
          paramKeyToTemplatesMultimap.put(param.name(), template);
        }
        if (param instanceof HeaderParam) {
          SoyType paramType = ((HeaderParam) param).type();
          findProtoTypesRecurse(paramType, protoTypes);
        }
      }
      // Field access nodes need special handling to ensure that extension references are handled.
      for (FieldAccessNode fieldAccess :
          SoyTreeUtils.getAllNodesOfType(template, FieldAccessNode.class)) {
        SoyType baseType = fieldAccess.getBaseExprChild().getType();
        if (baseType.getKind() == SoyType.Kind.PROTO) {
          FieldDescriptor desc =
              ((SoyProtoType) baseType).getFieldDescriptor(fieldAccess.getFieldName());
          if (desc.isExtension()) {
            protoTypes.add(ProtoUtils.getTofuExtensionImport(desc));
          }
        }
      }
      // Note: we need to add descriptors from other parts of the expression api that contain direct
      // proto references.  We do not just scan for all referenced proto types since that would
      // cause us to add direct references to the parseinfos for protos that are only indirectly
      // referenced.  If we were to do this it would trigger strict deps issues.
      // Add enums
      for (GlobalNode global : SoyTreeUtils.getAllNodesOfType(template, GlobalNode.class)) {
        if (global.isResolved() && global.getType().getKind() == SoyType.Kind.PROTO_ENUM) {
          protoTypes.add(((SoyProtoEnumType) global.getType()).getDescriptorExpression());
        }
      }
      // Add proto init
      for (ProtoInitNode protoInit :
          SoyTreeUtils.getAllNodesOfType(template, ProtoInitNode.class)) {
        if (protoInit.getType().getKind() == SoyType.Kind.PROTO) {
          protoTypes.add(((SoyProtoType) protoInit.getType()).getDescriptorExpression());
        }
      }
    }
    // allParamKeysMap is a map from upper-underscore key to original key.
    SortedMap<String, String> allParamKeysMap = Maps.newTreeMap();
    for (String key : allParamKeys) {
      String upperUnderscoreKey = convertToUpperUnderscore(key);
      // Appends underscores for params that generates the same under score names. An example is if
      // we have two params naming foo_bar and fooBar, both will generate the same key FOO_BAR. They
      // are still validate parameter names so we should not throw an error here.
      // We don't need to worry about duplicate parameters since it will be prevented by the earlier
      // stage of the compiler.
      while (allParamKeysMap.containsKey(upperUnderscoreKey)) {
        upperUnderscoreKey = upperUnderscoreKey + "_";
      }
      allParamKeysMap.put(upperUnderscoreKey, key);
      // Updates the convertedIdents here, since we might have changed the value by adding
      // prepending underscores. Without this, the generated SoyTemplateInfo still use the
      // old mapping and will fail.
      convertedIdents.put(key, upperUnderscoreKey);
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

    // ------ Class start. ------
    ilb.appendLine();
    ilb.appendLine();
    appendJavadoc(ilb, "Soy parse info for " + node.getFileName() + ".", true, false);
    ilb.appendLine("public final class ", javaClassName, " extends SoyFileInfo {");
    ilb.increaseIndent();

    // ------ Constant for namespace. ------
    ilb.appendLine();
    ilb.appendLine();
    ilb.appendLine("/** This Soy file's namespace. */");
    ilb.appendLine("public static final String __NAMESPACE__ = \"", node.getNamespace(), "\";");

    // ------ Proto types map. ------
    if (!protoTypes.isEmpty()) {
      ilb.appendLine();
      ilb.appendLine();
      ilb.appendLine("/** Protocol buffer types used by these templates. */");
      // TODO(lukes): fix the generic type param here.
      ilb.appendLine("@Override public ImmutableList<Object> getProtoTypes() {");
      ilb.increaseIndent();
      // Note we use fully-qualified names instead of imports to avoid potential collisions.
      List<String> defaultInstances = Lists.newArrayList();
      defaultInstances.addAll(protoTypes);
      appendListOrSetHelper(ilb, "return ImmutableList.<Object>of", defaultInstances);
      ilb.appendLineEnd(";");
      ilb.decreaseIndent();
      ilb.appendLine("}");
    }

    // ------ Template names. ------
    ilb.appendLine();
    ilb.appendLine();
    ilb.appendLine("public static final class TemplateName {");
    ilb.increaseIndent();
    ilb.appendLine("private TemplateName() {}");
    ilb.appendLine();

    for (Entry<String, TemplateNode> templateEntry : publicBasicTemplateMap.entrySet()) {
      StringBuilder javadocSb = new StringBuilder();
      javadocSb
          .append("The full template name of the ")
          .append(templateEntry.getValue().getPartialTemplateName())
          .append(" template.");
      appendJavadoc(ilb, javadocSb.toString(), false, true);
      ilb.appendLine(
          "public static final String ",
          templateEntry.getKey(),
          " = \"",
          templateEntry.getValue().getTemplateName(),
          "\";");
    }

    ilb.decreaseIndent();
    ilb.appendLine("}");

    // ------ Params. ------
    ilb.appendLine();
    ilb.appendLine();
    ilb.appendLine("/**");
    ilb.appendLine(" * Param names from all templates in this Soy file.");
    ilb.appendLine(" */");
    ilb.appendLine("public static final class Param {");
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
      javadocSb.append('.');
      appendJavadoc(ilb, javadocSb.toString(), false, true);

      ilb.appendLine("public static final String ", upperUnderscoreKey, " = \"", key, "\";");
    }

    ilb.decreaseIndent();
    ilb.appendLine("}");

    // ------ Templates. ------
    for (TemplateNode template : publicBasicTemplateMap.values()) {
      visit(template);
    }

    // ------ Constructor. ------
    ilb.appendLine();
    ilb.appendLine();

    ilb.appendLine("private ", javaClassName, "() {");
    ilb.increaseIndent();
    ilb.appendLine("super(");
    ilb.increaseIndent(2);
    ilb.appendLine("\"", node.getFileName(), "\",");
    ilb.appendLine("\"", node.getNamespace(), "\",");

    // Templates.
    List<String> itemSnippets = Lists.newArrayList();
    itemSnippets.addAll(publicBasicTemplateMap.keySet());
    appendImmutableList(ilb, "<SoyTemplateInfo>", itemSnippets);
    ilb.appendLineEnd(",");

    // CSS names.
    SortedMap<String, CssTagsPrefixPresence> cssNameMap = new CollectCssNamesVisitor().exec(node);
    List<Pair<String, String>> entrySnippetPairs = Lists.newArrayList();
    for (Map.Entry<String, CssTagsPrefixPresence> entry : cssNameMap.entrySet()) {
      entrySnippetPairs.add(
          Pair.of(
              "\"" + entry.getKey() + "\"", "CssTagsPrefixPresence." + entry.getValue().name()));
    }
    appendImmutableMap(ilb, "<String, CssTagsPrefixPresence>", entrySnippetPairs);
    ilb.appendLineEnd(",");
    appendImmutableList(ilb, "<String>", deltemplates);
    ilb.appendLineEnd(");");

    ilb.decreaseIndent(2);

    ilb.decreaseIndent();
    ilb.appendLine("}");

    // ------ Singleton instance and its getter. ------
    ilb.appendLine();
    ilb.appendLine();
    ilb.appendLine("private static final ", javaClassName, " __INSTANCE__ =");
    ilb.increaseIndent(2);
    ilb.appendLine("new ", javaClassName, "();");
    ilb.decreaseIndent(2);
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

  @Override
  protected void visitTemplateNode(TemplateNode node) {
    // Don't generate anything for private or delegate templates.
    if (node.getVisibility() == Visibility.LEGACY_PRIVATE || node instanceof TemplateDelegateNode) {
      return;
    }

    // First build list of all transitive params (direct and indirect).
    LinkedHashMap<String, TemplateParam> transitiveParamMap = Maps.newLinkedHashMap();
    // Direct params.
    for (TemplateParam param : node.getParams()) {
      transitiveParamMap.put(param.name(), param);
    }

    // Indirect params.
    IndirectParamsInfo indirectParamsInfo =
        new FindIndirectParamsVisitor(templateRegistry).exec(node);
    for (TemplateParam param : indirectParamsInfo.indirectParams.values()) {
      TemplateParam existingParam = transitiveParamMap.get(param.name());
      if (existingParam == null) {
        // Note: We don't list the description for indirect params.
        transitiveParamMap.put(param.name(), param.copyEssential());
      }
    }

    // Get info on injected params.
    IjParamsInfo ijParamsInfo = new FindIjParamsVisitor(templateRegistry).exec(node);

    @SuppressWarnings("ConstantConditions") // for IntelliJ
    String upperUnderscoreName =
        convertToUpperUnderscore(node.getPartialTemplateName().substring(1));
    String templateInfoClassName =
        CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, upperUnderscoreName)
            + "SoyTemplateInfo";

    // ------ *SoyTemplateInfo class start. ------
    ilb.appendLine();
    ilb.appendLine();
    appendJavadoc(ilb, Optional.fromNullable(node.getSoyDocDesc()).or(""), true, false);
    ilb.appendLine(
        "public static final class ", templateInfoClassName, " extends SoyTemplateInfo {");
    ilb.increaseIndent();

    // ------ Constants for template name. ------
    ilb.appendLine();
    ilb.appendLine("/** This template's full name. */");
    ilb.appendLine("public static final String __NAME__ = \"", node.getTemplateName(), "\";");
    ilb.appendLine("/** This template's partial name. */");
    ilb.appendLine(
        "public static final String __PARTIAL_NAME__ = \"", node.getPartialTemplateName(), "\";");

    // ------ Param constants. ------
    boolean hasSeenFirstDirectParam = false;
    boolean hasSwitchedToIndirectParams = false;
    for (TemplateParam param : transitiveParamMap.values()) {

      if (param.desc() != null) {
        // Direct param.
        if (!hasSeenFirstDirectParam) {
          ilb.appendLine();
          hasSeenFirstDirectParam = true;
        }
        appendJavadoc(ilb, param.desc(), false, false);

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
        for (TemplateNode transitiveCallee :
            indirectParamsInfo.paramKeyToCalleesMultimap.get(param.name())) {
          String javadocCalleeName =
              buildTemplateNameForJavadoc(node.getParent(), transitiveCallee);
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
        javadocSb.append('.');
        appendJavadoc(ilb, javadocSb.toString(), false, true);
      }

      // The actual param field.
      ilb.appendLine(
          "public static final String ",
          convertToUpperUnderscore(param.name()),
          " = \"",
          param.name(),
          "\";");
    }

    // ------ Constructor. ------
    ilb.appendLine();
    ilb.appendLine("private ", templateInfoClassName, "() {");
    ilb.increaseIndent();

    ilb.appendLine("super(");
    ilb.increaseIndent(2);
    ilb.appendLine("\"", node.getTemplateName(), "\",");

    if (!transitiveParamMap.isEmpty()) {
      List<Pair<String, String>> entrySnippetPairs = Lists.newArrayList();
      for (TemplateParam param : transitiveParamMap.values()) {
        entrySnippetPairs.add(
            Pair.of(
                "\"" + param.name() + "\"",
                param.isRequired()
                    ? "ParamRequisiteness.REQUIRED"
                    : "ParamRequisiteness.OPTIONAL"));
      }
      appendImmutableMap(ilb, "<String, ParamRequisiteness>", entrySnippetPairs);
      ilb.appendLineEnd(",");
    } else {
      ilb.appendLine("ImmutableMap.<String, ParamRequisiteness>of(),");
    }

    appendIjParamSet(ilb, ijParamsInfo);
    ilb.appendLineEnd(");");
    ilb.decreaseIndent(2);

    ilb.decreaseIndent();
    ilb.appendLine("}");

    // ------ Singleton instance and its getter. ------
    ilb.appendLine();
    ilb.appendLine("private static final ", templateInfoClassName, " __INSTANCE__ =");
    ilb.increaseIndent(2);
    ilb.appendLine("new ", templateInfoClassName, "();");
    ilb.decreaseIndent(2);
    ilb.appendLine();
    ilb.appendLine("public static ", templateInfoClassName, " getInstance() {");
    ilb.increaseIndent();
    ilb.appendLine("return __INSTANCE__;");
    ilb.decreaseIndent();
    ilb.appendLine("}");

    // ------ *SoyTemplateInfo class end. ------
    ilb.decreaseIndent();
    ilb.appendLine("}");

    // ------ Static field with instance of *SoyTemplateInfo class. ------
    ilb.appendLine();
    ilb.appendLine("/** Same as ", templateInfoClassName, ".getInstance(). */");
    ilb.appendLine("public static final ", templateInfoClassName, " ", upperUnderscoreName, " =");
    ilb.increaseIndent(2);
    ilb.appendLine(templateInfoClassName, ".getInstance();");
    ilb.decreaseIndent(2);
  }

  /**
   * Private helper for visitSoyFileNode() and visitTemplateNode() to convert an identifier to upper
   * underscore format.
   *
   * <p>We simply dispatch to Utils.convertToUpperUnderscore() to do the actual conversion. The
   * reason for the existence of this method is that we cache all results of previous invocations in
   * this pass because this method is expected to be called for the same identifier multiple times.
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
   * Recursively search for protocol buffer types within the given type.
   *
   * @param type The type to search.
   * @param protoTypes Output set.
   */
  private static void findProtoTypesRecurse(SoyType type, SortedSet<String> protoTypes) {
    if (type.getKind() == SoyType.Kind.PROTO) {
      protoTypes.add(((SoyProtoType) type).getDescriptorExpression());
    } else if (type.getKind() == SoyType.Kind.PROTO_ENUM) {
      protoTypes.add(((SoyProtoEnumType) type).getDescriptorExpression());
    } else {
      switch (type.getKind()) {
        case UNION:
          for (SoyType member : ((UnionType) type).getMembers()) {
            findProtoTypesRecurse(member, protoTypes);
          }
          break;

        case LIST:
          {
            ListType listType = (ListType) type;
            findProtoTypesRecurse(listType.getElementType(), protoTypes);
            break;
          }

        case MAP:
          {
            MapType mapType = (MapType) type;
            findProtoTypesRecurse(mapType.getKeyType(), protoTypes);
            findProtoTypesRecurse(mapType.getValueType(), protoTypes);
            break;
          }

        case RECORD:
          {
            RecordType recordType = (RecordType) type;
            for (SoyType fieldType : recordType.getMembers().values()) {
              findProtoTypesRecurse(fieldType, protoTypes);
            }
            break;
          }

        default:
          break;
      }
    }
  }

  /**
   * Private helper for visitSoyFileNode() and visitTemplateNode() to append a Javadoc comment to
   * the code being built.
   *
   * @param ilb The builder for the code.
   * @param doc The doc string to append as the content of a Javadoc comment. The Javadoc format
   *     will follow the usual conventions. Important: If the doc string is multiple lines, the line
   *     separator must be '\n'.
   * @param forceMultiline If true, we always generate a multiline Javadoc comment even if the doc
   *     string only has one line. If false, we generate either a single line or multiline Javadoc
   *     comment, depending on the doc string.
   * @param wrapAt100Chars If true, wrap at 100 chars.
   */
  @VisibleForTesting
  static void appendJavadoc(
      IndentedLinesBuilder ilb, String doc, boolean forceMultiline, boolean wrapAt100Chars) {

    if (wrapAt100Chars) {
      // Actual wrap length is less because of indent and because of space used by Javadoc chars.
      int wrapLen = 100 - ilb.getCurrIndentLen() - 7;
      List<String> wrappedLines = Lists.newArrayList();
      for (String line : Splitter.on('\n').split(doc)) {
        while (line.length() > wrapLen) {
          int spaceIndex = line.lastIndexOf(' ', wrapLen);
          if (spaceIndex >= 0) {
            wrappedLines.add(line.substring(0, spaceIndex));
            line = line.substring(spaceIndex + 1); // add 1 to skip the space
          } else {
            // No spaces. Just wrap at wrapLen.
            wrappedLines.add(line.substring(0, wrapLen));
            line = line.substring(wrapLen);
          }
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
   * Private helper for visitTemplateNode() to append the set of injected params.
   *
   * @param ilb The builder for the code.
   * @param ijParamsInfo Info on injected params for the template being processed.
   */
  private void appendIjParamSet(IndentedLinesBuilder ilb, IjParamsInfo ijParamsInfo) {
    List<String> itemSnippets = Lists.newArrayList();
    for (String paramKey : ijParamsInfo.ijParamSet) {
      itemSnippets.add("\"" + paramKey + "\"");
    }
    appendImmutableSortedSet(ilb, "<String>", itemSnippets);
  }

  // -----------------------------------------------------------------------------------------------
  // General helpers.

  /**
   * Private helper to build the human-readable string for referring to a template in the generated
   * code's javadoc.
   *
   * @param currSoyFile The current Soy file for which we're generating parse-info code.
   * @param template The template that we want to refer to in the generated javadoc. Note that this
   *     template may not be in the current Soy file.
   * @return The human-readable string for referring to the given template in the generated code's
   *     javadoc.
   */
  private static String buildTemplateNameForJavadoc(
      SoyFileNode currSoyFile, TemplateNode template) {

    StringBuilder resultSb = new StringBuilder();

    if (template.getParent() == currSoyFile && !(template instanceof TemplateDelegateNode)) {
      resultSb.append(template.getPartialTemplateName());
    } else {
      resultSb.append(template.getTemplateNameForUserMsgs());
    }

    if (template.getVisibility() == Visibility.LEGACY_PRIVATE) {
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
    appendListOrSetHelper(ilb, "ImmutableList." + typeParamSnippet + "of", itemSnippets);
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
    appendListOrSetHelper(ilb, "ImmutableSortedSet." + typeParamSnippet + "of", itemSnippets);
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
    if (itemSnippets.isEmpty()) {
      ilb.appendLineStart(creationFunctionSnippet, "()");

    } else {
      ilb.appendLine(creationFunctionSnippet, "(");
      boolean isFirst = true;
      for (String item : itemSnippets) {
        if (isFirst) {
          isFirst = false;
        } else {
          ilb.appendLineEnd(",");
        }
        ilb.appendLineStart("    ", item);
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
      IndentedLinesBuilder ilb,
      String typeParamSnippet,
      Collection<Pair<String, String>> entrySnippetPairs) {
    if (entrySnippetPairs.isEmpty()) {
      ilb.appendLineStart("ImmutableMap.", typeParamSnippet, "of()");

    } else {
      ilb.appendLine("ImmutableMap.", typeParamSnippet, "builder()");
      for (Pair<String, String> entrySnippetPair : entrySnippetPairs) {
        ilb.appendLine("    .put(", entrySnippetPair.first, ", ", entrySnippetPair.second, ")");
      }
      ilb.appendLineStart("    .build()");
    }
  }

  // -----------------------------------------------------------------------------------------------
  // Helper visitor to collect CSS names.

  /**
   * Private helper class for visitSoyFileNode() to collect all the CSS names appearing in a file.
   *
   * <p>The return value of exec() is a map from each CSS name appearing in the given node's subtree
   * to its CssTagsPrefixPresence state.
   */
  private static class CollectCssNamesVisitor
      extends AbstractSoyNodeVisitor<SortedMap<String, CssTagsPrefixPresence>> {

    /** Map from each CSS name to its CssTagsPrefixPresence state. */
    private SortedMap<String, CssTagsPrefixPresence> cssNamesMap;

    private CollectCssNamesVisitor() {
      cssNamesMap = Maps.newTreeMap();
    }

    @Override
    public SortedMap<String, CssTagsPrefixPresence> exec(SoyNode node) {
      List<CssNode> cssNodes = SoyTreeUtils.getAllNodesOfType(node, CssNode.class);
      for (CssNode css : cssNodes) {
        collectSelector(css.getSelectorText(), css.getComponentNameExpr() != null);
      }

      List<FunctionNode> fnNodes = SoyTreeUtils.getAllNodesOfType(node, FunctionNode.class);
      for (FunctionNode fn : fnNodes) {
        if (fn.getSoyFunction() != BuiltinFunction.CSS) {
          continue;
        }

        String selector = ((StringNode) Iterables.getLast(fn.getChildren())).getValue();
        collectSelector(selector, fn.numChildren() > 1);
      }

      return cssNamesMap;
    }

    private void collectSelector(String selector, boolean hasComponentName) {
      CssTagsPrefixPresence existingCssTagsPrefixPresence = cssNamesMap.get(selector);
      CssTagsPrefixPresence additionalCssTagsPrefixPresence =
          (hasComponentName) ? CssTagsPrefixPresence.ALWAYS : CssTagsPrefixPresence.NEVER;

      if (existingCssTagsPrefixPresence == null) {
        cssNamesMap.put(selector, additionalCssTagsPrefixPresence);
      } else if (existingCssTagsPrefixPresence != additionalCssTagsPrefixPresence) {
        // this CSS selector string has a prefix in some cases
        cssNamesMap.put(selector, CssTagsPrefixPresence.SOMETIMES);
      } else {
        // Nothing to change.
      }
    }
  }
}
