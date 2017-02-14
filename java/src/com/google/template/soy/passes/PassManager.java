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
import com.google.template.soy.base.internal.IdGenerator;
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
import java.util.Objects;

/**
 * Configures all the parsing passes.
 *
 * <p>The parsing passes are a collection of operations that mutate/rewrite parts of the parse tree
 * in trivial/obvious ways. These passes are logically part of parsing the literal text of the soy
 * file and each one could theoretically be done as part of the parser, but for maintainability it
 * is easier to pull them out into separate passes. It is expected that each of these passes will
 * mutate the AST in critical ways.
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

  private PassManager(Builder builder) {
    this.registry = checkNotNull(builder.registry);
    this.soyFunctionMap = checkNotNull(builder.soyFunctionMap);
    this.errorReporter = checkNotNull(builder.errorReporter);
    this.declaredSyntaxVersion = checkNotNull(builder.declaredSyntaxVersion);
    this.options = checkNotNull(builder.opts);
    this.allowUnknownGlobals = builder.allowUnknownGlobals;

    boolean enabledStrictHtml = options.getExperimentalFeatures().contains("stricthtml");

    ImmutableList.Builder<CompilerFilePass> singleFilePassesBuilder =
        ImmutableList.<CompilerFilePass>builder()
            .add(new RewriteGendersPass())
            .add(new RewriteRemaindersPass())
            .add(new HtmlRewritePass(options.getExperimentalFeatures(), errorReporter))
            .add(new StrictHtmlValidationPass(options.getExperimentalFeatures(), errorReporter))
            // TODO(lukes): run this very early for now, as support increases this should get moved
            // later and later in the compiler
            .add(new DesugarHtmlNodesPass())
            .add(new RewriteGlobalsPass(registry, options.getCompileTimeGlobals(), errorReporter))
            .add(new RewriteFunctionsPass(registry))
            .add(new SetFullCalleeNamesPass())
            .add(new ResolveNamesPass())
            .add(new ResolveFunctionsPass())
            .add(new ResolveExpressionTypesPass())
            .add(new ResolvePackageRelativeCssNamesPass())
            .add(new VerifyPhnameAttrOnlyOnPlaceholdersPass());
    if (!allowUnknownGlobals) {
      // Must come after RewriteGlobalsPass since that is when values are substituted.
      // We should also run after the ResolveNamesPass which checks for global/param ambiguity and
      // may issue better error messages.
      singleFilePassesBuilder.add(new CheckGlobalsPass(errorReporter));
    }
    singleFilePassesBuilder
        .add(new CheckInvalidParamsPass())
        .add(new ValidateAliasesPass())
        .add(new CheckSyntaxVersionPass())
        // Must run after ResolveExpressionTypesPass, which adds the SoyProtoType info.
        .add(new CheckProtoInitCallsPass(errorReporter))
        .add(
            new CheckFunctionCallsPass(
                builder.allowUnknownFunctions, declaredSyntaxVersion, errorReporter));
    // If requiring strict autoescaping, check and enforce it.
    if (options.isStrictAutoescapingRequired()) {
      singleFilePassesBuilder.add(new EnforceStrictAutoescapingPass());
    }

    this.singleFilePasses = singleFilePassesBuilder.build();
    // Fileset passes run on the whole tree and should be reserved for checks that need transitive
    // call information (or full delegate sets).
    // Notably, the results of these passes cannot be cached in the AST cache.  So minimize their
    // use.
    ImmutableList.Builder<CompilerFileSetPass> fileSetPassBuilder =
        ImmutableList.<CompilerFileSetPass>builder()
            .add(new CheckTemplateParamsPass())
            .add(new CheckTemplateCallsPass(enabledStrictHtml, errorReporter))
            .add(new CheckVisibilityPass())
            .add(new CheckDelegatesPass());
    // If disallowing external calls, perform the check.
    if (Objects.equals(options.allowExternalCalls(), Boolean.FALSE)) {
      fileSetPassBuilder.add(new StrictDepsPass());
    }
    this.fileSetPasses = fileSetPassBuilder.build();
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

  private final class CheckInvalidParamsPass extends CompilerFilePass {
    @Override
    public void run(SoyFileNode file, IdGenerator nodeIdGen) {
      new CheckInvalidParamsVisitor(errorReporter).exec(file);
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
