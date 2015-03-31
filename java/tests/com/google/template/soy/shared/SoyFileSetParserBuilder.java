/*
 * Copyright 2015 Google Inc.
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

package com.google.template.soy.shared;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.template.soy.base.internal.SoyFileKind;
import com.google.template.soy.base.internal.SoyFileSupplier;
import com.google.template.soy.basetree.SyntaxVersion;
import com.google.template.soy.soyparse.ParseResult;
import com.google.template.soy.soyparse.SoyFileSetParser;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.types.SoyTypeRegistry;

import java.util.Arrays;
import java.util.List;

import javax.annotation.Nullable;

/**
 * Fluent builder for configuring {@link SoyFileSetParser}s in tests.
 *
 * @author brndn@google.com (Brendan Linn)
 */
public final class SoyFileSetParserBuilder {

  private final ImmutableList<SoyFileSupplier> soyFileSuppliers;
  private boolean doRunCheckingPasses = false;
  private boolean doRunInitialParsingPasses = true; // Non-standard default
  private SoyTypeRegistry typeRegistry = new SoyTypeRegistry();
  private SyntaxVersion declaredSyntaxVersion = SyntaxVersion.V2_0;
  @Nullable private SoyAstCache astCache = null;

  /**
   * Returns a builder that gets its Soy inputs from the given {@code fileContents}.
   */
  public static SoyFileSetParserBuilder forFileContents(String... fileContents) {
    return new SoyFileSetParserBuilder(fileContents);
  }

  /**
   * Returns a builder that gets its Soy inputs from the given {@link SoyFileSupplier}s.
   */
  public static SoyFileSetParserBuilder forSuppliers(SoyFileSupplier... suppliers) {
    return new SoyFileSetParserBuilder(ImmutableList.copyOf(Arrays.asList(suppliers)));
  }

  /**
   * Returns a builder that gets its Soy inputs from the given {@code templateContents}.
   */
  public static SoyFileSetParserBuilder forTemplateContents(String... templateContents) {
    return forTemplateContents(AutoEscapingType.DEPRECATED_NONCONTEXTUAL, templateContents);
  }

  /**
   * Returns a builder that gets its Soy inputs from the given {@code templateContents},
   * using the given {@link AutoEscapingType}.
   */
  public static SoyFileSetParserBuilder forTemplateContents(
      AutoEscapingType autoEscapingType, String... templateContents) {
    String[] fileContents = new String[templateContents.length];
    for (int i = 0; i < fileContents.length; ++i) {
      fileContents[i] = SharedTestUtils.buildTestSoyFileContent(
          autoEscapingType, null /* soyDocParamNames */, templateContents[i]);
    }
    return new SoyFileSetParserBuilder(fileContents);
  }

  private SoyFileSetParserBuilder(String... soyCode) {
    this.soyFileSuppliers = ImmutableList.copyOf(buildTestSoyFileSuppliers(soyCode));
  }

  private SoyFileSetParserBuilder(ImmutableList<SoyFileSupplier> suppliers) {
    this.soyFileSuppliers = suppliers;
  }

  /**
   * Sets the parser's declared syntax version. Returns this object, for chaining.
   */
  public SoyFileSetParserBuilder declaredSyntaxVersion(SyntaxVersion version) {
    this.declaredSyntaxVersion = version;
    return this;
  }

  /**
   * Turns the parser's checking passes on or off. Returns this object, for chaining.
   *
   * <p>The checking passes include:
   * <ul>
   *   <li>{@link com.google.template.soy.parsepasses.CheckCallsVisitor}</li>
   *   <li>{@link com.google.template.soy.parsepasses.CheckDelegatesVisitor}</li>
   *   <li>{@link com.google.template.soy.parsepasses.CheckOverridesVisitor}</li>
   *   <li>{@link com.google.template.soy.parsepasses.InferRequiredSyntaxVersionVisitor}</li>
   *   <li>{@link com.google.template.soy.parsepasses.VerifyPhnameAttrOnlyOnPlaceholdersVisitor}
   *   </li>
   *   <li>{@link com.google.template.soy.sharedpasses.CheckCallingParamTypesVisitor}</li>
   *   <li>{@link com.google.template.soy.sharedpasses.CheckSoyDocVisitor}</li>
   *   <li>{@link com.google.template.soy.sharedpasses.CheckTemplateVisibility}</li>
   *   <li>{@link com.google.template.soy.sharedpasses.ReportSyntaxVersionErrorsVisitor}</li>
   * </ul>
   */
  public SoyFileSetParserBuilder doRunCheckingPasses(boolean doRunCheckingPasses) {
    this.doRunCheckingPasses = doRunCheckingPasses;
    return this;
  }

  /**
   * Turns the parser's initial parsing passes on or off. Returns this object, for chaining.
   *
   * <p>The initial parsing passes include:
   * <ul>
   *   <li>{@link com.google.template.soy.parsepasses.RewriteGenderMsgsVisitor}</li>
   *   <li>{@link com.google.template.soy.parsepasses.ReplaceHasDataFunctionVisitor}</li>
   *   <li>{@link com.google.template.soy.parsepasses.RewriteRemainderNodesVisitor}</li>
   *   <li>{@link com.google.template.soy.parsepasses.RewriteNullCoalescingOpVisitor}</li>
   *   <li>{@link com.google.template.soy.parsepasses.SetDefaultForDelcallAllowsEmptyDefaultVisitor}
   *   <li>{@link com.google.template.soy.parsepasses.SetFullCalleeNamesVisitor}</li>
   *   </li>
   *   <li>{@link com.google.template.soy.sharedpasses.RemoveHtmlCommentsVisitor}</li>
   *   <li>{@link com.google.template.soy.sharedpasses.ResolveExpressionTypesVisitor}</li>
   *   <li>{@link com.google.template.soy.sharedpasses.ResolveNamesVisitor}</li>
   * </ul>
   */
  public SoyFileSetParserBuilder doRunInitialParsingPasses(boolean doRunInitialParsingPasses) {
    this.doRunInitialParsingPasses = doRunInitialParsingPasses;
    return this;
  }

  /**
   * Sets the parser's type registry. Returns this object, for chaining.
   */
  public SoyFileSetParserBuilder typeRegistry(SoyTypeRegistry typeRegistry) {
    this.typeRegistry = typeRegistry;
    return this;
  }

  private static List<SoyFileSupplier> buildTestSoyFileSuppliers(String... soyFileContents) {

    List<SoyFileSupplier> soyFileSuppliers = Lists.newArrayList();
    for (int i = 0; i < soyFileContents.length; i++) {
      String soyFileContent = soyFileContents[i];
      // Names are now required to be unique in a SoyFileSet. Use one-based indexing.
      String filePath = (i == 0) ? "no-path" : ("no-path-" + (i + 1));
      soyFileSuppliers.add(
          SoyFileSupplier.Factory.create(soyFileContent, SoyFileKind.SRC, filePath));
    }
    return soyFileSuppliers;
  }

  public ParseResult<SoyFileSetNode> parse() {
    return new SoyFileSetParser(
        typeRegistry,
        astCache,
        declaredSyntaxVersion,
        soyFileSuppliers)
        .setDoRunInitialParsingPasses(doRunInitialParsingPasses)
        .setDoRunCheckingPasses(doRunCheckingPasses)
        .parse();
  }
}
