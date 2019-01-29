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
import com.google.template.soy.exprtree.FieldAccessNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.GlobalNode;
import com.google.template.soy.exprtree.ProtoInitNode;
import com.google.template.soy.exprtree.StringNode;
import com.google.template.soy.internal.proto.ProtoUtils;
import com.google.template.soy.parseinfo.SoyFileInfo.CssTagsPrefixPresence;
import com.google.template.soy.passes.IndirectParamsCalculator;
import com.google.template.soy.passes.IndirectParamsCalculator.IndirectParamsInfo;
import com.google.template.soy.plugin.java.internal.PluginInstanceFinder;
import com.google.template.soy.plugin.java.restricted.SoyJavaSourceFunction;
import com.google.template.soy.shared.internal.BuiltinFunction;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TemplateBasicNode;
import com.google.template.soy.soytree.TemplateDelegateNode;
import com.google.template.soy.soytree.TemplateMetadata;
import com.google.template.soy.soytree.TemplateMetadata.Parameter;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.TemplateRegistry;
import com.google.template.soy.soytree.Visibility;
import com.google.template.soy.soytree.defn.HeaderParam;
import com.google.template.soy.soytree.defn.TemplateParam;
import com.google.template.soy.types.AbstractMapType;
import com.google.template.soy.types.ListType;
import com.google.template.soy.types.RecordType;
import com.google.template.soy.types.SoyProtoEnumType;
import com.google.template.soy.types.SoyProtoType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyType.Kind;
import com.google.template.soy.types.SoyTypeRegistry;
import com.google.template.soy.types.UnionType;
import com.google.template.soy.types.VeType;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
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
          String namespaceLastPart = namespace.substring(namespace.lastIndexOf('.') + 1);
          return makeUpperCamelCase(namespaceLastPart);

        case GENERIC:
          return "File";
      }
      throw new AssertionError();
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

  private final SoyTypeRegistry typeRegistry;

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
      String javaPackage,
      String javaClassNameSource,
      TemplateRegistry registry,
      SoyTypeRegistry typeRegistry) {
    this.javaPackage = javaPackage;
    this.templateRegistry = registry;
    this.typeRegistry = typeRegistry;
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

    // Run the pass.
    for (SoyFileNode soyFile : node.getChildren()) {
      visit(soyFile);
    }
  }

  @Override
  protected void visitSoyFileNode(SoyFileNode node) {
    String javaClassName = soyFileToJavaClassNameMap.get(node);

    // Collect the following:
    // + all the public basic templates (non-private, non-delegate) in a map from the
    //   upper-underscore template name to the template's node,
    // + all the param keys from all templates (including private),
    // + for each param key, the list of templates that list it directly.
    // + for any params whose type is a proto, get the proto name and Java class name.
    // + all plugin instances used by any SoyJavaSourceFunctions
    LinkedHashMap<String, TemplateNode> publicBasicTemplateMap = Maps.newLinkedHashMap();
    Set<String> allParamKeys = Sets.newHashSet();
    SetMultimap<String, TemplateNode> paramKeyToTemplatesMultimap = LinkedHashMultimap.create();
    SortedSet<String> protoTypes = Sets.newTreeSet();
    Map<String, String> pluginInstances = new TreeMap<>();
    for (TemplateNode template : node.getChildren()) {
      if (template.getVisibility() == Visibility.PUBLIC && template instanceof TemplateBasicNode) {
        publicBasicTemplateMap.put(
            convertToUpperUnderscore(template.getPartialTemplateName().substring(1)), template);
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
      // TODO(b/77597955): Scan all expressions, to pick up types from function return values and
      // anything else that may have a type now or in the future.
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
      // Add plugin instances
      for (FunctionNode fnNode : SoyTreeUtils.getAllNodesOfType(template, FunctionNode.class)) {
        if (fnNode.getSoyFunction() instanceof SoyJavaSourceFunction
            && !pluginInstances.containsKey(fnNode.getFunctionName())) {
          Set<Class<?>> instances =
              PluginInstanceFinder.find(
                  (SoyJavaSourceFunction) fnNode.getSoyFunction(), fnNode.numChildren());
          if (!instances.isEmpty()) {
            // We guarantee there's either 0 or 1 instances in the plugin because we already
            // passed through PluginResolver, which checked this.
            pluginInstances.put(
                fnNode.getFunctionName(), Iterables.getOnlyElement(instances).getName());
          }
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
    if (!protoTypes.isEmpty()) {
      ilb.appendLine("import com.google.protobuf.Descriptors.GenericDescriptor;");
    }
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
      ilb.appendLine("@Override public ImmutableList<GenericDescriptor> getProtoDescriptors() {");
      ilb.increaseIndent();
      // Note we use fully-qualified names instead of imports to avoid potential collisions.
      List<String> defaultInstances = Lists.newArrayList();
      defaultInstances.addAll(protoTypes);
      appendListOrSetHelper(ilb, "return ImmutableList.<GenericDescriptor>of", defaultInstances);
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

    for (Map.Entry<String, TemplateNode> templateEntry : publicBasicTemplateMap.entrySet()) {
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
        javadocSb.append(buildTemplateNameForJavadoc(node, templateRegistry.getMetadata(template)));
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
    ImmutableMap.Builder<String, String> cssTagPrefixes = ImmutableMap.builder();
    for (Map.Entry<String, CssTagsPrefixPresence> entry : cssNameMap.entrySet()) {
      cssTagPrefixes.put(
          "\"" + entry.getKey() + "\"", "CssTagsPrefixPresence." + entry.getValue().name());
    }
    appendImmutableMap(ilb, "<String, CssTagsPrefixPresence>", cssTagPrefixes.build());
    ilb.appendLineEnd(",");

    // Plugin Instances
    ImmutableMap.Builder<String, String> pluginInstanceSnippets = ImmutableMap.builder();
    for (Map.Entry<String, String> entry : pluginInstances.entrySet()) {
      pluginInstanceSnippets.put("\"" + entry.getKey() + "\"", "\"" + entry.getValue() + "\"");
    }
    appendImmutableMap(ilb, "<String, String>", pluginInstanceSnippets.build());
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
    if (node.getVisibility() != Visibility.PUBLIC || node instanceof TemplateDelegateNode) {
      return;
    }

    // First build list of all transitive params (direct and indirect).
    Set<String> directParamNames = new HashSet<>();
    // Direct params.
    for (TemplateParam param : node.getParams()) {
      directParamNames.add(param.name());
    }

    TemplateMetadata nodeMetadata = templateRegistry.getMetadata(node);
    // Indirect params.
    IndirectParamsInfo indirectParamsInfo =
        new IndirectParamsCalculator(templateRegistry).calculateIndirectParams(nodeMetadata);

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
    // Direct params.
    for (TemplateParam param : node.getParams()) {
      if (!hasSeenFirstDirectParam) {
        ilb.appendLine();
        hasSeenFirstDirectParam = true;
      }
      if (param.desc() != null) {
        appendJavadoc(ilb, param.desc(), false, false);
      }
      // The actual param field.
      ilb.appendLine(
          "public static final String ",
          convertToUpperUnderscore(param.name()),
          " = \"",
          param.name(),
          "\";");
    }
    for (Parameter param : indirectParamsInfo.indirectParams.values()) {
      if (directParamNames.contains(param.getName())) {
        continue;
      }

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
      for (TemplateMetadata transitiveCallee :
          indirectParamsInfo.paramKeyToCalleesMultimap.get(param.getName())) {
        String javadocCalleeName = buildTemplateNameForJavadoc(node.getParent(), transitiveCallee);
        sortedJavadocCalleeNames.add(javadocCalleeName);
      }

      // Generate the Javadoc.
      String javadoc = "Listed by " + Joiner.on(", ").join(sortedJavadocCalleeNames) + ".";
      appendJavadoc(ilb, javadoc, /* forceMultiline= */ false, /* wrapAt100Chars= */ true);

      // The actual param field.
      ilb.appendLine(
          "public static final String ",
          convertToUpperUnderscore(param.getName()),
          " = \"",
          param.getName(),
          "\";");
    }

    // ------ Constructor. ------
    ilb.appendLine();
    ilb.appendLine("private ", templateInfoClassName, "() {");
    ilb.increaseIndent();

    ilb.appendLine("super(");
    ilb.increaseIndent(2);
    ilb.appendLine("\"", node.getTemplateName(), "\",");

    if (!nodeMetadata.getParameters().isEmpty() || !indirectParamsInfo.indirectParams.isEmpty()) {
      ImmutableMap.Builder<String, String> entrySnippetPairs = ImmutableMap.builder();
      Set<String> seenParams = new HashSet<>();
      for (Parameter param :
          Iterables.concat(
              nodeMetadata.getParameters(), indirectParamsInfo.indirectParams.values())) {
        if (seenParams.add(param.getName())) {
          entrySnippetPairs.put(
              "\"" + param.getName() + "\"",
              param.isRequired() ? "ParamRequisiteness.REQUIRED" : "ParamRequisiteness.OPTIONAL");
        }
      }
      appendImmutableMap(ilb, "<String, ParamRequisiteness>", entrySnippetPairs.build());
      ilb.appendLineEnd(",");
    } else {
      ilb.appendLine("ImmutableMap.<String, ParamRequisiteness>of(),");
    }

    ilb.appendLine("\"", node.getAutoescapeMode().getAttributeValue(), "\");");
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
    String result = convertedIdents.computeIfAbsent(ident, BaseUtils::convertToUpperUnderscore);
    return result;
  }

  /**
   * Recursively search for protocol buffer types within the given type.
   *
   * @param type The type to search.
   * @param protoTypes Output set.
   */
  private void findProtoTypesRecurse(SoyType type, SortedSet<String> protoTypes) {
    switch (type.getKind()) {
      case PROTO:
        protoTypes.add(((SoyProtoType) type).getDescriptorExpression());
        break;

      case PROTO_ENUM:
        protoTypes.add(((SoyProtoEnumType) type).getDescriptorExpression());
        break;

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
      case LEGACY_OBJECT_MAP:
        {
          AbstractMapType mapType = (AbstractMapType) type;
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
      case VE:
        {
          VeType veType = (VeType) type;
          if (veType.getDataType().isPresent()) {
            // Don't grab the proto type for ve<null>
            SoyType soyType = typeRegistry.getType(veType.getDataType().get());
            if (soyType.getKind() == Kind.PROTO) {
              protoTypes.add(((SoyProtoType) soyType).getDescriptorExpression());
            }
          }
          break;
        }

      case ANY:
      case UNKNOWN:
      case ERROR:
      case NULL:
      case BOOL:
      case INT:
      case FLOAT:
      case STRING:
      case HTML:
      case ATTRIBUTES:
      case JS:
      case CSS:
      case URI:
      case TRUSTED_RESOURCE_URI:
      case VE_DATA:
        // continue
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
      SoyFileNode currSoyFile, TemplateMetadata template) {

    StringBuilder resultSb = new StringBuilder();

    if (template.getSourceLocation().getFilePath().equals(currSoyFile.getFilePath())
        && template.getTemplateKind() != TemplateMetadata.Kind.DELTEMPLATE) {
      resultSb.append(
          template.getTemplateName().substring(template.getTemplateName().lastIndexOf('.')));
    } else {
      switch (template.getTemplateKind()) {
        case BASIC:
        case ELEMENT:
          resultSb.append(template.getTemplateName());
          break;
        case DELTEMPLATE:
          resultSb.append(template.getDelTemplateName());
          if (!template.getDelTemplateVariant().isEmpty()) {
            resultSb.append(':');
            resultSb.append(template.getDelTemplateVariant());
          }
          break;
      }
    }

    if (template.getVisibility() != Visibility.PUBLIC) {
      resultSb.append(" (private)");
    }
    if (template.getTemplateKind() == TemplateMetadata.Kind.DELTEMPLATE) {
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
      IndentedLinesBuilder ilb, String typeParamSnippet, Map<String, String> entrySnippetPairs) {
    if (entrySnippetPairs.isEmpty()) {
      ilb.appendLineStart("ImmutableMap.", typeParamSnippet, "of()");

    } else {
      ilb.appendLine("ImmutableMap.", typeParamSnippet, "builder()");
      for (Map.Entry<String, String> entrySnippetPair : entrySnippetPairs.entrySet()) {
        ilb.appendLine(
            "    .put(", entrySnippetPair.getKey(), ", ", entrySnippetPair.getValue(), ")");
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
      for (FunctionNode fn : SoyTreeUtils.getAllFunctionInvocations(node, BuiltinFunction.CSS)) {
        String selector = ((StringNode) Iterables.getLast(fn.getChildren())).getValue();
        collectSelector(selector, fn.numChildren() > 1);
      }

      return cssNamesMap;
    }

    private void collectSelector(String selector, boolean hasComponentName) {
      CssTagsPrefixPresence existingCssTagsPrefixPresence = cssNamesMap.get(selector);
      CssTagsPrefixPresence additionalCssTagsPrefixPresence =
          hasComponentName ? CssTagsPrefixPresence.ALWAYS : CssTagsPrefixPresence.NEVER;

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
