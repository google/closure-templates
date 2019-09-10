/*
 * Copyright 2019 Google Inc.
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
package com.google.template.soy.invocationbuilders.passes;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.template.soy.shared.internal.gencode.JavaGenerationUtils.appendImmutableSetInline;
import static com.google.template.soy.shared.internal.gencode.JavaGenerationUtils.appendJavadoc;
import static com.google.template.soy.shared.internal.gencode.JavaGenerationUtils.makeUpperCamelCase;

import com.google.auto.value.AutoValue;
import com.google.common.base.Ascii;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.template.soy.base.internal.IndentedLinesBuilder;
import com.google.template.soy.invocationbuilders.javatypes.FutureJavaType;
import com.google.template.soy.invocationbuilders.javatypes.JavaType;
import com.google.template.soy.invocationbuilders.javatypes.ProtoEnumJavaType;
import com.google.template.soy.invocationbuilders.javatypes.ProtoJavaType;
import com.google.template.soy.passes.IndirectParamsCalculator;
import com.google.template.soy.passes.IndirectParamsCalculator.IndirectParamsInfo;
import com.google.template.soy.shared.internal.gencode.GeneratedFile;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.TemplateMetadata;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.TemplateRegistry;
import com.google.template.soy.soytree.Visibility;
import com.google.template.soy.soytree.defn.TemplateParam;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/**
 * Visitor for generating Java template parameter builders (see {@link
 * com.google.template.soy.data.BaseSoyTemplateImpl}) that can be used for invoking Soy templates
 * from Java. One java file will be generated for each soy file, containing template param builders
 * for each template in the soy file.
 *
 * <p>For example, "foo.soy" containing templates "bar" and "baz" would result in FooTemplates.java,
 * with inner classes Bar and Baz.
 */
public final class GenInvocationBuildersVisitor
    extends AbstractSoyNodeVisitor<ImmutableList<GeneratedFile>> {

  private static final Logger logger =
      Logger.getLogger(GenInvocationBuildersVisitor.class.getName());

  private final String javaPackage; // The package name to use for the generated Java files.

  private final IndirectParamsCalculator indirectParamsCalculator;

  private IndentedLinesBuilder ilb; // Line formatter for the generated code.
  private ImmutableList.Builder<GeneratedFile> generatedFiles; // The generated Java files to write.

  // Set of "Foo" class names that we've used already. Occasionally template names will
  // generate the same Java class name (e.g."foo" and "foo_" would both try to generate a
  // "Foo" class, so if "foo_" is not actually marked as visibility="private", then we'd have
  // a collision). For now, we ignore templates that would generate the same name as a previous
  // template, and log a warning.
  private Set<String> paramsClassNamesUsed;

  public GenInvocationBuildersVisitor(String javaPackage, TemplateRegistry templateRegistry) {
    this.javaPackage = javaPackage;
    this.indirectParamsCalculator = new IndirectParamsCalculator(templateRegistry);
  }

  @Override
  public ImmutableList<GeneratedFile> exec(SoyNode node) {
    generatedFiles = new ImmutableList.Builder<>();
    ilb = null;
    visit(node);

    ImmutableList<GeneratedFile> builtFileList = generatedFiles.build();
    logWarningIfFilenamesNotUnique(builtFileList);
    return builtFileList;
  }

  @Override
  protected void visitSoyFileSetNode(SoyFileSetNode node) {
    for (SoyFileNode soyFile : node.getChildren()) {
      visit(soyFile);
    }
  }

  @Override
  protected void visitSoyFileNode(SoyFileNode soyFile) {
    ilb = new IndentedLinesBuilder(2);
    appendFileHeaderAndImports(soyFile);

    String javaClassNameForSoyFile = convertSoyFileNameToJavaClassName(soyFile);

    // Start of *FooTemplates class.
    appendJavadoc(
        ilb,
        "Wrapper class containing {@link BaseSoyTemplateImpl} builders for each template in: "
            + soyFile.getFileName()
            + ".",
        /* forceMultiline= */ false,
        /* wrapAt100Chars= */ true);
    ilb.appendLine("@Generated(\"com.google.template.soy.SoyParseInfoGenerator\")");
    ilb.appendLine("public final class " + javaClassNameForSoyFile + " {");

    ilb.increaseIndent();

    // Add FooParams subclasses for the templates in this file.
    generateParamsClassesForEachTemplate(soyFile);

    // End of *FooTemplates class.
    ilb.decreaseIndent();
    ilb.appendLine("}");

    // Add the file name and contents to the list of generated files to write.
    String fileName = javaClassNameForSoyFile + ".java";
    generatedFiles.add(GeneratedFile.create(fileName, ilb.toString()));
    ilb = null;
  }

  /** For each public, non-delegate template in the given soy file, generates a Foo inner class. */
  private void generateParamsClassesForEachTemplate(SoyFileNode soyFile) {
    paramsClassNamesUsed = new HashSet<>();
    for (TemplateNode template : soyFile.getChildren()) {
      if (template.getVisibility() == Visibility.PUBLIC
          && template.getKind() != SoyNode.Kind.TEMPLATE_DELEGATE_NODE) {
        visit(template);
      }
    }
  }

  /** A report encapsulating how a template file is handled by this class. */
  @AutoValue
  public abstract static class Report {
    protected abstract ImmutableMap<TemplateNode, Boolean> completePerTemplate();

    /** Returns whether all templates in the file are fully handled. */
    public abstract boolean complete();

    protected abstract ImmutableMap<TemplateNode, String> classNamePerTemplate();

    /** Returns the name of the outer class created to hold the SoyTemplate implementations. */
    public abstract String className();

    protected abstract ImmutableSetMultimap<TemplateNode, ParamReport> paramReports();

    public boolean isTemplateComplete(TemplateNode node) {
      return Boolean.TRUE.equals(completePerTemplate().get(node));
    }

    public String getClassName(TemplateNode value) {
      return classNamePerTemplate().get(value);
    }

    public Set<TemplateNode> getTemplates() {
      return completePerTemplate().keySet();
    }

    public Set<ParamReport> getParams(TemplateNode node) {
      return paramReports().get(node);
    }
  }

  /** See {@link GenInvocationBuildersVisitor.ParamReport} */
  public enum ParamStatus {
    HANDLED,
    NAME_COLLISION,
    INDIRECT,
    UNHANDLED_TYPE,
    JAVA_INCOMPATIBLE
  }

  /** See {@link GenInvocationBuildersVisitor.Report#getParams} */
  @AutoValue
  public abstract static class ParamReport {
    public abstract TemplateMetadata.Parameter param();

    public abstract ParamStatus status();
  }

  public Report getReport(SoyFileNode soyFile) {
    boolean allComplete = true;
    String baseClassName = javaPackage + "." + convertSoyFileNameToJavaClassName(soyFile);
    Map<TemplateNode, Boolean> templateComplete = new HashMap<>();
    Map<TemplateNode, String> classNames = new HashMap<>();
    ImmutableSetMultimap.Builder<TemplateNode, ParamReport> paramReports =
        ImmutableSetMultimap.builder();
    Set<String> allClassNames = new HashSet<>();
    for (TemplateNode template : soyFile.getChildren()) {
      if (template.getVisibility() == Visibility.PUBLIC
          && template.getKind() != SoyNode.Kind.TEMPLATE_DELEGATE_NODE) {
        boolean nameCollision = !allClassNames.add(generateTemplateClassName(template));
        boolean complete = templateFullyHandled(template, paramReports) && !nameCollision;
        templateComplete.put(template, complete);
        allComplete = allComplete && complete;
        if (!nameCollision) {
          classNames.put(template, baseClassName + "." + generateTemplateClassName(template));
        }
      }
    }
    return new AutoValue_GenInvocationBuildersVisitor_Report(
        ImmutableMap.copyOf(templateComplete),
        allComplete,
        ImmutableMap.copyOf(classNames),
        baseClassName,
        paramReports.build());
  }

  private boolean templateFullyHandled(
      TemplateNode template, ImmutableSetMultimap.Builder<TemplateNode, ParamReport> paramReports) {
    boolean ok = true;
    Set<String> allNativeNames = new HashSet<>();
    Set<String> allJavaNames = new HashSet<>();
    for (TemplateParam param : template.getParams()) {
      allNativeNames.add(param.name());
      ParamStatus status;
      if (InvocationBuilderTypeUtils.isJavaIncompatible(param.type())) {
        status = ParamStatus.JAVA_INCOMPATIBLE;
      } else if (!allJavaNames.add(getParamSetterSuffix(param.name()))) {
        status = ParamStatus.NAME_COLLISION;
        ok = false;
      } else if (!InvocationBuilderTypeUtils.getJavaTypes(
              param.type(), /* shouldMakeNullable= */ !param.isRequired())
          .isEmpty()) {
        status = ParamStatus.HANDLED;
      } else {
        status = ParamStatus.UNHANDLED_TYPE;
        ok = false;
      }

      paramReports.put(
          template,
          new AutoValue_GenInvocationBuildersVisitor_ParamReport(
              TemplateMetadata.Parameter.fromParam(param), status));
    }

    IndirectParamsInfo idi =
        indirectParamsCalculator.calculateIndirectParams(TemplateMetadata.fromTemplate(template));
    for (String key : idi.indirectParams.keySet()) {
      if (allNativeNames.contains(key)) {
        continue;
      }
      paramReports.put(
          template,
          new AutoValue_GenInvocationBuildersVisitor_ParamReport(
              idi.indirectParams.get(key), ParamStatus.INDIRECT));
      ok = false;
    }

    return ok;
  }

  /**
   * Writes a Foo subclass for the given template. The class extends {@link
   * com.google.template.soy.data.BaseSoyTemplateImpl}, which implements {@link
   * com.google.template.soy.data.SoyTemplate}.
   */
  @Override
  protected void visitTemplateNode(TemplateNode template) {
    Optional<String> templateParamsClassname = getParamsClassNameIfUnique(template);

    // If no java class name was generated for this template, skip over this template.
    if (!templateParamsClassname.isPresent()) {
      return;
    }
    String paramsClass = templateParamsClassname.get();

    // Start of Foo class.
    String templateDescription = template.getSoyDocDesc();
    ilb.appendLine();
    appendJavadoc(
        ilb,
        "Template params for "
            + template.getTemplateNameForUserMsgs()
            + (templateDescription != null ? ": " + templateDescription : "."),
        /* forceMultiline= */ false,
        /* wrapAt100Chars= */ true);
    ilb.appendLine(
        "public static final class "
            + templateParamsClassname.get()
            + " extends BaseSoyTemplateImpl {");
    ilb.increaseIndent();
    ilb.appendLine();
    ilb.appendLine(
        "private static final String TEMPLATE_NAME = \"" + template.getTemplateName() + "\";");
    ilb.appendLine();

    appendFutureWrapperMethod(paramsClass);

    // Constructor for Foo.
    ilb.appendLine("private " + paramsClass + "(java.util.Map<String, SoyValueProvider> data) {");
    ilb.increaseIndent();
    ilb.appendLine("super(TEMPLATE_NAME, data);");
    ilb.decreaseIndent();
    ilb.appendLine("}");

    ilb.appendLine();
    appendParamsBuilderClass(template, templateParamsClassname.get());

    // End of Foo class.
    ilb.decreaseIndent();
    ilb.appendLine("}");
    ilb.appendLine();
  }

  /**
   * Adds a static method to each Params class: {@code public static SoyTemplate.AsyncWrapper<Foo>
   * wrapFuture(ListenableFuture<Foo>)}. This utility is needed for supporting Producers + some Apps
   * Framework utility classes.
   *
   * @see com.google.apps.framework.template.StructuredPageResponse
   */
  private void appendFutureWrapperMethod(String paramsClass) {
    appendJavadoc(
        ilb,
        "Wraps a ListenableFuture<"
            + paramsClass
            + "> as a SoyTemplate.AsyncWrapper<"
            + paramsClass
            + ">",
        false,
        true);
    ilb.appendLine(
        "public static SoyTemplate.AsyncWrapper<"
            + paramsClass
            + "> wrapFuture(ListenableFuture<"
            + paramsClass
            + "> paramsFuture) {");
    ilb.increaseIndent();
    ilb.appendLine("return new SoyTemplate.AsyncWrapper<>(TEMPLATE_NAME, paramsFuture);");
    ilb.decreaseIndent();
    ilb.appendLine("}");
    ilb.appendLine();
  }

  /**
   * Appends a builder class for template "foo" with parameter setting methods. This class extends
   * the {@link com.google.template.soy.data.BaseSoyTemplateImpl.AbstractBuilder} class.
   */
  private void appendParamsBuilderClass(TemplateNode template, String templateParamsClassname) {
    appendJavadoc(ilb, "Creates a new Builder instance.", false, true);
    ilb.appendLine("public static Builder builder() {");
    ilb.increaseIndent();
    ilb.appendLine("return new Builder();");
    ilb.decreaseIndent();
    ilb.appendLine("}");
    ilb.appendLine();

    if (template.getParams().stream().noneMatch(TemplateParam::isRequired)) {
      appendJavadoc(
          ilb,
          "Creates a new instance of "
              + templateParamsClassname
              + " with no parameters set. This method was generated because all template"
              + " parameters are optional.",
          false,
          true);
      ilb.appendLine("public static " + templateParamsClassname + " getDefaultInstance() {");
      ilb.increaseIndent();
      ilb.appendLine("return builder().build();");
      ilb.decreaseIndent();
      ilb.appendLine("}");
      ilb.appendLine();
    }

    // Start of Foo.Builder class.
    ilb.appendLine("@CanIgnoreReturnValue");
    ilb.appendLine(
        "public static class Builder extends AbstractBuilder<Builder, "
            + templateParamsClassname
            + "> {");
    ilb.appendLine();
    ilb.increaseIndent();

    // Add a constant ImmutableSet of type {@link
    // com.google.template.soy.data.BaseSoyTemplateImpl.Param}
    // containing metadata about the template's params.
    String paramsSetConstantName = "PARAMS";
    appendParamsImmutableSetConstant(paramsSetConstantName, template.getParams());

    // Constructor for Foo.Builder.
    ilb.appendLine("private Builder() {");
    ilb.increaseIndent();
    ilb.appendLine("super(TEMPLATE_NAME, " + paramsSetConstantName + ");");
    ilb.decreaseIndent();
    ilb.appendLine("}");
    ilb.appendLine();

    // #buildInternal() for FooTemplate.Builder.
    ilb.appendLine("@Override");
    ilb.appendLine(
        "protected "
            + templateParamsClassname
            + " buildInternal(String name, ImmutableMap<String, SoyValueProvider> data) {");
    ilb.increaseIndent();
    ilb.appendLine("return new " + templateParamsClassname + "(data);");
    ilb.decreaseIndent();
    ilb.appendLine("}");

    // Add setters for each direct template param.
    Set<String> paramUpperCamelCaseNamesUsed = new HashSet<>(); // To prevent collisions.
    Set<String> allParamNames =
        template.getParams().stream()
            .map(t -> makeUpperCamelCase(t.name()))
            .collect(Collectors.toSet());
    template
        .getParams()
        .forEach(
            param ->
                writeSettersForParam(
                    param,
                    param.desc(),
                    allParamNames,
                    paramUpperCamelCaseNamesUsed,
                    template.getTemplateName()));

    ilb.appendLine();

    // End of FooTemplateInvocation.Builder class.
    ilb.decreaseIndent();
    ilb.appendLine("}");
  }

  /** Appends the file header and imports for the generated *FooTemplates.java */
  private void appendFileHeaderAndImports(SoyFileNode soyFile) {
    // Header.
    ilb.appendLine("// This file was automatically generated by the Soy compiler.");
    ilb.appendLine("// Please don't edit this file by hand.");
    ilb.appendLine("// source: " + soyFile.getFilePath()); // For Code Search link.
    ilb.appendLine();
    ilb.appendLine("package " + javaPackage + ";");
    ilb.appendLine();

    // Imports.
    ilb.appendLine("import static com.google.common.collect.ImmutableList.toImmutableList;");
    ilb.appendLine("import static com.google.common.collect.ImmutableMap.toImmutableMap;");
    ilb.appendLine();
    ilb.appendLine("import com.google.common.base.Preconditions;");
    ilb.appendLine("import com.google.common.collect.ImmutableList;");
    ilb.appendLine("import com.google.common.collect.ImmutableMap;");
    ilb.appendLine("import com.google.common.collect.ImmutableSet;");
    ilb.appendLine("import com.google.common.collect.Streams;");
    ilb.appendLine("import com.google.common.html.types.SafeHtml;");
    ilb.appendLine("import com.google.common.html.types.SafeScript;");
    ilb.appendLine("import com.google.common.html.types.SafeStyle;");
    ilb.appendLine("import com.google.common.html.types.SafeStyleSheet;");
    ilb.appendLine("import com.google.common.html.types.SafeUrl;");
    ilb.appendLine("import com.google.common.html.types.TrustedResourceUrl;");
    ilb.appendLine("import com.google.common.util.concurrent.ListenableFuture;");
    ilb.appendLine("import com.google.errorprone.annotations.CanIgnoreReturnValue;");
    ilb.appendLine("import com.google.template.soy.data.BaseSoyTemplateImpl;");
    ilb.appendLine("import com.google.template.soy.data.SanitizedContent;");
    ilb.appendLine("import com.google.template.soy.data.SoyTemplate;");
    ilb.appendLine("import com.google.template.soy.data.SoyValueConverter;");
    ilb.appendLine("import com.google.template.soy.data.SoyValueProvider;");
    ilb.appendLine("import java.util.concurrent.Future;");
    ilb.appendLine("import javax.annotation.Generated;");
    ilb.appendLine("import javax.annotation.Nullable;");
    ilb.appendLine();
    ilb.appendLine();
  }

  /**
   * Appends a constant ImmutableSet of type {@link
   * com.google.template.soy.data.BaseSoyTemplateImpl.Param} containing metadata about the
   * template's params.
   */
  private void appendParamsImmutableSetConstant(
      String constantName, ImmutableList<TemplateParam> params) {
    ImmutableList<String> genCodeForCreatingParams =
        params.stream()
            .map(
                p ->
                    p.isRequired()
                        ? "BaseSoyTemplateImpl.Param.required(\"" + p.name() + "\")"
                        : "BaseSoyTemplateImpl.Param.optional(\"" + p.name() + "\")")
            .collect(toImmutableList());

    ilb.appendLineStart("private static final ImmutableSet<Param> " + constantName + " = ");
    appendImmutableSetInline(ilb, "<BaseSoyTemplateImpl.Param>", genCodeForCreatingParams);
    ilb.appendLineEnd(";");
    ilb.appendLine();
  }

  /**
   * Writes setter methods each of the java types that this param can be (e.g union int | string
   * would generate setFoo(int) and setFoo(string)).
   *
   * <p>TODO(b/77550695): Update docs for how we handle futures.
   */
  private void writeSettersForParam(
      TemplateParam param,
      @Nullable String paramDescription,
      Set<String> allParamNames,
      Set<String> paramUpperCamelCaseNamesUsed,
      String templateName) {

    // Convert the param name to upper camel case. If this generates the same name as another param,
    // log a warning and skip over this param.
    String upperCamelCaseName = getParamSetterSuffix(param.name());
    if (!paramUpperCamelCaseNamesUsed.add(upperCamelCaseName)) {
      logDuplicateParamNameWarning(param.name(), upperCamelCaseName, templateName);
      return;
    }

    // Add setters for this param.
    List<JavaType> javaTypes =
        InvocationBuilderTypeUtils.getJavaTypes(param.type(), /* shouldMakeNullable= */ false);

    javaTypes.forEach(
        javaType -> writeSetter(ilb, param.name(), upperCamelCaseName, paramDescription, javaType));

    // For now only write the future interface if the setter is not already overloaded
    if (javaTypes.size() == 1) {
      JavaType onlyType = javaTypes.get(0);
      if (onlyType.isGenericsTypeSupported()) {
        String futureCamel = upperCamelCaseName + "Future";
        if (allParamNames.contains(futureCamel)) {
          logger.warning(
              String.format(
                  "Achievement unlocked. You have a template with parameters named %s and"
                      + " %sFuture, preventing a future setter from being created for the first"
                      + " parameter.",
                  param.name(), param.name()));
        } else {
          writeFutureSetter(ilb, param.name(), futureCamel, new FutureJavaType(onlyType));
        }
      }
    }
  }

  private static String getParamSetterSuffix(String paramName) {
    return makeUpperCamelCase(paramName);
  }

  /** Writes a setter method for the given param and java type. */
  private static void writeSetter(
      IndentedLinesBuilder ilb,
      String originalParamName,
      String paramNameInUpperCamelCase,
      @Nullable String paramDescription,
      JavaType javaType) {

    String javaTypeString = javaType.toJavaTypeString();
    ilb.appendLine();
    appendJavadoc(
        ilb,
        "Sets "
            + originalParamName
            + (Strings.isNullOrEmpty(paramDescription) ? "." : ": " + paramDescription),
        /* forceMultiline= */ false,
        /* wrapAt100Chars= */ true);

    // Add @Nullable if the type is nullable AND this isn't a proto/proto enum.
    // TODO(b/140632665): Add fix for inserting @Nullable after proto package and before proto name.
    if (javaType.isNullable()
        && !(javaType instanceof ProtoEnumJavaType)
        && !(javaType instanceof ProtoJavaType)) {
      ilb.appendLine(
          ("public Builder set" + paramNameInUpperCamelCase)
              + ("(@Nullable " + javaTypeString + " value) {"));
    } else {
      ilb.appendLine(
          ("public Builder set" + paramNameInUpperCamelCase)
              + ("(" + javaTypeString + " value) {"));
    }
    ilb.increaseIndent();

    String newVariableName = javaType.appendRunTimeOperations(ilb, "value");
    ilb.appendLine("return setParam(\"" + originalParamName + "\", " + newVariableName + ");");
    ilb.decreaseIndent();
    ilb.appendLine("}");
  }

  /** Writes a setter method for the given param and java type. */
  private static void writeFutureSetter(
      IndentedLinesBuilder ilb,
      String originalParamName,
      String futureSetterName,
      FutureJavaType javaType) {

    ilb.appendLine();
    appendJavadoc(
        ilb,
        "Future compatible version of {@link #set"
            + makeUpperCamelCase(originalParamName)
            + "("
            + javaType.getType().toJavaTypeString()
            + ")}.",
        /* forceMultiline= */ false,
        /* wrapAt100Chars= */ true);
    ilb.appendLine(
        ("public Builder set" + futureSetterName)
            + ("(" + javaType.toJavaTypeString() + " future) {"));
    ilb.increaseIndent();

    ilb.appendLine(
        "return setParam(\"" + originalParamName + "\", Preconditions.checkNotNull(future));");
    ilb.decreaseIndent();
    ilb.appendLine("}");
  }

  /** Converts a soy file name to its corresponding *Templates java classname. */
  private static String convertSoyFileNameToJavaClassName(SoyFileNode soyFile) {
    String fileName = soyFile.getFileName();
    if (fileName == null) {
      throw new IllegalArgumentException(
          "Trying to generate Java class name based on Soy file name, but Soy file name was"
              + " not provided.");
    }
    if (Ascii.toLowerCase(fileName).endsWith(".soy")) {
      fileName = fileName.substring(0, fileName.length() - 4);
    }
    return makeUpperCamelCase(fileName) + "Templates";
  }

  // This list should include all short names of classes imported as well as any class in java.lang.
  // For now this gets all targets to compile.
  private static final ImmutableSet<String> RESERVED_NAMES =
      ImmutableSet.of("String", "Override", "Number", "Integer", "Long", "Future");

  /**
   * Converts a template name to its corresponding Foo class name.
   *
   * <p>NOTE: If the java class name has already been used, this returns an empty optional. See
   * {@link #paramsClassNamesUsed} for more info about when this happens.
   */
  private Optional<String> getParamsClassNameIfUnique(TemplateNode template) {
    String className = generateTemplateClassName(template);
    // If this class name has already been used, log a warning and return an empty optional (we will
    // skip over this template).
    if (!paramsClassNamesUsed.add(className)) {
      logDuplicateTemplateNameWarning(template.getTemplateNameForUserMsgs(), className);
      return Optional.empty();
    } else if (RESERVED_NAMES.contains(className)) {
      logger.warning(
          "When generating soy java invocation builders, soy template: "
              + template.getTemplateNameForUserMsgs()
              + " generated a Java UpperCamelCase that is reserved.");
      return Optional.empty();
    }
    return Optional.of(className);
  }

  private static String generateTemplateClassName(TemplateNode template) {
    String namespacedTemplateName = template.getTemplateName();
    String templateName =
        namespacedTemplateName.substring(namespacedTemplateName.lastIndexOf('.') + 1);

    // Convert the template name to upper camel case (stripping non-alphanumeric characters),  (e.g.
    // template "foo" -> "Foo").
    return makeUpperCamelCase(templateName);
  }

  /**
   * Logs a warning if two templates in the same soy file mapped to the same UpperCamelCase java
   * class name.
   */
  private static void logDuplicateTemplateNameWarning(
      String templateName, String generatedClassName) {
    logger.warning(
        "When generating soy java invocation builders, soy template: "
            + templateName
            + " generated the same Java"
            + " UpperCamelCase name as another template in this file.\n"
            + " This template was skipped during invocation builder generation.\n"
            + " To use this api, all soy template names in a given file should be"
            + " unique when converted to UpperCamelCase (with non-alphanumeric characters"
            + " stripped).\n"
            + "The generated Java class name was: "
            + generatedClassName
            + ".");
  }

  /**
   * Logs a warning if two params generate the same upper camel case name (which means we need to
   * skip over the param and not generate setters for it.
   */
  private static void logDuplicateParamNameWarning(
      String templateParamName, String nameAsUpperCamelCase, String templateName) {
    logger.warning(
        "When generating soy java invocation builders, soy template: "
            + templateName
            + " had multiple parameters that converted to the same upper camel case name: "
            + nameAsUpperCamelCase
            + ".\nParam: "
            + templateParamName
            + " is being skipped (no setters will be generated for this param).\n"
            + " To use this api, all parameter names for a given template should be"
            + " unique when converted to UpperCamelCase (with non-alphanumeric characters"
            + " stripped).\n");
  }

  /** Logs a warning if two soy files mapped to the same generated java file name. */
  private static void logWarningIfFilenamesNotUnique(ImmutableList<GeneratedFile> files) {
    ImmutableList<String> duplicateFilenames =
        files.stream()
            .collect(Collectors.groupingBy(GeneratedFile::fileName, Collectors.counting()))
            .entrySet()
            .stream()
            .filter(e -> e.getValue() > 1) // We only care about duplicate filenames.
            .map(e -> e.getKey())
            .collect(toImmutableList());

    for (String fileName : duplicateFilenames) {
      logger.warning(
          "While generating Soy Java invocation builders, multiple files in this soy fileset"
              + " mapped to the same file name: "
              + fileName
              + ".\n"
              + " To use this api, soy file names should be unique when"
              + " converted to UpperCamelCase (with non-alpha-numeric characters stripped).\n");
    }
  }
}
