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

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.internal.IndentedLinesBuilder;
import com.google.template.soy.shared.internal.gencode.GeneratedFile;
import com.google.template.soy.shared.internal.gencode.JavaGenerationUtils;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.Visibility;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Visitor for generating Java template parameter builders (see {@link BaseParamsImpl}) that can be
 * used for invoking Soy templates from Java. One java file will be generated for each soy file,
 * containg template param builders for each template in the soy file.
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
    JavaGenerationUtils.appendJavadoc(
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
   * Writes a FooParams subclass for the given template. The class extends {@link BaseParamsImpl},
   * which implements {@link TemplateParams}.
   */
  @Override
  protected void visitTemplateNode(TemplateNode template) {
    Optional<String> templateParamsClassname = generateBaseParamsImplClassName(template);

    // If no java class name was generated for this template, skip over this template.
    if (!templateParamsClassname.isPresent()) {
      return;
    }

    // Start of FooParams class.
    String templateDescription = template.getSoyDocDesc();
    JavaGenerationUtils.appendJavadoc(
        ilb,
        "Template params for "
            + template.getTemplateNameForUserMsgs()
            + (templateDescription != null ? ": " + templateDescription : "."),
        /* forceMultiline= */ false,
        /* wrapAt100Chars= */ true);
    ilb.appendLine(
        "public final class " + templateParamsClassname.get() + " extends BaseParamsImpl {");
    ilb.appendLine();

    // Constructor for FooParams.
    ilb.increaseIndent();
    ilb.appendLine(
        "public "
            + templateParamsClassname.get()
            + "(String templateName, Map<String, SoyValueProvider> data) {");
    ilb.increaseIndent();
    ilb.appendLine("super(templateName, data);");
    ilb.decreaseIndent();
    ilb.appendLine("}");

    // End of FooParams class.
    ilb.decreaseIndent();
    ilb.appendLine("}");
    ilb.appendLine();
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
    ilb.appendLine("import com.google.template.soy.data.SoyValueProvider;");
    ilb.appendLine("import com.google.template.soy.data.BaseParamsImpl;");
    ilb.appendLine("import javax.annotation.Generated;");
    ilb.appendLine("import java.util.Map;");
    ilb.appendLine();
    ilb.appendLine();
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
    return JavaGenerationUtils.makeUpperCamelCase(fileName) + "Templates";
  }

  /**
   * Converts a template name to its corresponding FooParams class name.
   *
   * <p>NOTE: If the java class name has already been used, this returns an empty optional. See
   * {@link paramsClassNamesUsed} for more info about when this happens.
   */
  private Optional<String> generateBaseParamsImplClassName(TemplateNode template) {
    String namespacedTemplateName = template.getTemplateName();
    String templateName =
        namespacedTemplateName.substring(namespacedTemplateName.lastIndexOf('.') + 1);

    // Convert the template name to upper camel case (stripping non-alphanumeric characters), and
    // append "Params" (e.g. template "foo" -> "FooParams").
    String className = JavaGenerationUtils.makeUpperCamelCase(templateName) + "Params";

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
