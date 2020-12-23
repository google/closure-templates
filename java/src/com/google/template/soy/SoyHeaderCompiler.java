/*
 * Copyright 2018 Google Inc.
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

package com.google.template.soy;

import static com.google.common.base.Strings.nullToEmpty;
import static java.util.stream.Collectors.toList;

import com.google.common.collect.Iterables;
import com.google.template.soy.css.CssMetadata;
import com.google.template.soy.css.CssRegistry;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.StringNode;
import com.google.template.soy.shared.internal.BuiltinFunction;
import com.google.template.soy.soytree.CallBasicNode;
import com.google.template.soy.soytree.CallDelegateNode;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.CompilationUnit;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.templatecall.TemplateCallMetadata;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.zip.GZIPOutputStream;
import org.kohsuke.args4j.Option;

/**
 * Executable for compiling a set of Soy files into CompilationUnit proto.
 *
 * <p>A compilation unit represents all the extracted TemplateMetadata instances for a single
 * library. This serves as an intermediate build artifact that later compilations can use in
 * preference to parsing dependencies. This improves overall compiler performance by making builds
 * more cacheable.
 */
final class SoyHeaderCompiler extends AbstractSoyCompiler {
  private static final int OUTPUT_STREAM_BUFFER_SIZE = 64 * 1024;

  @Option(
      name = "--output",
      required = true,
      usage =
          "[Required] The file name of the output file to be written.  Each compiler"
              + " invocation will produce exactly one file containing all the TemplateMetadata")
  private File output;

  @Option(
      name = "--cssMetadataOutput",
      usage =
          "Where to write metadata about CSS.  This will be a file containing a gzipped"
              + " CssMetadata proto")
  private File cssMetadataOutput = null;

  @Option(
      name = "--templateCallMetadataOutput",
      usage =
          "Where to write metadata about the template calls.  This will be a file containing"
              + " a gzipped TemplateCallMetadata proto")
  private File templateCallMetadataOutput = null;

  SoyHeaderCompiler(PluginLoader loader, SoyInputCache cache) {
    super(loader, cache);
  }

  SoyHeaderCompiler() {}

  @Override
  protected void compile(SoyFileSet.Builder sfsBuilder) throws IOException {
    SoyFileSet.HeaderResult result = sfsBuilder.build().compileMinimallyForHeaders();
    CompilationUnit unit =
        TemplateMetadataSerializer.compilationUnitFromFileSet(
            result.fileSet(), result.templateRegistry());
    // some small tests revealed about a 5x compression ratio.  This is likely due to template names
    // sharing common prefixes and repeated parameter names and types.
    try (OutputStream os =
        new GZIPOutputStream(new FileOutputStream(output), OUTPUT_STREAM_BUFFER_SIZE)) {
      unit.writeTo(os);
    }
    if (cssMetadataOutput != null) {
      try (OutputStream os =
          new GZIPOutputStream(
              new FileOutputStream(cssMetadataOutput), OUTPUT_STREAM_BUFFER_SIZE)) {
        calculateCssMetadata(result.fileSet(), result.cssRegistry()).writeTo(os);
      }
    }
    if (templateCallMetadataOutput != null) {
      try (OutputStream os =
          new GZIPOutputStream(
              new FileOutputStream(templateCallMetadataOutput), OUTPUT_STREAM_BUFFER_SIZE)) {
        calculateTemplateCallMetadata(result.fileSet()).writeTo(os);
      }
    }
  }

  private static CssMetadata calculateCssMetadata(SoyFileSetNode fileSet, CssRegistry cssRegistry) {
    // We need to remove duplicates and preserve order, so collect into maps first
    Set<String> requiredCssNames = new LinkedHashSet<>();
    Set<String> requiredCssPaths = new LinkedHashSet<>();
    for (SoyFileNode file : fileSet.getChildren()) {
      requiredCssNames.addAll(file.getRequiredCssNamespaces());
      for (SoyFileNode.CssPath cssPath : file.getRequiredCssPaths()) {
        // This should alwayus be present due to the ValidateRequiredCssPass, but that pass isn't
        // run in the open source release.
        cssPath.resolvedPath().ifPresent(requiredCssPaths::add);
      }
      for (TemplateNode template : file.getTemplates()) {
        requiredCssNames.addAll(template.getRequiredCssNamespaces());
      }
    }
    Set<String> cssClassNames = new LinkedHashSet<>();
    for (FunctionNode fn : SoyTreeUtils.getAllFunctionInvocations(fileSet, BuiltinFunction.CSS)) {
      cssClassNames.add(((StringNode) Iterables.getLast(fn.getChildren())).getValue());
    }
    return CssMetadata.newBuilder()
        .addAllRequireCssNames(requiredCssNames)
        .addAllRequireCssPaths(requiredCssPaths)
        .addAllRequireCssPathsFromNamespaces(
            requiredCssNames.stream()
                .map(namespace -> cssRegistry.symbolToFilePath().get(namespace))
                // This shouldn't really happen due to the ValidateRequiredCssPass but that pass
                // doesn't run in the open source build
                .filter(f -> f != null)
                .collect(toList()))
        .addAllCssClassNames(cssClassNames)
        .build();
  }

  private static TemplateCallMetadata calculateTemplateCallMetadata(SoyFileSetNode fileSet) {

    TemplateCallMetadata.Builder templateCallMetadata = TemplateCallMetadata.newBuilder();

    for (SoyFileNode file : fileSet.getChildren()) {
      for (TemplateNode template : file.getTemplates()) {
        TemplateCallMetadata.Template.Builder templateMetadata =
            TemplateCallMetadata.Template.newBuilder()
                .setName(template.getTemplateName())
                .setDelpackage(nullToEmpty(template.getDelPackageName()));

        // TODO(b/172278368): incorporate Soy Element Composition (see

        for (CallNode callNode : SoyTreeUtils.getAllNodesOfType(template, CallNode.class)) {
          templateMetadata.addCalls(
              TemplateCallMetadata.TemplateCall.newBuilder()
                  .setDestTemplateName(getDestTemplateName(callNode))
                  .setIsDelcall(callNode.getKind() == SoyNode.Kind.CALL_DELEGATE_NODE)
                  .setDataArg(getDataArgStr(callNode)));
        }
        templateCallMetadata.addTemplates(templateMetadata.build());
      }
    }
    return templateCallMetadata.build();
  }

  private static String getDestTemplateName(CallNode callNode) {
    switch (callNode.getKind()) {
      case CALL_BASIC_NODE:
        CallBasicNode basicNode = ((CallBasicNode) callNode);
        return basicNode.isStaticCall()
            ? basicNode.getCalleeName()
            : basicNode.getCalleeExpr().toSourceString();
      case CALL_DELEGATE_NODE:
        return ((CallDelegateNode) callNode).getDelCalleeName();
      default:
        throw new IllegalStateException("Unknown CallNode kind");
    }
  }

  private static String getDataArgStr(CallNode callNode) {
    return callNode.getDataExpr() != null
        ? callNode.getDataExpr().toSourceString()
        : callNode.isPassingAllData() ? "all" : "";
  }

  /**
   * Compiles a set of Soy files into corresponding header files, which are usable as intermediates
   * for future Soy compile routines.
   *
   * @param args Should contain command-line flags and the list of paths to the Soy files.
   * @throws IOException If there are problems reading the input files or writing the output file.
   */
  public static void main(final String[] args) throws IOException {
    new SoyHeaderCompiler().runMain(args);
  }
}
