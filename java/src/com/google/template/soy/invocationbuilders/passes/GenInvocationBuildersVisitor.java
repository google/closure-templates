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

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.internal.IndentedLinesBuilder;
import com.google.template.soy.shared.internal.gencode.GeneratedFile;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.Visibility;
import com.google.template.soy.soytree.defn.TemplateParam;
import com.google.template.soy.types.SoyType;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Visitor for generating Java template parameter builders (see {@link
 * com.google.template.soy.data.BaseParamsImpl}) that can be used for invoking Soy templates from
 * Java. One java file will be generated for each soy file, containing template param builders for
 * each template in the soy file.
 *
 * <p>For example, "foo.soy" containing templates "bar" and "baz" would result in FooTemplates.java,
 * with inner classes BarParams and BazParams.
 */
public final class GenInvocationBuildersVisitor
    extends AbstractSoyNodeVisitor<ImmutableList<GeneratedFile>> {

  private static final Logger logger =
      Logger.getLogger(GenInvocationBuildersVisitor.class.getName());

  private IndentedLinesBuilder ilb; // Line formatter for the generated code.
  private final String javaPackage; // The package name to use for the generated Java files.
  private ImmutableList.Builder<GeneratedFile> generatedFiles; // The generated Java files to write.

  // Set of "FooParams" class names that we've used already. Occasionally template names will
  // generate the same Java class name (e.g."foo" and "foo_" would both try to generate a
  // "FooParams" class, so if "foo_" is not actually marked as visibility="private", then we'd have
  // a collision). For now, we ignore templates that would generate the same name as a previous
  // template, and log a warning.
  private Set<String> paramsClassNamesUsed;

  public GenInvocationBuildersVisitor(String javaPackage) {
    this.javaPackage = javaPackage;
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
        "Wrapper class containing {@link BaseParamsImpl} builders for each template in: "
            + soyFile.getFileName()
            + ".",
        /* forceMultiline= */ false,
        /* wrapAt100Chars= */ true);
    ilb.appendLine("@Generated(\"com.google.template.soy.SoyParseInfoGenerator\")");
    ilb.appendLine("public final class " + javaClassNameForSoyFile + " {");

    ilb.increaseIndent();

    // Add FooParams subclasses for the templates in this file.
    generateFooParamsClassesForEachTemplate(soyFile);

    // End of *FooTemplates class.
    ilb.decreaseIndent();
    ilb.appendLine("}");

    // Add the file name and contents to the list of generated files to write.
    String fileName = javaClassNameForSoyFile + ".java";
    generatedFiles.add(GeneratedFile.create(fileName, ilb.toString()));
    ilb = null;
  }

  /**
   * For each public, non-delegate template in the given soy file, generates a FooParams inner
   * class.
   */
  private void generateFooParamsClassesForEachTemplate(SoyFileNode soyFile) {
    paramsClassNamesUsed = new HashSet<>();
    for (TemplateNode template : soyFile.getChildren()) {
      if (template.getVisibility() == Visibility.PUBLIC
          && template.getKind() != SoyNode.Kind.TEMPLATE_DELEGATE_NODE) {
        visit(template);
      }
    }
  }

  /**
   * Writes a FooParams subclass for the given template. The class extends {@link
   * com.google.template.soy.data.BaseParamsImpl}, which implements {@link
   * com.google.template.soy.data.TemplateParameters}.
   */
  @Override
  protected void visitTemplateNode(TemplateNode template) {
    Optional<String> templateParamsClassname = generateBaseParamsImplClassName(template);

    // If no java class name was generated for this template, skip over this template.
    if (!templateParamsClassname.isPresent()) {
      return;
    }
    String paramsClass = templateParamsClassname.get();

    // Start of FooParams class.
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
        "public static final class " + templateParamsClassname.get() + " extends BaseParamsImpl {");
    ilb.increaseIndent();
    ilb.appendLine();
    ilb.appendLine(
        "public static final String TEMPLATE_NAME = \"" + template.getTemplateName() + "\";");
    ilb.appendLine();

    appendFutureWrapperMethod(paramsClass);

    // Constructor for FooParams.
    ilb.appendLine("private " + paramsClass + "(Map<String, SoyValueProvider> data) {");
    ilb.increaseIndent();
    ilb.appendLine("super(TEMPLATE_NAME, data);");
    ilb.decreaseIndent();
    ilb.appendLine("}");

    ilb.appendLine();
    appendParamsBuilderClass(template, templateParamsClassname.get());

    // End of FooParams class.
    ilb.decreaseIndent();
    ilb.appendLine("}");
    ilb.appendLine();
  }

  /**
   * Adds a static method to each Params class: {@code public static
   * TemplateParameters.AsyncWrapper<FooParams> wrapFuture(ListenableFuture<FooParams>)}. This
   * utility is needed for supporting Producers + some Apps Framework utility classes.
   *
   * @see com.google.apps.framework.template.StructuredPageResponse
   */
  private void appendFutureWrapperMethod(String paramsClass) {
    appendJavadoc(
        ilb,
        "Wraps a ListenableFuture<"
            + paramsClass
            + "> as a TemplateParameters.AsyncWrapper<"
            + paramsClass
            + ">",
        false,
        true);
    ilb.appendLine(
        "public static TemplateParameters.AsyncWrapper<"
            + paramsClass
            + "> wrapFuture(ListenableFuture<"
            + paramsClass
            + "> paramsFuture) {");
    ilb.increaseIndent();
    ilb.appendLine("return new TemplateParameters.AsyncWrapper<>(TEMPLATE_NAME, paramsFuture);");
    ilb.decreaseIndent();
    ilb.appendLine("}");
    ilb.appendLine();
  }

  /**
   * Appends a builder class for template "foo" with parameter setting methods. This class extends
   * the {@link com.google.template.soy.data.BaseParamsImpl.AbstractBuilder} class.
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

    // Start of FooParams.Builder class.
    ilb.appendLine(
        "public static class Builder extends AbstractBuilder<Builder, "
            + templateParamsClassname
            + "> {");
    ilb.appendLine();
    ilb.increaseIndent();

    // Add a constant ImmutableSet of type {@link com.google.template.soy.data.BaseParamsImpl.Param}
    // containing metadata about the template's params.
    String paramsSetConstantName = "PARAMS";
    appendParamsImmutableSetConstant(paramsSetConstantName, template.getParams());

    // Constructor for FooParams.Builder.
    ilb.appendLine("private Builder() {");
    ilb.increaseIndent();
    ilb.appendLine("super(TEMPLATE_NAME, " + paramsSetConstantName + ");");
    ilb.decreaseIndent();
    ilb.appendLine("}");
    ilb.appendLine();

    // #buildInternal() for FooTemplateParams.Builder.
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
    template
        .getParams()
        .forEach(
            param ->
                writeSettersForParam(
                    param.name(),
                    param.type(),
                    Optional.ofNullable(param.desc()),
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
    ilb.appendLine("import com.google.common.util.concurrent.ListenableFuture;");
    ilb.appendLine("import com.google.common.collect.ImmutableMap;");
    ilb.appendLine("import com.google.common.collect.ImmutableSet;");
    ilb.appendLine("import com.google.common.html.types.SafeHtml;");
    ilb.appendLine("import com.google.common.html.types.SafeScript;");
    ilb.appendLine("import com.google.common.html.types.SafeStyle;");
    ilb.appendLine("import com.google.common.html.types.SafeStyleSheet;");
    ilb.appendLine("import com.google.common.html.types.SafeUrl;");
    ilb.appendLine("import com.google.common.html.types.TrustedResourceUrl;");
    ilb.appendLine("import com.google.template.soy.data.SanitizedContent;");
    ilb.appendLine("import com.google.template.soy.data.SoyValueProvider;");
    ilb.appendLine("import com.google.template.soy.data.SoyValueConverter;");
    ilb.appendLine("import com.google.template.soy.data.TemplateParameters;");
    ilb.appendLine("import com.google.template.soy.data.BaseParamsImpl;");
    ilb.appendLine("import java.util.Map;");
    ilb.appendLine("import java.util.Objects;");
    ilb.appendLine("import java.util.concurrent.Future;");
    ilb.appendLine("import javax.annotation.Generated;");
    ilb.appendLine();
    ilb.appendLine();
  }

  /**
   * Appends a constant ImmutableSet of type {@link
   * com.google.template.soy.data.BaseParamsImpl.Param} containing metadata about the template's
   * params.
   */
  private void appendParamsImmutableSetConstant(
      String constantName, ImmutableList<TemplateParam> params) {
    ImmutableList<String> genCodeForCreatingParams =
        params.stream()
            .map(
                p ->
                    p.isRequired()
                        ? "BaseParamsImpl.Param.required(\"" + p.name() + "\")"
                        : "BaseParamsImpl.Param.optional(\"" + p.name() + "\")")
            .collect(toImmutableList());

    ilb.appendLineStart("private static final ImmutableSet<Param> " + constantName + " = ");
    appendImmutableSetInline(ilb, "<BaseParamsImpl.Param>", genCodeForCreatingParams);
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
      String templateParamName,
      SoyType soyType,
      Optional<String> paramDescription,
      Set<String> paramUpperCamelCaseNamesUsed,
      String templateName) {

    // Convert the param name to upper camel case. If this generates the same name as another param,
    // log a warning and skip over this param.
    String upperCamelCaseName = makeUpperCamelCase(templateParamName);
    if (!paramUpperCamelCaseNamesUsed.add(upperCamelCaseName)) {
      logDuplicateParamNameWarning(templateParamName, upperCamelCaseName, templateName);
      return;
    }

    // Add setters for this param.
    InvocationBuilderTypeUtils.getJavaTypes(soyType)
        .forEach(
            (javaType) ->
                writeSetter(
                    ilb, templateParamName, upperCamelCaseName, paramDescription, javaType));
    // TODO(b/77550695): Add future setter once we add supertype impl.
  }

  /** Writes a setter method for the given param and java type. */
  private static void writeSetter(
      IndentedLinesBuilder ilb,
      String originalParamName,
      String paramNameInUpperCamelCase,
      Optional<String> paramDescription,
      String javaType) {
    ilb.appendLine();
    appendJavadoc(
        ilb,
        "Sets "
            + originalParamName
            + (paramDescription.isPresent() ? ": " + paramDescription : "."),
        /* forceMultiline= */ false,
        /* wrapAt100Chars= */ true);
    ilb.appendLine(
        ("public Builder set" + paramNameInUpperCamelCase) + ("(" + javaType + " value) {"));
    ilb.increaseIndent();

    boolean isPrimitiveType = Character.isLowerCase(javaType.charAt(0));
    if (!isPrimitiveType) {
      ilb.appendLine("Objects.requireNonNull(value);");
    }
    ilb.appendLine("return setParam(\"" + originalParamName + "\", value);");
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

  /**
   * Converts a template name to its corresponding FooParams class name.
   *
   * <p>NOTE: If the java class name has already been used, this returns an empty optional. See
   * {@link #paramsClassNamesUsed} for more info about when this happens.
   */
  private Optional<String> generateBaseParamsImplClassName(TemplateNode template) {
    String namespacedTemplateName = template.getTemplateName();
    String templateName =
        namespacedTemplateName.substring(namespacedTemplateName.lastIndexOf('.') + 1);

    // Convert the template name to upper camel case (stripping non-alphanumeric characters), and
    // append "Params" (e.g. template "foo" -> "FooParams").
    String className = makeUpperCamelCase(templateName) + "Params";

    // If this class name has already been used, log a warning and return an empty optional (we will
    // skip over this template).
    if (!paramsClassNamesUsed.add(className)) {
      logDuplicateTemplateNameWarning(template.getTemplateNameForUserMsgs(), className);
      return Optional.empty();
    }
    return Optional.of(className);
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
