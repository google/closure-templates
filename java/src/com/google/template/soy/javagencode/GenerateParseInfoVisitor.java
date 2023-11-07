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

package com.google.template.soy.javagencode;

import static com.google.common.collect.ImmutableSortedSet.toImmutableSortedSet;
import static com.google.template.soy.shared.internal.gencode.JavaGenerationUtils.appendImmutableList;
import static com.google.template.soy.shared.internal.gencode.JavaGenerationUtils.appendImmutableListInline;
import static com.google.template.soy.shared.internal.gencode.JavaGenerationUtils.appendImmutableMap;
import static com.google.template.soy.shared.internal.gencode.JavaGenerationUtils.appendImmutableSet;
import static com.google.template.soy.shared.internal.gencode.JavaGenerationUtils.appendJavadoc;
import static com.google.template.soy.shared.internal.gencode.JavaGenerationUtils.makeUpperCamelCase;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ascii;
import com.google.common.base.CaseFormat;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Ordering;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import com.google.template.soy.base.internal.BaseUtils;
import com.google.template.soy.base.internal.IndentedLinesBuilder;
import com.google.template.soy.exprtree.StringNode;
import com.google.template.soy.passes.IndirectParamsCalculator;
import com.google.template.soy.passes.IndirectParamsCalculator.IndirectParamsInfo;
import com.google.template.soy.shared.internal.BuiltinFunction;
import com.google.template.soy.shared.internal.gencode.GeneratedFile;
import com.google.template.soy.shared.internal.gencode.JavaGenerationUtils;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.FileSetMetadata;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TemplateDelegateNode;
import com.google.template.soy.soytree.TemplateMetadata;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.Visibility;
import com.google.template.soy.soytree.defn.TemplateParam;
import com.google.template.soy.types.SoyTypes;
import com.google.template.soy.types.TemplateType;
import com.google.template.soy.types.TemplateType.Parameter;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeSet;

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
 */
public final class GenerateParseInfoVisitor
    extends AbstractSoyNodeVisitor<ImmutableList<GeneratedFile>> {

  /** Represents the source of the generated Java class names. */
  @VisibleForTesting
  enum JavaClassNameSource {
    /** AaaBbb.soy or aaa_bbb.soy --> AaaBbbSoyInfo. */
    SOY_FILE_NAME,

    /** boo.foo.aaaBbb --> AaaBbbSoyInfo. */
    SOY_NAMESPACE_LAST_PART,

    /** File1SoyInfo, File2SoyInfo, etc. */
    GENERIC;

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
          if (Ascii.toLowerCase(fileName).endsWith(".soy")) {
            fileName = fileName.substring(0, fileName.length() - 4);
          }
          String prefix = makeUpperCamelCase(fileName);
          if (Character.isDigit(prefix.charAt(0))) {
            prefix = "_" + prefix;
          }
          return prefix;

        case SOY_NAMESPACE_LAST_PART:
          String namespace = soyFile.getNamespace();
          String namespaceLastPart = namespace.substring(namespace.lastIndexOf('.') + 1);
          return makeUpperCamelCase(namespaceLastPart);

        case GENERIC:
          return "File";
      }
      throw new AssertionError();
    }
  }

  /** The package name of the generated files. */
  private final String javaPackage;

  /** The source of the generated Java class names. */
  private final JavaClassNameSource javaClassNameSource;

  /** Map from Soy file node to generated Java class name (built at start of pass). */
  private Map<SoyFileNode, String> soyFileToJavaClassNameMap;

  private final String kytheCorpus;

  /** Registry of all templates in the Soy tree. */
  private final FileSetMetadata fileSetMetadata;

  private final SoyFileNodeTransformer soyFileNodeTransformer;

  /** Cache for results of calls to {@code Utils.convertToUpperUnderscore()}. */
  private final Map<String, String> convertedIdents = Maps.newLinkedHashMap();

  /** The contents of the generated Java files. */
  private ImmutableList.Builder<GeneratedFile> generatedFiles;

  /** Builder for the generated code. */
  private IndentedLinesBuilder ilb;

  private SoyFileNodeTransformer.FileInfo builderReport;

  private Set<String> paramFields = new HashSet<>();

  /**
   * @param javaPackage The Java package for the generated classes.
   * @param javaClassNameSource Source of the generated class names. Must be one of "filename",
   *     "namespace", or "generic".
   */
  public GenerateParseInfoVisitor(
      String javaPackage,
      String kytheCorpus,
      String javaClassNameSource,
      FileSetMetadata registry) {
    this.javaPackage = javaPackage;
    this.kytheCorpus = kytheCorpus;
    this.fileSetMetadata = registry;
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
    soyFileNodeTransformer = new SoyFileNodeTransformer(javaPackage, registry);
  }

  @Override
  public ImmutableList<GeneratedFile> exec(SoyNode node) {
    generatedFiles = new ImmutableList.Builder<>();
    ilb = null;
    visit(node);
    return generatedFiles.build();
  }

  // -----------------------------------------------------------------------------------------------
  // Implementations for specific nodes.

  @Override
  protected void visitSoyFileSetNode(SoyFileSetNode node) {
    // Figure out the generated class name for each Soy file, including adding number suffixes
    // to resolve collisions, and then adding the common suffix "SoyInfo".
    Multimap<String, SoyFileNode> baseGeneratedClassNameToSoyFilesMap = LinkedHashMultimap.create();
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
    builderReport = soyFileNodeTransformer.transform(node);

    // Collect the following:
    // + all the public basic/element templates (non-private, non-delegate) in a map from the
    //   upper-underscore template name to the template's node,
    // + all the param keys from all templates (including private),
    // + for each param key, the list of templates that list it directly.
    // + for any params whose type is a proto, get the proto name and Java class name.
    LinkedHashMap<String, TemplateNode> publicBasicTemplateMap = Maps.newLinkedHashMap();
    Set<String> allParamKeys = Sets.newLinkedHashSet();
    SetMultimap<String, TemplateNode> paramKeyToTemplatesMultimap = LinkedHashMultimap.create();
    for (TemplateNode template : node.getTemplates()) {
      if (template.getVisibility() == Visibility.PUBLIC
          && template.getKind() != SoyNode.Kind.TEMPLATE_DELEGATE_NODE) {
        publicBasicTemplateMap.put(
            convertToUpperUnderscore(template.getPartialTemplateName()), template);
      }
      for (TemplateParam param : template.getParams()) {
        if (!param.isImplicit()) {
          allParamKeys.add(param.name());
          paramKeyToTemplatesMultimap.put(param.name(), template);
        }
      }
    }
    SortedSet<String> protoTypes =
        Sets.newTreeSet(JavaGenerationUtils.getProtoTypes(node, node.getSoyTypeRegistry()));
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
    ilb.appendLine("// This file was automatically generated by the Soy compiler.");
    ilb.appendLine("// Please don't edit this file by hand.");
    // This "source" comment makes Code Search link the gencode to the Soy source:
    ilb.appendLine("// source: ", node.getFilePath().path());

    ilb.appendLine();
    ilb.appendLine("package ", javaPackage, ";");
    ilb.appendLine();
    ilb.appendLine("import com.google.common.collect.ImmutableList;");
    ilb.appendLine("import com.google.common.collect.ImmutableMap;");
    ilb.appendLine("import com.google.common.collect.ImmutableSet;");
    ilb.appendLine("import com.google.common.collect.ImmutableSortedSet;");
    if (!protoTypes.isEmpty()) {
      ilb.appendLine("import com.google.protobuf.Descriptors.FileDescriptor;");
    }
    ilb.appendLine("import com.google.template.soy.parseinfo.SoyFileInfo;");
    ilb.appendLine("import com.google.template.soy.parseinfo.SoyTemplateInfo;");
    ilb.appendLine("import javax.annotation.Generated;");

    // ------ Class start. ------
    ilb.appendLine();
    ilb.appendLine();
    appendJavadoc(
        ilb,
        deprecatedJavaDoc(
            "Soy parse info for " + node.getFileName() + ".",
            builderReport.complete(),
            builderReport.fqClassName()),
        true,
        false);

    deprecatedAnnotation(ilb, builderReport.complete());
    ilb.appendLine("@Generated(\"com.google.template.soy.SoyParseInfoGenerator\")");
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
      ilb.appendLine("@Override public ImmutableList<FileDescriptor> getProtoDescriptors() {");
      ilb.increaseIndent();
      // Note we use fully-qualified names instead of imports to avoid potential collisions.
      List<String> defaultInstances = Lists.newArrayList();
      defaultInstances.addAll(protoTypes);
      ilb.appendLineStart("return ");
      appendImmutableListInline(ilb, /* typeParamSnippet= */ "", defaultInstances);
      ilb.appendLineEnd(";");
      ilb.decreaseIndent();
      ilb.appendLine("}");
    }

    // ------ Template names. ------
    ilb.appendLine();
    ilb.appendLine();
    deprecatedAnnotation(ilb, builderReport.complete());
    ilb.appendLine("public static final class TemplateName {");
    ilb.increaseIndent();
    ilb.appendLine("private TemplateName() {}");
    ilb.appendLine();

    for (Map.Entry<String, TemplateNode> templateEntry : publicBasicTemplateMap.entrySet()) {
      String javadocSb =
          "The full template name of the "
              + templateEntry.getValue().getPartialTemplateName()
              + " template.";
      appendJavadoc(ilb, javadocSb, false, true);
      ilb.appendLine(
          "public static final String ",
          templateEntry.getKey(),
          " = \"",
          templateEntry.getValue().getTemplateName(),
          "\";");
      ilb.appendLine(
          "public static final com.google.template.soy.parseinfo.TemplateName ",
          templateEntry.getKey(),
          "__NAME = com.google.template.soy.parseinfo.TemplateName.of(",
          templateEntry.getKey(),
          ");");
    }

    ilb.decreaseIndent();
    ilb.appendLine("}");

    // ------ Params. ------
    ilb.appendLine();
    ilb.appendLine();
    ilb.appendLine("/**");
    ilb.appendLine(" * Param names from all templates in this Soy file.");
    ilb.appendLine(" */");
    deprecatedAnnotation(ilb, builderReport.complete());
    ilb.appendLine("public static final class Param {");
    ilb.increaseIndent();
    ilb.appendLine("private Param() {}");
    ilb.appendLine();

    paramFields.clear();
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
        javadocSb.append(buildTemplateNameForJavadoc(node, fileSetMetadata.getTemplate(template)));
      }
      javadocSb.append('.');
      appendJavadoc(ilb, javadocSb.toString(), false, true);

      paramFields.add(upperUnderscoreKey);
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
    ilb.appendLine("__NAMESPACE__,");

    // Templates.
    List<String> itemSnippets = Lists.newArrayList();
    itemSnippets.addAll(publicBasicTemplateMap.keySet());
    appendImmutableList(ilb, "<SoyTemplateInfo>", itemSnippets);
    ilb.appendLineEnd(",");

    // CSS names.
    SortedSet<String> cssNames =
        collectCssNames(node).stream()
            .map(s -> String.format("\"%s\"", s))
            .collect(toImmutableSortedSet(Ordering.natural()));
    appendImmutableSet(ilb, "<String>", cssNames);
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

    generatedFiles.add(GeneratedFile.create(javaClassName + ".java", ilb.toString()));
    ilb = null;
  }

  @Override
  protected void visitTemplateNode(TemplateNode node) {
    // Don't generate anything for private or delegate templates.
    if (node.getVisibility() != Visibility.PUBLIC || node instanceof TemplateDelegateNode) {
      return;
    }

    // First build list of all transitive params (direct and indirect).
    Set<String> directParamNames = Sets.newHashSet();
    // Direct params.
    for (TemplateParam param : node.getParams()) {
      if (!param.isImplicit()) {
        directParamNames.add(param.name());
      }
    }

    TemplateMetadata nodeMetadata = fileSetMetadata.getTemplate(node);
    // Indirect params.
    IndirectParamsInfo indirectParamsInfo =
        new IndirectParamsCalculator(fileSetMetadata).calculateIndirectParams(node);

    @SuppressWarnings("ConstantConditions") // for IntelliJ
    String upperUnderscoreName = convertToUpperUnderscore(node.getPartialTemplateName());
    String templateInfoClassName =
        CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, upperUnderscoreName)
            + "SoyTemplateInfo";

    SoyFileNodeTransformer.TemplateInfo templateInfo = builderReport.findTemplate(node);
    boolean isDeprecated = templateInfo.complete();

    // ------ *SoyTemplateInfo class start. ------
    ilb.appendLine();
    ilb.appendLine();
    appendJavadoc(
        ilb,
        deprecatedJavaDoc(
            Optional.ofNullable(node.getSoyDocDesc()).orElse(""),
            isDeprecated,
            templateInfo.fqClassName()),
        true,
        false);
    deprecatedAnnotation(ilb, isDeprecated);
    ilb.appendLine(
        "public static final class ", templateInfoClassName, " extends SoyTemplateInfo {");
    ilb.increaseIndent();

    // ------ Constants for template name. ------
    String templateFieldName = convertToUpperUnderscore(node.getPartialTemplateName());

    ilb.appendLine();
    ilb.appendLine("/** This template's full name. */");
    ilb.appendLine("public static final String __NAME__ = TemplateName.", templateFieldName, ";");
    ilb.appendLine(
        "private static final com.google.template.soy.parseinfo.TemplateName __TEMPLATE_NAME__ =",
        " TemplateName.",
        templateFieldName,
        "__NAME",
        ";");

    // ------ Param constants. ------
    boolean hasSeenFirstDirectParam = false;
    boolean hasSwitchedToIndirectParams = false;
    // Direct params.
    for (TemplateParam param : node.getParams()) {
      if (param.isImplicit()) {
        continue;
      }
      if (!hasSeenFirstDirectParam) {
        ilb.appendLine();
        hasSeenFirstDirectParam = true;
      }
      if (param.desc() != null) {
        appendJavadoc(ilb, param.desc(), false, false);
      }
      // The actual param field.
      String fieldName = convertToUpperUnderscore(param.name());
      ilb.appendLine(
          "public static final String ",
          convertToUpperUnderscore(param.name()),
          " = ",
          (paramFields.contains(fieldName) ? "Param." + fieldName : "\"" + param.name() + "\""),
          ";");
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

      String fieldName = convertToUpperUnderscore(param.getName());
      // The actual param field.
      ilb.appendLine(
          "public static final String ",
          fieldName,
          " = ",
          (paramFields.contains(fieldName) ? "Param." + fieldName : "\"" + param.getName() + "\""),
          ";");
    }

    // ------ Constructor. ------
    ilb.appendLine();
    ilb.appendLine("private ", templateInfoClassName, "() {");
    ilb.increaseIndent();

    ilb.appendLine("super(");
    ilb.increaseIndent(2);
    ilb.appendLine("__NAME__,");
    ilb.appendLine("__TEMPLATE_NAME__,");

    if (!nodeMetadata.getTemplateType().getParameters().isEmpty()
        || !indirectParamsInfo.indirectParams.isEmpty()) {
      ImmutableMap.Builder<String, String> entrySnippetPairs = ImmutableMap.builder();
      Set<String> seenParams = Sets.newHashSet();
      for (Parameter param :
          Iterables.concat(
              nodeMetadata.getTemplateType().getParameters(),
              indirectParamsInfo.indirectParams.values())) {
        if (param.isImplicit()) {
          continue;
        }
        if (seenParams.add(param.getName())) {
          entrySnippetPairs.put(
              convertToUpperUnderscore(param.getName()),
              isRequired(param) ? "ParamRequisiteness.REQUIRED" : "ParamRequisiteness.OPTIONAL");
        }
      }
      appendImmutableMap(ilb, "<String, ParamRequisiteness>", entrySnippetPairs.build());
      ilb.appendLineEnd(");");
    } else {
      ilb.appendLine("ImmutableMap.<String, ParamRequisiteness>of());");
    }

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
    deprecatedAnnotation(ilb, isDeprecated);
    ilb.appendLine("public static final ", templateInfoClassName, " ", upperUnderscoreName, " =");
    ilb.increaseIndent(2);
    ilb.appendLine(templateInfoClassName, ".getInstance();");
    ilb.decreaseIndent(2);
  }

  private static boolean isRequired(Parameter param) {
    // TODO(b/291132644): If possible stop treating |null as optional.
    return param.isRequired() && !SoyTypes.isNullable(param.getType());
  }

  private static void deprecatedAnnotation(IndentedLinesBuilder ilb, boolean deprecated) {
    if (deprecated) {
      ilb.appendLine("@Deprecated");
    }
  }

  private static String deprecatedJavaDoc(String content, boolean deprecated, String useInstead) {
    if (deprecated) {
      String instead = "";
      if (!Strings.isNullOrEmpty(useInstead)) {
        instead = " Use {@link " + useInstead + "} instead.";
      }

      String dep = "@deprecated" + instead;
      return content.isEmpty() ? dep : content + "\n\n" + dep;
    }
    return content;
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
    return convertedIdents.computeIfAbsent(ident, BaseUtils::convertToUpperUnderscore);
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
        && template.getTemplateType().getTemplateKind() != TemplateType.TemplateKind.DELTEMPLATE) {
      resultSb.append(
          template.getTemplateName().substring(template.getTemplateName().lastIndexOf('.')));
    } else {
      switch (template.getTemplateType().getTemplateKind()) {
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
    if (template.getTemplateType().getTemplateKind() == TemplateType.TemplateKind.DELTEMPLATE) {
      resultSb.append(" (delegate)");
    }

    return resultSb.toString();
  }

  private static SortedSet<String> collectCssNames(SoyNode node) {
    SortedSet<String> cssNames = new TreeSet<>();
    SoyTreeUtils.allFunctionInvocations(node, BuiltinFunction.CSS)
        .forEach(
            fn -> {
              String selector = ((StringNode) Iterables.getLast(fn.getParams())).getValue();
              cssNames.add(selector);
            });

    return cssNames;
  }
}
