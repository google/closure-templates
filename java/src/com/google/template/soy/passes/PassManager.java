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

package com.google.template.soy.passes;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.CharSource;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.base.internal.TriState;
import com.google.template.soy.basetree.SyntaxVersion;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.ErrorReporter.Checkpoint;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.shared.SoyGeneralOptions;
import com.google.template.soy.shared.restricted.SoyFunction;
import com.google.template.soy.soytree.AliasDeclaration;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TemplateRegistry;
import com.google.template.soy.types.SoyTypeRegistry;

/**
 * Configures all compiler passes.
 *
 * <p>This arranges all compiler passes into two phases.
 *
 * <ul>
 *   <li>The single file passes. This includes AST rewriting passes such as {@link HtmlRewritePass}
 *       and {@link RewriteGendersPass} and other kinds of validation that doesn't require
 *       information about the full file set.
 *   <li>The file set passes. This includes AST validation passes like the {@link
 *       CheckVisibilityPass}. Passes should run here if they need to check the relationships
 *       between templates.
 * </ul>
 *
 * <p>The reason things have been divided in this way is partially to create consistency and also to
 * enable other compiler features. For example, for in process (server side) compilation we can
 * cache the results of the single file passes to speed up edit-refresh flows. Also, theoretically,
 * we could run the single file passes for each file in parallel.
 *
 * <p>There are 2 parts of the compiler that are not currently managed by this class. The contextual
 * autoescaper and the optimizer. These are currently excluded because they don't fit into the model
 * above. Notably, the autoescaper can actually create new templates and as such it invalidates the
 * type registry, and the optimizer needs to run after the autoescaper in order to optimize the
 * newly created templates.
 *
 * <p>A note on ordering. There is no real structure to the ordering of the passes beyond what is
 * documented in comments. Many passes do rely on running before/after a different pass (e.g. {@link
 * CheckFunctionCallsPass} needs to run after {@link ResolveExpressionTypesVisitor} which needs to
 * run after {@link ResolveNamesPass}), but there isn't any dependency system in place.
 */
public final class PassManager {
  private final ImmutableList<CompilerFilePass> singleFilePasses;
  private final ImmutableList<CompilerFileSetPass> fileSetPasses;
  private final SoyTypeRegistry registry;
  private final ImmutableMap<String, ? extends SoyFunction> soyFunctionMap;
  private final ErrorReporter errorReporter;
  private final SyntaxVersion declaredSyntaxVersion;
  private final SoyGeneralOptions options;
  private final boolean allowUnknownGlobals;
  private final boolean disableAllTypeChecking;

  private PassManager(Builder builder) {
    this.registry = checkNotNull(builder.registry);
    this.soyFunctionMap = checkNotNull(builder.soyFunctionMap);
    this.errorReporter = checkNotNull(builder.errorReporter);
    this.declaredSyntaxVersion = checkNotNull(builder.declaredSyntaxVersion);
    this.options = checkNotNull(builder.opts);
    this.allowUnknownGlobals = builder.allowUnknownGlobals;
    this.disableAllTypeChecking = builder.disableAllTypeChecking;

    boolean enabledStrictHtml = options.getExperimentalFeatures().contains("stricthtml");
    HtmlRewritePass rewritePass =
        new HtmlRewritePass(
            options.getExperimentalFeatures(),
            errorReporter);

    ImmutableList.Builder<CompilerFilePass> singleFilePassesBuilder =
        ImmutableList.<CompilerFilePass>builder()
            .add(new RewriteGendersPass())
            .add(new RewriteRemaindersPass())
            .add(rewritePass)
            // needs to run after htmlrewriting, before resolvenames and autoescaping
            .add(
                new ContentSecurityPolicyNonceInjectionPass(
                    options.getExperimentalFeatures(), errorReporter))
            .add(new StrictHtmlValidationPass(options.getExperimentalFeatures(), errorReporter))
            .add(new RewriteGlobalsPass(registry, options.getCompileTimeGlobals(), errorReporter))
            .add(new RewriteFunctionsPass(registry))
            .add(new SetFullCalleeNamesPass())
            .add(new ResolveNamesPass())
            .add(new ResolveFunctionsPass());
    if (!disableAllTypeChecking) {
      singleFilePassesBuilder.add(new ResolveExpressionTypesPass());
    }
    singleFilePassesBuilder
        .add(new ResolvePackageRelativeCssNamesPass())
        .add(new VerifyPhnameAttrOnlyOnPlaceholdersPass());
    if (!allowUnknownGlobals) {
      // Must come after RewriteGlobalsPass since that is when values are substituted.
      // We should also run after the ResolveNamesPass which checks for global/param ambiguity and
      // may issue better error messages.
      singleFilePassesBuilder.add(new CheckGlobalsPass(errorReporter));
    }
    singleFilePassesBuilder
        .add(new ValidateAliasesPass())
        // could run anywhere
        .add(new CheckNonEmptyMsgNodesPass(errorReporter))
        .add(new CheckSyntaxVersionPass());
    if (!disableAllTypeChecking) {
      // Must run after ResolveExpressionTypesPass, which adds the SoyProtoType info.
      singleFilePassesBuilder
          .add(new CheckProtoInitCallsPass(errorReporter))
          // uses the syntax version to conditionally enable unknown functions for v1 templates
          // TODO(lukes): remove!
          .add(
              new CheckFunctionCallsPass(
                  builder.allowUnknownFunctions, declaredSyntaxVersion, errorReporter));
    }
    // If requiring strict autoescaping, check and enforce it.
    if (options.isStrictAutoescapingRequired() == TriState.ENABLED) {
      singleFilePassesBuilder.add(new EnforceStrictAutoescapingPass());
    }

    this.singleFilePasses = singleFilePassesBuilder.build();
    // Fileset passes run on the whole tree and should be reserved for checks that need transitive
    // call information (or full delegate sets).
    // Notably, the results of these passes cannot be cached in the AST cache.  So minimize their
    // use.
    ImmutableList.Builder<CompilerFileSetPass> fileSetPassBuilder =
        ImmutableList.<CompilerFileSetPass>builder().add(new CheckTemplateParamsPass());
    if (!disableAllTypeChecking) {
      fileSetPassBuilder.add(new CheckTemplateCallsPass(enabledStrictHtml, errorReporter));
    }
    fileSetPassBuilder
        .add(new CheckVisibilityPass())
        .add(new CheckDelegatesPass())
        // Could run ~anywhere, needs to be a fileset pass to validate deprecated-noncontextual
        // calls.  Make this a singlefile pass when deprecated-noncontextual is dead.
        .add(new CheckEscapingSanityFileSetPass(errorReporter));
    // If disallowing external calls, perform the check.
    if (options.allowExternalCalls() == TriState.DISABLED) {
      fileSetPassBuilder.add(new StrictDepsPass());
    }
    // TODO(lukes): move this to run after autoescaping.
    fileSetPassBuilder.add(new DesugarHtmlNodesPass());
    this.fileSetPasses = fileSetPassBuilder.build();
  }

  public SoyTypeRegistry getTypeRegistry() {
    return registry;
  }

  public void runSingleFilePasses(SoyFileNode file, IdGenerator nodeIdGen) {
    for (CompilerFilePass pass : singleFilePasses) {
      pass.run(file, nodeIdGen);
    }
  }

  // TODO(lukes): consider changing this to create the registry here and then return some tuple
  // object that contains the registry, the file set and ijparams info.  This would make it easier
  // to move ContextualAutoescaping into this file (alternatively, eliminate deprecated-contextual
  // autoescaping, which would make it so the autoescaper no longer modifies calls and adds
  // templates.
  public void runWholeFilesetPasses(TemplateRegistry registry, SoyFileSetNode soyTree) {
    for (CompilerFileSetPass pass : fileSetPasses) {
      pass.run(soyTree, registry);
    }
  }

  public static final class Builder {
    private SoyTypeRegistry registry;
    private ImmutableMap<String, ? extends SoyFunction> soyFunctionMap;
    private ErrorReporter errorReporter;
    private SyntaxVersion declaredSyntaxVersion;
    private SoyGeneralOptions opts;
    private boolean allowUnknownGlobals;
    private boolean allowUnknownFunctions;
    private boolean disableAllTypeChecking;
    private ImmutableList<CharSource> conformanceConfigs = ImmutableList.of();

    public Builder setErrorReporter(ErrorReporter errorReporter) {
      this.errorReporter = checkNotNull(errorReporter);
      return this;
    }

    public Builder setSoyFunctionMap(ImmutableMap<String, ? extends SoyFunction> functionMap) {
      this.soyFunctionMap = checkNotNull(functionMap);
      return this;
    }

    public Builder setTypeRegistry(SoyTypeRegistry registry) {
      this.registry = checkNotNull(registry);
      return this;
    }

    public Builder setDeclaredSyntaxVersion(SyntaxVersion declaredSyntaxVersion) {
      this.declaredSyntaxVersion = checkNotNull(declaredSyntaxVersion);
      return this;
    }

    public Builder setGeneralOptions(SoyGeneralOptions opts) {
      this.opts = opts;
      return this;
    }

    /**
     * Disables all the passes which enforce and rely on type information.
     *
     * <p>This should only be used for things like message extraction which doesn't tend to be
     * configured with a type registry.
     */
    public Builder disableAllTypeChecking() {
      this.disableAllTypeChecking = true;
      return this;
    }

    /**
     * Allows unknown global references.
     *
     * <p>This option is only available for backwards compatibility with legacy js only templates
     * and for parseinfo generation.
     */
    public Builder allowUnknownGlobals() {
      this.allowUnknownGlobals = true;
      return this;
    }

    /**
     * Allows unknown functions.
     *
     * <p>This option is only available for the parseinfo generator which historically has not had
     * proper build dependencies and thus often references unknown functions.
     */
    public Builder allowUnknownFunctions() {
      this.allowUnknownFunctions = true;
      return this;
    }

    public PassManager build() {
      return new PassManager(this);
    }

    /** Configures this passmanager to run the given conformance pass using these configs */
    public Builder setConformanceConfigs(ImmutableList<CharSource> conformanceConfigs) {
      this.conformanceConfigs = checkNotNull(conformanceConfigs);
      return this;
    }
  }

  private final class CheckSyntaxVersionPass extends CompilerFilePass {
    final ReportSyntaxVersionErrors reportDeclaredVersionErrors =
        new ReportSyntaxVersionErrors(declaredSyntaxVersion, true, errorReporter);

    @Override
    public void run(SoyFileNode file, IdGenerator nodeIdGen) {
      Checkpoint checkpoint = errorReporter.checkpoint();
      reportDeclaredVersionErrors.report(file);
      // If there were no errors against the declared syntax version, check for errors against
      // the inferred syntax version too. (If there were errors against the declared syntax version,
      // skip the inferred error checking, because it could produce duplicate errors and in any case
      // it's confusing for the user to have to deal with both declared and inferred errors.)
      if (!errorReporter.errorsSince(checkpoint)) {
        SyntaxVersion inferredSyntaxVersion = InferRequiredSyntaxVersion.infer(file);
        if (inferredSyntaxVersion.num > declaredSyntaxVersion.num) {
          new ReportSyntaxVersionErrors(inferredSyntaxVersion, false, errorReporter).report(file);
        }
      }
    }
  }

  private final class RewriteGendersPass extends CompilerFilePass {
    @Override
    public void run(SoyFileNode file, IdGenerator nodeIdGen) {
      new RewriteGenderMsgsVisitor(nodeIdGen, errorReporter).exec(file);
    }
  }

  private final class RewriteRemaindersPass extends CompilerFilePass {
    @Override
    public void run(SoyFileNode file, IdGenerator nodeIdGen) {
      new RewriteRemaindersVisitor(errorReporter).exec(file);
    }
  }

  private final class SetFullCalleeNamesPass extends CompilerFilePass {
    @Override
    public void run(SoyFileNode file, IdGenerator nodeIdGen) {
      new SetFullCalleeNamesVisitor(errorReporter).exec(file);
    }
  }

  private final class ResolveNamesPass extends CompilerFilePass {
    @Override
    public void run(SoyFileNode file, IdGenerator nodeIdGen) {
      new ResolveNamesVisitor(errorReporter).exec(file);
    }
  }

  private final class ResolveFunctionsPass extends CompilerFilePass {
    @Override
    public void run(SoyFileNode file, IdGenerator nodeIdGen) {
      SoyTreeUtils.execOnAllV2Exprs(file, new ResolveFunctionsVisitor(soyFunctionMap));
    }
  }

  private final class ResolveExpressionTypesPass extends CompilerFilePass {
    @Override
    public void run(SoyFileNode file, IdGenerator nodeIdGen) {
      // Needs the syntax version to decide
      // 1. whether the type of boolean operators is bool
      // 2. whether to allow printing bools
      new ResolveExpressionTypesVisitor(registry, declaredSyntaxVersion, errorReporter).exec(file);
    }
  }

  private final class ResolvePackageRelativeCssNamesPass extends CompilerFilePass {
    @Override
    public void run(SoyFileNode file, IdGenerator nodeIdGen) {
      new ResolvePackageRelativeCssNamesVisitor(errorReporter).exec(file);
    }
  }

  private final class VerifyPhnameAttrOnlyOnPlaceholdersPass extends CompilerFilePass {
    @Override
    public void run(SoyFileNode file, IdGenerator nodeIdGen) {
      new VerifyPhnameAttrOnlyOnPlaceholdersVisitor(errorReporter).exec(file);
    }
  }

  private final class EnforceStrictAutoescapingPass extends CompilerFilePass {
    final AssertStrictAutoescapingVisitor visitor =
        new AssertStrictAutoescapingVisitor(errorReporter);

    @Override
    public void run(SoyFileNode file, IdGenerator nodeIdGen) {
      visitor.exec(file);
    }
  }

  private final class CheckTemplateParamsPass extends CompilerFileSetPass {
    @Override
    public void run(SoyFileSetNode fileSet, TemplateRegistry registry) {
      // Syntax version is used to decide whether or not to enforce that params are correctly
      // delcared.  Switch this to using the deprecatedV1 bit
      new CheckTemplateParamsVisitor(registry, declaredSyntaxVersion, errorReporter).exec(fileSet);
    }
  }

  private final class CheckDelegatesPass extends CompilerFileSetPass {
    private final boolean enabledStrictHtml =
        options.getExperimentalFeatures().contains("stricthtml");

    @Override
    public void run(SoyFileSetNode fileSet, TemplateRegistry registry) {
      new CheckDelegatesVisitor(registry, enabledStrictHtml, errorReporter).exec(fileSet);
    }
  }

  private final class CheckVisibilityPass extends CompilerFileSetPass {
    @Override
    public void run(SoyFileSetNode fileSet, TemplateRegistry registry) {
      // TODO(lukes): make this part of CheckCallsPass?
      new CheckTemplateVisibility(registry, errorReporter).exec(fileSet);
    }
  }

  private final class StrictDepsPass extends CompilerFileSetPass {
    @Override
    public void run(SoyFileSetNode fileSet, TemplateRegistry registry) {
      new StrictDepsVisitor(registry, errorReporter).exec(fileSet);
    }
  }

  private static final SoyErrorKind ALIAS_CONFLICTS_WITH_GLOBAL =
      SoyErrorKind.of("Alias ''{0}'' conflicts with a global of the same name.");
  private static final SoyErrorKind ALIAS_CONFLICTS_WITH_GLOBAL_PREFIX =
      SoyErrorKind.of("Alias ''{0}'' conflicts with namespace for global ''{1}''.");

  private final class ValidateAliasesPass extends CompilerFilePass {
    @Override
    public void run(SoyFileNode file, IdGenerator nodeIdGen) {
      for (AliasDeclaration alias : file.getAliasDeclarations()) {
        if (options.getCompileTimeGlobals().containsKey(alias.getAlias())) {
          errorReporter.report(alias.getLocation(), ALIAS_CONFLICTS_WITH_GLOBAL, alias.getAlias());
        }
        for (String global : options.getCompileTimeGlobals().keySet()) {
          if (global.startsWith(alias.getAlias() + ".")) {
            errorReporter.report(
                alias.getLocation(), ALIAS_CONFLICTS_WITH_GLOBAL_PREFIX, alias.getAlias(), global);
          }
        }
      }
    }
  }
}
