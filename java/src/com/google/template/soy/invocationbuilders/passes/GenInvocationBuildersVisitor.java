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
import static com.google.template.soy.invocationbuilders.javatypes.CodeGenUtils.ADD_TO_LIST_PARAM;
import static com.google.template.soy.invocationbuilders.javatypes.CodeGenUtils.AS_RECORD;
import static com.google.template.soy.invocationbuilders.javatypes.CodeGenUtils.INDIRECT_P;
import static com.google.template.soy.invocationbuilders.javatypes.CodeGenUtils.INIT_LIST_PARAM;
import static com.google.template.soy.invocationbuilders.javatypes.CodeGenUtils.INJECTED_P;
import static com.google.template.soy.invocationbuilders.javatypes.CodeGenUtils.SET_PARAM_INTERNAL;
import static com.google.template.soy.invocationbuilders.javatypes.CodeGenUtils.STANDARD_P;
import static com.google.template.soy.shared.internal.gencode.JavaGenerationUtils.appendFunctionCallWithParamsOnNewLines;
import static com.google.template.soy.shared.internal.gencode.JavaGenerationUtils.appendJavadoc;
import static com.google.template.soy.shared.internal.gencode.JavaGenerationUtils.makeLowerCamelCase;
import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.template.soy.base.internal.IndentedLinesBuilder;
import com.google.template.soy.invocationbuilders.javatypes.CodeGenUtils;
import com.google.template.soy.invocationbuilders.javatypes.FutureJavaType;
import com.google.template.soy.invocationbuilders.javatypes.JavaType;
import com.google.template.soy.invocationbuilders.javatypes.RecordJavaType;
import com.google.template.soy.invocationbuilders.passes.SoyFileNodeTransformer.FileInfo;
import com.google.template.soy.invocationbuilders.passes.SoyFileNodeTransformer.ParamInfo;
import com.google.template.soy.invocationbuilders.passes.SoyFileNodeTransformer.ParamStatus;
import com.google.template.soy.invocationbuilders.passes.SoyFileNodeTransformer.TemplateInfo;
import com.google.template.soy.shared.internal.gencode.GeneratedFile;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.TemplateRegistry;
import com.google.template.soy.types.SoyTypeRegistry;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

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

  private static final String TEMPLATE_NAME_FIELD = "__NAME__";
  private static final String PARAMS_FIELD = "__PARAMS__";
  private static final String PROTOS_FIELD = "__PROTOS__";
  private static final String DEFAULT_INSTANCE_FIELD = "__DEFAULT_INSTANCE__";

  private final SoyFileNodeTransformer transformer;

  private IndentedLinesBuilder ilb; // Line formatter for the generated code.
  private ImmutableList.Builder<GeneratedFile> generatedFiles; // The generated Java files to write.

  public GenInvocationBuildersVisitor(String javaPackage, TemplateRegistry templateRegistry) {
    this.transformer = new SoyFileNodeTransformer(javaPackage, templateRegistry);
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
    FileInfo fileInfo = transformer.transform(soyFile);
    ilb = new IndentedLinesBuilder(2);
    appendFileHeaderAndImports(fileInfo);

    String javaClassNameForSoyFile = fileInfo.className();

    // Start of *FooTemplates class.
    appendJavadoc(
        ilb,
        "Wrapper class containing {@link com.google.template.soy.data.SoyTemplate} builders for"
            + " each template in: "
            + fileInfo.soyFileName()
            + ".",
        /* forceMultiline= */ false,
        /* wrapAt100Chars= */ true);
    ilb.appendLine(
        "@javax.annotation.Generated(\"com.google.template.soy.SoyParseInfoGenerator\")");
    ilb.appendLine("public final class " + javaClassNameForSoyFile + " {");

    ilb.increaseIndent();

    appendProtoDescriptors(fileInfo, soyFile.getSoyTypeRegistry());

    // Add FooParams subclasses for the templates in this file.
    generateParamsClassesForEachTemplate(fileInfo);

    // End of *FooTemplates class.
    ilb.decreaseIndent();
    ilb.appendLine("}");

    // Add the file name and contents to the list of generated files to write.
    String fileName = javaClassNameForSoyFile + ".java";
    generatedFiles.add(GeneratedFile.create(fileName, ilb.toString()));
    ilb = null;
  }

  /** For each public, non-delegate template in the given soy file, generates a Foo inner class. */
  private void generateParamsClassesForEachTemplate(FileInfo soyFile) {
    soyFile
        .templates()
        .forEach(
            t -> {
              switch (t.status()) {
                case HANDLED:
                  visitTemplateInfo(t);
                  break;
                case NAME_COLLISION:
                  logDuplicateTemplateNameWarning(t.templateName(), t.className());
                  break;
              }
            });
  }

  /**
   * Writes a Foo subclass for the given template. The class extends {@link
   * com.google.template.soy.data.BaseSoyTemplateImpl}, which implements {@link
   * com.google.template.soy.data.SoyTemplate}.
   */
  private void visitTemplateInfo(TemplateInfo template) {
    String paramsClass = template.className();

    // Start of Foo class.
    String templateDescription = template.soyDocDesc();
    ilb.appendLine();
    appendJavadoc(
        ilb,
        "Template params for "
            + template.templateNameForUserMsgs()
            + (templateDescription != null ? ": " + templateDescription : "."),
        /* forceMultiline= */ false,
        /* wrapAt100Chars= */ true);
    ilb.appendLine(
        "public static final class "
            + paramsClass
            + " extends com.google.template.soy.data.BaseSoyTemplateImpl {");
    ilb.increaseIndent();
    ilb.appendLine();
    ilb.appendLine(
        "private static final java.lang.String "
            + TEMPLATE_NAME_FIELD
            + " = \""
            + template.templateName()
            + "\";");
    ilb.appendLine();

    appendFutureWrapperMethod(paramsClass);

    // Constructor for Foo.
    ilb.appendLine(
        "private "
            + paramsClass
            + "(com.google.common.collect.ImmutableMap<java.lang.String,"
            + " com.google.template.soy.data.SoyValueProvider> data) {");
    ilb.increaseIndent();
    ilb.appendLine("super(data);");
    ilb.decreaseIndent();
    ilb.appendLine("}");

    ilb.appendLine();

    ilb.appendLine("@java.lang.Override");
    ilb.appendLine("public final java.lang.String getTemplateName() {");
    ilb.increaseIndent();
    ilb.appendLine("return " + TEMPLATE_NAME_FIELD + ";");
    ilb.decreaseIndent();
    ilb.appendLine("}");
    ilb.appendLine();

    appendParamsBuilderClass(template, paramsClass);

    // End of Foo class.
    ilb.decreaseIndent();
    ilb.appendLine("}");
    ilb.appendLine();
  }

  private void appendProtoDescriptors(FileInfo fileInfo, SoyTypeRegistry typeRegistry) {
    List<String> protoTypes =
        fileInfo.getProtoTypes(typeRegistry).stream().sorted().collect(toList());

    if (protoTypes.isEmpty()) {
      return;
    }

    ilb.appendLine();
    appendJavadoc(
        ilb,
        "The list of protos used by all templates (public and private) in this Soy file, which are "
            + "used by 1) the edit-refresh development compiler and 2) the java compiler to "
            + "enforce strict proto deps.",
        false,
        true);
    ilb.appendLineStart(
        "private static final com.google.common.collect.ImmutableList<"
            + "com.google.protobuf.Descriptors.FileDescriptor> "
            + PROTOS_FIELD
            + " = ");
    appendFunctionCallWithParamsOnNewLines(
        ilb, "com.google.common.collect.ImmutableList.of", protoTypes);
    ilb.appendLineEnd(";");
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
        "public static com.google.template.soy.data.SoyTemplate.AsyncWrapper<"
            + paramsClass
            + "> wrapFuture(com.google.common.util.concurrent.ListenableFuture<"
            + paramsClass
            + "> paramsFuture) {");
    ilb.increaseIndent();
    ilb.appendLine(
        "return new com.google.template.soy.data.SoyTemplate.AsyncWrapper<>("
            + TEMPLATE_NAME_FIELD
            + ", paramsFuture);");
    ilb.decreaseIndent();
    ilb.appendLine("}");
    ilb.appendLine();
  }

  /**
   * Appends a builder class for template "foo" with parameter setting methods. This class extends
   * the {@link com.google.template.soy.data.BaseSoyTemplateImpl.AbstractBuilder} class.
   */
  private void appendParamsBuilderClass(TemplateInfo template, String templateParamsClassname) {
    appendJavadoc(ilb, "Creates a new Builder instance.", false, true);
    ilb.appendLine("public static Builder builder() {");
    ilb.increaseIndent();
    ilb.appendLine("return new Builder();");
    ilb.decreaseIndent();
    ilb.appendLine("}");
    ilb.appendLine();

    // Will contain handled and unhandled params. We include some types of unhandled params so that
    // they still end up in the generated list of params.
    List<ParamInfo> combinedParams =
        template.params().stream()
            .filter(
                info -> {
                  switch (info.status()) {
                    case HANDLED:
                    case UNHANDLED_TYPE:
                      return true;
                    case NAME_COLLISION:
                      logDuplicateParamNameWarning(
                          info.name(), info.setterName(), template.templateName());
                      return true;
                    case JAVA_INCOMPATIBLE:
                      break;
                    case INDIRECT_INCOMPATIBLE_TYPES:
                      logger.warning(
                          String.format(
                              "Parameter '%s' in %s has different types in different templates. No"
                                  + " parameter setter generated.",
                              info.name(), template.templateName()));
                      break;
                    case INDIRECT_PROTO:
                      logger.warning(
                          String.format(
                              "Indirect parameter '%s' in %s is of type proto or proto enum. No"
                                  + " parameter setter generated.",
                              info.name(), template.templateName()));
                      break;
                  }
                  return false;
                })
            .collect(toList());
    List<ParamInfo> nonInjectedParams =
        combinedParams.stream().filter(p -> !p.injected()).collect(toList());

    if (nonInjectedParams.stream().noneMatch(ParamInfo::requiredAndNotIndirect)) {
      // Invoke the constructor directly. For these templates it could allow callers to avoid
      // loading the builder completely.
      ilb.appendLine(
          "private static final "
              + templateParamsClassname
              + " "
              + DEFAULT_INSTANCE_FIELD
              + " = new "
              + templateParamsClassname
              + "(com.google.common.collect.ImmutableMap.of());");
      ilb.appendLine();

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
      ilb.appendLine("return " + DEFAULT_INSTANCE_FIELD + ";");
      ilb.decreaseIndent();
      ilb.appendLine("}");
      ilb.appendLine();
    }
    appendParamConstants(ilb, combinedParams);

    boolean anyAccumulatorParameters =
        nonInjectedParams.stream()
            .flatMap(param -> param.javaTypes().stream())
            .anyMatch(
                javaType ->
                    javaType instanceof RecordJavaType && ((RecordJavaType) javaType).isList());
    // Start of Foo.Builder class.
    ilb.appendLine("@com.google.errorprone.annotations.CanIgnoreReturnValue");
    ilb.appendLine(
        "public static final class Builder extends"
            + " com.google.template.soy.data.BaseSoyTemplateImpl."
            + (anyAccumulatorParameters
                ? "AbstractBuilderWithAccumulatorParameters"
                : "AbstractBuilder")
            + "<Builder, "
            + templateParamsClassname
            + "> {");
    ilb.appendLine();
    ilb.increaseIndent();

    // Constructor for Foo.Builder.
    ilb.appendLine("private Builder() {");
    ilb.increaseIndent();
    ilb.appendLine("super(", nonInjectedParams.size(), ");");
    appendRecordListInitializations(ilb, nonInjectedParams);
    ilb.decreaseIndent();
    ilb.appendLine("}");
    ilb.appendLine();

    // #allParams() for FooTemplate.Builder.
    ilb.appendLine("@java.lang.Override");
    ilb.appendLine(
        "protected"
            + " com.google.common.collect.ImmutableSet<com.google.template.soy.data.SoyTemplateParam<?>>"
            + " allParams() {");
    ilb.increaseIndent();
    ilb.appendLine("return " + PARAMS_FIELD + ";");
    ilb.decreaseIndent();
    ilb.appendLine("}");
    ilb.appendLine();

    // #buildInternal() for FooTemplate.Builder.
    ilb.appendLine("@java.lang.Override");
    ilb.appendLine(
        "protected "
            + templateParamsClassname
            + " buildInternal(com.google.common.collect.ImmutableMap<java.lang.String,"
            + " com.google.template.soy.data.SoyValueProvider> data) {");
    ilb.increaseIndent();
    ilb.appendLine("return new " + templateParamsClassname + "(data);");
    ilb.decreaseIndent();
    ilb.appendLine("}");

    // Add setters for each direct template param.
    nonInjectedParams.stream()
        .filter(p -> p.status() == ParamStatus.HANDLED)
        .forEach(this::writeSettersForParam);

    ilb.appendLine();

    // End of FooTemplateInvocation.Builder class.
    ilb.decreaseIndent();
    ilb.appendLine("}");
  }

  private static void appendParamConstants(IndentedLinesBuilder ilb, List<ParamInfo> params) {
    Set<String> usedNames = new HashSet<>();
    List<String> nonInjected = new ArrayList<>();
    for (ParamInfo param : params) {
      while (usedNames.contains(param.constantFieldName())) {
        param.updateConstantFieldName();
      }
      String fieldName = param.constantFieldName();
      usedNames.add(fieldName);
      if (!param.injected()) {
        nonInjected.add(fieldName);
      }

      String genericType = "?";
      List<JavaType> types = param.javaTypes();
      if (types.size() == 1) {
        JavaType javaType = types.get(0);
        // this is basically 'instanceof RecordJavaType' at this point
        if (javaType.isTypeLiteralSupported()) {
          genericType = javaType.asTypeLiteralString();
        }
      }

      // Make any param that supports type literal public so it can be used with
      // TemplateParamModule, SoyTemplateData, AbstractBuilder, and tests. Union types, records, and
      // CSS params will be private since they can't be represented as a single specific type
      // literal.
      String visibility = !"?".equals(genericType) ? "public" : "private";

      // These values correspond to static factory methods on SoyTemplateParam.
      CodeGenUtils.Member factory = STANDARD_P;
      if (param.injected()) {
        factory = INJECTED_P;
      } else if (param.indirect()) {
        factory = INDIRECT_P;
      }

      String paramDescription = param.param().getDescription();
      if (paramDescription == null) {
        paramDescription = "";
      } else {
        paramDescription += " ";
      }

      String typeToken =
          "?".equals(genericType)
              // TODO(user): this should probably be a wildcard type
              ? "com.google.common.reflect.TypeToken.of(java.lang.Object.class)"
              : (genericType.matches("(\\.|\\w)+")
                  ? "com.google.common.reflect.TypeToken.of(" + genericType + ".class" + ")"
                  : "new com.google.common.reflect.TypeToken<" + genericType + ">() {}");
      ilb.appendLine(
          String.format(
              "/** {@%s %s} %s*/",
              param.injected() ? "inject" : "param", param.name(), paramDescription));
      ilb.appendLine(
          String.format(
              "%s static final com.google.template.soy.data.SoyTemplateParam<%s>",
              visibility, genericType));
      ilb.increaseIndent(2);
      ilb.appendLine(fieldName, " =");
      ilb.increaseIndent(2);
      ilb.appendLine(factory, "(");
      ilb.increaseIndent(2);
      ilb.appendLine("\"", param.name(), "\",");
      ilb.appendLine("/* required= */ ", param.required(), ",");
      ilb.appendLine(typeToken, ");");
      ilb.decreaseIndent(6);
      ilb.appendLine();
    }

    ilb.appendLineStart(
        "private static final"
            + " com.google.common.collect.ImmutableSet<com.google.template.soy.data.SoyTemplateParam<?>>"
            + " "
            + PARAMS_FIELD
            + " = ");
    // Omit injected params from the list of params passed to the builder.
    appendFunctionCallWithParamsOnNewLines(
        ilb, "com.google.common.collect.ImmutableSet.of", nonInjected);
    ilb.appendLineEnd(";");
    ilb.appendLine();
  }

  private static void appendRecordListInitializations(
      IndentedLinesBuilder ilb, List<ParamInfo> params) {
    // For every required param that's of type list<[...]> (list of records), initialize the list
    // so that upon building the template we do not throw an error for zero records.
    for (ParamInfo param : params) {
      if (param.required()) {
        List<JavaType> types = param.javaTypes();
        if (types.size() == 1
            && types.get(0) instanceof RecordJavaType
            && ((RecordJavaType) types.get(0)).isList()) {
          ilb.appendLine(String.format("%s(%s);", INIT_LIST_PARAM, param.constantFieldName()));
        }
      }
    }
  }

  /** Appends the file header and imports for the generated *FooTemplates.java */
  private void appendFileHeaderAndImports(FileInfo soyFile) {
    // Header.
    ilb.appendLine("// This file was automatically generated by the Soy compiler.");
    ilb.appendLine("// Please don't edit this file by hand.");
    ilb.appendLine("// source: " + soyFile.soyFilePath()); // For Code Search link.
    ilb.appendLine();
    ilb.appendLine("package " + soyFile.packageName() + ";");
    ilb.appendLine();
    ilb.appendLine();

    // No Imports!
    // It is annoying and verbose but by fully qualifying all type names we can avoid conflicts
    // with user defined symbols
  }

  /**
   * Writes setter methods each of the java types that this param can be (e.g union int | string
   * would generate setFoo(int) and setFoo(string)).
   *
   * <p>TODO(b/77550695): Update docs for how we handle futures.
   */
  private void writeSettersForParam(ParamInfo param) {
    // Add setters for this param.
    param.javaTypes().forEach(javaType -> writeSetter(ilb, param, javaType));

    // For now only write the future interface if the setter is not already overloaded
    switch (param.futureStatus()) {
      case HANDLED:
        for (JavaType futureType : param.futureTypes()) {
          writeFutureSetter(ilb, param, new FutureJavaType(futureType));
        }
        break;
      case NAME_COLLISION:
        logger.warning(
            String.format(
                "Achievement unlocked. You have a template with parameters named %s and"
                    + " %sFuture, preventing a future setter from being created for the first"
                    + " parameter.",
                param.name(), param.name()));
        break;
      case UNHANDLED:
        break;
    }
  }

  /** Writes a setter method for the given param and java type. */
  private static void writeSetter(IndentedLinesBuilder ilb, ParamInfo param, JavaType javaType) {
    String paramDescription = param.param().getDescription();
    ilb.appendLine();
    appendJavadoc(
        ilb,
        "Sets "
            + param.name()
            + (Strings.isNullOrEmpty(paramDescription) ? "." : ": " + paramDescription),
        /* forceMultiline= */ false,
        /* wrapAt100Chars= */ true);

    if (javaType instanceof RecordJavaType) {
      writeRecordSetter(ilb, param, (RecordJavaType) javaType);
    } else {
      String javaTypeString = javaType.toJavaTypeString();
      boolean nullable = javaType.isNullable();

      ilb.appendLine(
          "public Builder "
              + param.setterName()
              + "("
              + (nullable ? "@javax.annotation.Nullable " : "")
              + javaTypeString
              + " value) {");
      ilb.increaseIndent();

      String newVariableName = javaType.asInlineCast("value");
      ilb.appendLine(
          "return " + SET_PARAM_INTERNAL + "(",
          param.constantFieldName(),
          ", ",
          newVariableName,
          ");");
      ilb.decreaseIndent();
      ilb.appendLine("}");
    }
  }

  private static void writeRecordSetter(
      IndentedLinesBuilder ilb, ParamInfo param, RecordJavaType type) {
    ilb.appendLineStart(
        "public Builder ", type.isList() ? param.adderName() : param.setterName(), "(");

    List<String> paramNames = type.getJavaTypeMap().keySet().asList();
    List<String> javaParamNames = new ArrayList<>();

    boolean first = true;
    for (Map.Entry<String, JavaType> entry : type.getJavaTypeMap().entrySet()) {
      String paramName = makeParamName(entry.getKey());
      javaParamNames.add(paramName);

      if (!first) {
        ilb.append(", ");
      }
      JavaType paramType = entry.getValue();
      if (paramType.isNullable()) {
        ilb.append("@javax.annotation.Nullable ");
      }
      ilb.append(paramType.toJavaTypeString()).append(" ").append(paramName);
      first = false;
    }
    ilb.appendLineEnd(") {");
    ilb.increaseIndent();

    CodeGenUtils.Member delegate = type.isList() ? ADD_TO_LIST_PARAM : SET_PARAM_INTERNAL;

    ilb.appendLineStart(
        "return ", delegate, "(", param.constantFieldName(), ", " + AS_RECORD + "(");
    int numParams = paramNames.size();
    for (int i = 0; i < numParams; i++) {
      if (i != 0) {
        ilb.append(", ");
      }
      ilb.append("\"")
          .append(paramNames.get(i))
          .append("\", ")
          .append(type.getJavaTypeMap().get(paramNames.get(i)).asInlineCast(javaParamNames.get(i)));
    }
    ilb.appendLineEnd("));");
    ilb.decreaseIndent();
    ilb.appendLine("}");
  }

  /** Writes a setter method for the given param and java type. */
  private static void writeFutureSetter(
      IndentedLinesBuilder ilb, ParamInfo param, FutureJavaType javaType) {

    ilb.appendLine();
    appendJavadoc(
        ilb,
        "Future compatible version of {@link #"
            + param.setterName()
            + "("
            + stripGenerics(javaType.getType().toJavaTypeString())
            + ")}.",
        /* forceMultiline= */ false,
        /* wrapAt100Chars= */ true);
    ilb.appendLine(
        "public Builder "
            + param.futureSetterName()
            + "("
            + javaType.toJavaTypeString()
            + " future) {");
    ilb.increaseIndent();

    ilb.appendLine(
        "return "
            + SET_PARAM_INTERNAL
            + "("
            + param.constantFieldName()
            + ", "
            + javaType.asInlineCast("future")
            + ");");
    ilb.decreaseIndent();
    ilb.appendLine("}");
  }

  private static String stripGenerics(String type) {
    String newType = type;
    do {
      type = newType;
      newType = type.replaceAll("<[^>]*>", "");
    } while (!newType.equals(type));
    return newType;
  }

  /**
   * Logs a warning if two templates in the same soy file mapped to the same UpperCamelCase java
   * class name.
   */
  private static void logDuplicateTemplateNameWarning(
      String templateName, String generatedClassName) {
    logger.warning(
        String.format(
            "When generating Soy Java Template Builders, the template: %s generated the same Java"
                + " UpperCamelCase name as another template in this file, or collided with a"
                + " reserved identifier: "
                + SoyFileNodeTransformer.RESERVED_IDENTIFIERS
                + ".\n"
                + "This template was skipped during Soy java_builders generation.\n"
                + "To use this API, all Soy template names in a given file should be unique when "
                + "converted to UpperCamelCase (with non-alphanumeric characters stripped).\n"
                + "The generated Java class name was: %s.",
            templateName,
            generatedClassName));
  }

  /**
   * Logs a warning if two params generate the same upper camel case name (which means we need to
   * skip over the param and not generate setters for it.
   */
  private static void logDuplicateParamNameWarning(
      String templateParamName, String setterName, String templateName) {
    logger.warning(
        String.format(
            "When generating Soy Java Template Builders, the param named %s in template %s"
                + " generated the same UpperCamelCase name as another parameter, or collided with"
                + " a reserved identifier: "
                + SoyFileNodeTransformer.RESERVED_IDENTIFIERS
                + ".\n"
                + "Param: %s is being skipped (no setters will be generated for this param). The "
                + "generated setter name was: %s.\n"
                + "To use this API, all parameter names for a given template should be unique "
                + "when converted to UpperCamelCase (with non-alphanumeric characters stripped).\n",
            templateParamName,
            templateName,
            templateParamName,
            setterName));
  }

  /** Logs a warning if two soy files mapped to the same generated java file name. */
  private static void logWarningIfFilenamesNotUnique(ImmutableList<GeneratedFile> files) {
    ImmutableList<String> duplicateFilenames =
        files.stream().collect(groupingBy(GeneratedFile::fileName, counting())).entrySet().stream()
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

  private static final ImmutableSet<String> RESERVED_JAVA_WORDS =
      ImmutableSet.of(
          "abstract",
          "assert",
          "boolean",
          "byte",
          "case",
          "catch",
          "char",
          "class",
          "const",
          "continue",
          "default",
          "do",
          "double",
          "else",
          "extends",
          "false",
          "final",
          "finally",
          "float",
          "for",
          "goto",
          "if",
          "implements",
          "import",
          "instanceof",
          "int",
          "interface",
          "long",
          "native",
          "new",
          "null",
          "package",
          "private",
          "protected",
          "public",
          "return",
          "short",
          "static",
          "strictfp",
          "super",
          "switch",
          "synchronized",
          "this",
          "throw",
          "throws",
          "transient",
          "true",
          "try",
          "void",
          "volatile",
          "while");

  private static String makeParamName(String s) {
    s = makeLowerCamelCase(s);
    return RESERVED_JAVA_WORDS.contains(s) ? s + "_" : s;
  }
}
