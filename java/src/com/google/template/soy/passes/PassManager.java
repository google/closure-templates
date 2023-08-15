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
import static java.util.stream.Collectors.joining;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.template.soy.base.SourceFilePath;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.conformance.ValidatedConformanceConfig;
import com.google.template.soy.css.CssRegistry;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.passes.CompilerFileSetPass.Result;
import com.google.template.soy.passes.CompilerFileSetPass.TopologicallyOrdered;
import com.google.template.soy.plugin.java.MethodChecker;
import com.google.template.soy.shared.SoyGeneralOptions;
import com.google.template.soy.shared.restricted.SoyPrintDirective;
import com.google.template.soy.soytree.FileSetMetadata;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.types.SoyTypeRegistry;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Configures all compiler passes.
 *
 * <p>This arranges all compiler passes into four phases.
 *
 * <ul>
 *   <li>The single file passes. This includes AST rewriting passes such as {@link
 *       ResolveExpressionTypesPass} and {@link RewriteGenderMsgsPass} and other kinds of validation
 *       that doesn't require information about the full file set.
 *   <li>Cross template checking passes. This includes AST validation passes like the {@link
 *       CheckTemplateVisibilityPass}. Passes should run here if they need to check the
 *       relationships between templates.
 *   <li>The autoescaper. This runs in its own special phase because it can do special things like
 *       create synthetic templates and add them to the tree.
 *   <li>Simplification passes. This includes tree simplification passes like the optimizer. These
 *       should run last so that they can simplify code generated by any earlier pass.
 * </ul>
 *
 * <p>The reason things have been divided in this way is partially to create consistency and also to
 * enable other compiler features. For example, for in process (server side) compilation we can
 * cache the results of the single file passes to speed up edit-refresh flows. Also, theoretically,
 * we could run the single file passes for each file in parallel.
 *
 * <p>A note on ordering. There is no real structure to the ordering of the passes beyond what is
 * documented in comments. Many passes do rely on running before/after a different pass (e.g. {@link
 * ResolveExpressionTypesPass} needs to run after {@link ResolveNamesPass}), but there isn't any
 * dependency system in place.
 */
public final class PassManager {

  /**
   * Pass continuation rules. By default, compilation continues after each pass without stopping.
   */
  public enum PassContinuationRule {
    /** Stops compilation immediately before a pass. */
    STOP_BEFORE_PASS,
    /** Stops compilation immediately after a pass. */
    STOP_AFTER_PASS,
  }

  /** State used for inter-pass communication, without modifying the AST. */
  private static class AccumulatedState {
    private FileSetMetadata fileSetMetadataFromDeps;
    private FileSetMetadata fileSetMetadataFull;
    private ImmutableList<SoyFileNode> topologicallyOrderedFiles;

    FileSetMetadata registryFromDeps() {
      return fileSetMetadataFromDeps;
    }

    FileSetMetadata registryFull() {
      return fileSetMetadataFull;
    }
  }

  @VisibleForTesting final ImmutableList<CompilerFilePass> parsePasses;
  @VisibleForTesting final ImmutableList<CompilerFileSetPass> passes;
  private final AccumulatedState accumulatedState;

  private PassManager(
      ImmutableList<CompilerFilePass> parsePasses,
      ImmutableList<CompilerFileSetPass> passes,
      AccumulatedState accumulatedState) {
    this.parsePasses = parsePasses;
    this.passes = passes;
    this.accumulatedState = accumulatedState;
    checkOrdering();
  }

  public void runParsePasses(SoyFileNode file, IdGenerator nodeIdGen) {
    for (CompilerFilePass pass : parsePasses) {
      pass.run(file, nodeIdGen);
    }
  }

  /**
   * Runs passes that are needed before we can add the fileset's files to the {TemplateRegistry}.
   *
   * @param partialFileSetMetadataWithJustDeps registry of just the deps (we don't have enough info
   *     yet to create the metadata for the current fileset).
   */
  public Result runPasses(
      SoyFileSetNode soyTree, FileSetMetadata partialFileSetMetadataWithJustDeps) {
    accumulatedState.fileSetMetadataFromDeps = partialFileSetMetadataWithJustDeps;

    ImmutableList<SoyFileNode> sourceFiles = ImmutableList.copyOf(soyTree.getChildren());
    IdGenerator idGenerator = soyTree.getNodeIdGenerator();
    for (CompilerFileSetPass pass : passes) {
      ImmutableList<SoyFileNode> sourceFilesThisPass = sourceFiles;
      if (pass instanceof TopologicallyOrdered) {
        sourceFilesThisPass = accumulatedState.topologicallyOrderedFiles;
      }
      if (pass.run(sourceFilesThisPass, idGenerator) == Result.STOP) {
        return Result.STOP;
      }
    }
    return Result.CONTINUE;
  }

  @Nullable
  public FileSetMetadata getFinalTemplateRegistry() {
    return accumulatedState.fileSetMetadataFull;
  }

  /** Enforces that the current set of passes doesn't violate any annotated ordering constraints. */
  private void checkOrdering() {
    Set<Class<? extends CompilerPass>> executed = new LinkedHashSet<>();
    for (CompilerPass pass : Iterables.concat(parsePasses, passes)) {
      prepareToRun(executed, pass);
    }
  }

  private static void prepareToRun(Set<Class<? extends CompilerPass>> executed, CompilerPass pass) {
    ImmutableList<Class<? extends CompilerPass>> shouldHaveAlreadyRun = pass.runAfter();
    if (!executed.containsAll(shouldHaveAlreadyRun)) {
      throw new IllegalStateException(
          "Attempted to executed pass "
              + pass.name()
              + " but its dependencies ("
              + shouldHaveAlreadyRun.stream()
                  .filter(dep -> !executed.contains(dep))
                  .map(Class::getSimpleName)
                  .collect(joining(", "))
              + ") haven't run yet.\n Passes executed so far: "
              + executed.stream().map(Class::getSimpleName).collect(joining(", ")));
    }
    ImmutableList<Class<? extends CompilerPass>> shouldNotHaveAlreadyRun = pass.runBefore();
    Set<Class<? extends CompilerPass>> ranButShouldntHave =
        Sets.intersection(new HashSet<>(shouldNotHaveAlreadyRun), executed);
    if (!ranButShouldntHave.isEmpty()) {
      throw new IllegalStateException(
          "Attempted to execute pass "
              + pass.name()
              + " but it should always run before ("
              + ranButShouldntHave.stream().map(Class::getSimpleName).collect(joining(", "))
              + ").\n Passes executed so far: "
              + executed.stream().map(Class::getSimpleName).collect(joining(", ")));
    }
    executed.add(pass.getClass());
  }

  /** See {@link Builder#astRewrites}. */
  public enum AstRewrites {
    /** No AST rewrites whatsoever. */
    NONE {
      @Override
      boolean isNone() {
        return true;
      }
    },

    /** Enough AST rewrites for Kythe analysis to work. */
    KYTHE,

    /** Enough AST rewrites for Tricorder analysis to work. */
    TRICORDER {
      @Override
      public boolean rewriteShortFormCalls() {
        // It is OK for Kythe to depend on the rewritten call nodes since they have appropriate
        // source locations to map back to the original template. For tricorder fixes, we need
        // to make sure that we are only rewriting human-written call nodes.
        return false;
      }
    },

    /** Enough AST rewrites for TSX transpilation to work. */
    TSX {
      @Override
      boolean combineTextNodes() {
        return false;
      }

      @Override
      public boolean rewriteElementComposition() {
        return true;
      }

      @Override
      public boolean rewriteAttributeParams() {
        return true;
      }

      @Override
      public boolean rewriteCssVariables() {
        return false;
      }
    },

    /** All the AST rewrites. */
    ALL {
      @Override
      boolean isAll() {
        return true;
      }
    };

    boolean isAll() {
      return false;
    }

    boolean isNone() {
      return false;
    }

    boolean combineTextNodes() {
      return true;
    }

    public boolean rewriteShortFormCalls() {
      return !isNone();
    }

    public boolean rewriteElementComposition() {
      return isAll();
    }

    public boolean rewriteAttributeParams() {
      return isAll();
    }

    public boolean rewriteCssVariables() {
      return isAll();
    }
  }

  /** A builder for configuring the pass manager. */
  public static final class Builder {
    private SoyTypeRegistry registry;
    // TODO(lukes): combine with the print directive map
    private PluginResolver pluginResolver;
    private ImmutableList<? extends SoyPrintDirective> soyPrintDirectives;
    private ErrorReporter errorReporter;
    private SoyGeneralOptions options;
    private CssRegistry cssRegistry = CssRegistry.EMPTY;
    private boolean allowUnknownGlobals;
    private boolean allowUnknownJsGlobals;
    private boolean disableAllTypeChecking;
    private MethodChecker javaPluginValidator;
    private boolean desugarHtmlNodes = true;
    private boolean desugarIdomFeatures = true;
    private boolean optimize = true;
    private ImmutableSet<SourceFilePath> generatedPathsToCheck = ImmutableSet.of();
    private ValidatedConformanceConfig conformanceConfig = ValidatedConformanceConfig.EMPTY;
    private boolean insertEscapingDirectives = true;
    private boolean addHtmlAttributesForDebugging = true;
    private AstRewrites astRewrites = AstRewrites.ALL;
    private final Map<Class<? extends CompilerPass>, PassContinuationRule>
        passContinuationRegistry = Maps.newHashMap();
    private boolean building;
    private boolean validateJavaMethods = true;
    private final AccumulatedState accumulatedState = new AccumulatedState();

    @CanIgnoreReturnValue
    public Builder setErrorReporter(ErrorReporter errorReporter) {
      this.errorReporter = checkNotNull(errorReporter);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setSoyPrintDirectives(
        ImmutableList<? extends SoyPrintDirective> printDirectives) {
      this.soyPrintDirectives = checkNotNull(printDirectives);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setJavaPluginValidator(MethodChecker javaPluginValidator) {
      this.javaPluginValidator = javaPluginValidator;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setTypeRegistry(SoyTypeRegistry registry) {
      this.registry = checkNotNull(registry);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setCssRegistry(CssRegistry registry) {
      this.cssRegistry = registry;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setPluginResolver(PluginResolver pluginResolver) {
      this.pluginResolver = pluginResolver;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setGeneralOptions(SoyGeneralOptions options) {
      this.options = options;
      return this;
    }

    /**
     * Disables all the passes which enforce and rely on type information.
     *
     * <p>This should only be used for things like message extraction which doesn't tend to be
     * configured with a type registry.
     */
    @CanIgnoreReturnValue
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
    @CanIgnoreReturnValue
    public Builder allowUnknownGlobals() {
      this.allowUnknownGlobals = true;
      return this;
    }

    /**
     * Determines whether passes that modify the AST run. Typically analysis tools set this to false
     * since the resulting AST will not match the original source file.
     */
    @CanIgnoreReturnValue
    public Builder astRewrites(AstRewrites astRewrites) {
      this.astRewrites = astRewrites;
      return this;
    }

    /**
     * Allows the unknownJsGlobal() function to be used.
     *
     * <p>This option is only available for backwards compatibility with legacy JS only templates.
     */
    @CanIgnoreReturnValue
    public Builder allowUnknownJsGlobals() {
      this.allowUnknownJsGlobals = true;
      return this;
    }

    /**
     * Whether to turn all the html nodes back into raw text nodes before code generation.
     *
     * <p>The default is {@code true}.
     */
    @CanIgnoreReturnValue
    public Builder desugarHtmlNodes(boolean desugarHtmlNodes) {
      this.desugarHtmlNodes = desugarHtmlNodes;
      return this;
    }

    /**
     * Whether to desugar idom features such as @state and keys.
     *
     * <p>The default is {@code true}.
     */
    @CanIgnoreReturnValue
    public Builder desugarIdomFeatures(boolean desugarIdomFeatures) {
      this.desugarIdomFeatures = desugarIdomFeatures;
      return this;
    }

    /**
     * Whether to run any of the optimization passes.
     *
     * <p>The default is {@code true}.
     */
    @CanIgnoreReturnValue
    public Builder optimize(boolean optimize) {
      this.optimize = optimize;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setGeneratedPathsToCheck(ImmutableSet<SourceFilePath> generatedPaths) {
      this.generatedPathsToCheck = generatedPaths;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder addHtmlAttributesForDebugging(boolean addHtmlAttributesForDebugging) {
      this.addHtmlAttributesForDebugging = addHtmlAttributesForDebugging;
      return this;
    }

    /** Configures this passmanager to run the conformance pass using the given config object. */
    @CanIgnoreReturnValue
    public Builder setConformanceConfig(ValidatedConformanceConfig conformanceConfig) {
      this.conformanceConfig = checkNotNull(conformanceConfig);
      return this;
    }

    /**
     * Can be used to enable/disable the autoescaper.
     *
     * <p>The autoescaper is enabled by default.
     */
    @CanIgnoreReturnValue
    public Builder insertEscapingDirectives(boolean insertEscapingDirectives) {
      this.insertEscapingDirectives = insertEscapingDirectives;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder validateJavaMethods(boolean validate) {
      validateJavaMethods = validate;
      return this;
    }

    /**
     * Registers a pass continuation rule.
     *
     * <p>By default, compilation continues after each pass. You can stop compilation before or
     * after any pass. This is useful for testing, or for running certain kinds of passes, such as
     * conformance-only compilations.
     *
     * <p>This method overwrites any previously registered rule.
     */
    @CanIgnoreReturnValue
    public Builder addPassContinuationRule(
        Class<? extends CompilerPass> pass, PassContinuationRule rule) {
      checkNotNull(rule);
      Preconditions.checkState(passContinuationRegistry.put(pass, rule) == null);
      return this;
    }

    public PassManager build() {
      // Single file passes
      // These passes perform tree rewriting and all compiler checks that don't require information
      // about callees.
      // Note that we try to run all of the single file passes to report as many errors as possible,
      // meaning that errors reported in earlier passes do not prevent running subsequent passes.
      building = true;
      // Fileset passes run on all sources files and have access to a partial template registry so
      // they can examine information about dependencies.
      // TODO(b/158474755): Try to simplify this pass structure structure once we have template
      // imports.
      PassBuilder passes =
          new PassBuilder()
              .add(new CheckGeneratedSourcesPass(errorReporter, generatedPathsToCheck));
      if (astRewrites.isAll()) {
        passes
            .add(new ContentSecurityPolicyNonceInjectionPass(errorReporter))
            // Needs to come after ContentSecurityPolicyNonceInjectionPass.
            .add(new CheckEscapingSanityFilePass(errorReporter));
      }

      passes
          .add(
              new ImportsPass(
                  errorReporter,
                  disableAllTypeChecking,
                  new ProtoImportProcessor(registry, errorReporter, disableAllTypeChecking),
                  new TemplateImportProcessor(errorReporter, accumulatedState::registryFromDeps),
                  new CssImportProcessor(cssRegistry, errorReporter)))
          .add(new ResolveUseVariantTypePass(errorReporter))
          .add(
              new FileDependencyOrderPass(
                  errorReporter, v -> accumulatedState.topologicallyOrderedFiles = v))
          .add(new ModernFeatureInvariantsEnforcementPass(errorReporter))
          .add(new RestoreGlobalsPass())
          .add(new RestoreCompilerChecksPass(errorReporter))
          // needs to come early since it is necessary to create template metadata objects for
          // header compilation
          .add(new ResolveTemplateParamTypesPass(errorReporter, disableAllTypeChecking));

      // needs to come before SoyConformancePass
      passes.add(new ResolvePluginsPass(pluginResolver));
      // When type checking is disabled, extern implementations will likely not be loaded.
      if (!disableAllTypeChecking) {
        passes.add(
            new ValidateExternsPass(errorReporter, javaPluginValidator, validateJavaMethods));
      }

      // Must come after ResolvePluginsPass.
      if (astRewrites.isAll()) {
        passes
            .add(new RewriteDirectivesCallableAsFunctionsPass(errorReporter))
            .add(new RewriteRemaindersPass(errorReporter))
            .add(new RewriteGenderMsgsPass(errorReporter));
      }
      if (astRewrites.isAll() || astRewrites == AstRewrites.TSX) {
        // Needs to come after any pass that manipulates msg placeholders.
        passes.add(new CalculateMsgSubstitutionInfoPass(errorReporter));
      }
      passes.add(new CheckNonEmptyMsgNodesPass(errorReporter));

      // Run before the RewriteGlobalsPass as it removes some globals.
      passes
          .add(new VeRewritePass())
          .add(new RewriteGlobalsPass())
          .add(
              new XidPass(
                  errorReporter))
          .add(new UnknownJsGlobalPass(allowUnknownJsGlobals, errorReporter))
          .add(new ResolveNamesPass(errorReporter))
          .add(
              new ResolveDottedImportsPass(
                  errorReporter, registry, astRewrites.rewriteCssVariables()));
      if (!astRewrites.isNone()) {
        passes.add(new RewriteElementCompositionFunctionsPass(errorReporter));
      }
      passes.add(new ResolveTemplateNamesPass(errorReporter));
      if (!disableAllTypeChecking) {
        // Without type checking proto enums in variant expressions are not resolved.
        passes.add(new ValidateVariantExpressionsPass(errorReporter));
      }
      // needs to be after ResolveNames and MsgsPass
      if (astRewrites.isAll()) {
        passes.add(new MsgWithIdFunctionPass(errorReporter));
      }

      // The StrictHtmlValidatorPass needs to run after ResolveNames.
      passes
          .add(new StrictHtmlValidationPass(errorReporter))
          .add(new CheckSkipPass(errorReporter))
          .add(new SoyElementPass(errorReporter, accumulatedState::registryFromDeps));
      if (addHtmlAttributesForDebugging) {
        // needs to run after MsgsPass (so we don't mess up the auto placeholder naming algorithm)
        // and before ResolveExpressionTypesPass (since we insert expressions).
        passes.add(new AddDebugAttributesPass());
      }
      if (astRewrites.rewriteAttributeParams()) {
        passes.add(
            new ElementAttributePass(
                errorReporter, accumulatedState::registryFromDeps, desugarIdomFeatures));
      }
      if (!disableAllTypeChecking) {
        passes
            .add(new CheckDeclaredTypesPass(errorReporter))
            // Run before ResolveExpressionTypesPass since this makes type analysis on null safe
            // accesses simpler.
            .add(new NullSafeAccessPass())
            .add(
                new ResolveExpressionTypesPass(
                    errorReporter, pluginResolver, accumulatedState::registryFromDeps))
            .add(new VeDefValidationPass(errorReporter));
        if (astRewrites.isAll()) {
          passes.add(new SimplifyAssertNonNullPass());
        }
        // Must run after ResolveExpressionTypesPass to use allowedToInvokeAsFunction
        passes.add(new TemplateCallMetadataPass(errorReporter));
        if (astRewrites.isAll()) {
          passes.add(new VeLogRewritePass());
        }
        passes.add(new CheckModifiableTemplatesPass(errorReporter));
      }
      passes.add(new CheckAllFunctionsResolvedPass(pluginResolver));

      passes.add(new ResolvePackageRelativeCssNamesPass(errorReporter));

      if (!allowUnknownGlobals) {
        // Must come after RewriteGlobalsPass since that is when values are substituted.
        // We should also run after the ResolveNamesPass which checks for global/param ambiguity and
        // may issue better error messages.
        passes.add(new CheckGlobalsPass(errorReporter));
      }
      passes
          .add(new ValidateAliasesPass(errorReporter))
          .add(new KeyCommandPass(errorReporter, disableAllTypeChecking));

      if (!disableAllTypeChecking && astRewrites.isAll()) {
        // Can't run this pass without VeLogRewritePass.
        passes.add(new VeLogValidationPass(errorReporter, registry));
      }
      // Cross template checking passes

      passes.add(
          new FinalizeTemplateRegistryPass(
              errorReporter,
              accumulatedState::registryFromDeps,
              reg -> accumulatedState.fileSetMetadataFull = reg));

      // Fileset passes run on all sources files and have access to a template registry so they can
      // examine information about dependencies. These are naturally more expensive and should be
      // reserved for checks that require transitive call information (or full delegate sets).
      // Notably, the results of these passes cannot be cached in the AST cache.  So minimize their
      // use.

      // Because conformance exits abruptly after this pass we must ensure that the AST is left in a
      // complete state. Therefore this pass should come after ResolveExpressionTypesPass and
      // others.
      passes.add(new SoyConformancePass(conformanceConfig, errorReporter));
      if (!disableAllTypeChecking) {
        if (astRewrites.rewriteShortFormCalls()) {
          passes.add(new RewriteShortFormCallsPass(errorReporter));
        }
        passes.add(new MoreCallValidationsPass(errorReporter, astRewrites.isAll()));
      }
      passes.add(new CheckTemplateHeaderVarsPass(errorReporter, accumulatedState::registryFull));
      if (!disableAllTypeChecking) {
        passes.add(
            new EnforceExperimentalFeaturesPass(options.getExperimentalFeatures(), errorReporter));
        passes
            .add(new CheckTemplateCallsPass(errorReporter, accumulatedState::registryFull))
            .add(new ElementCheckCrossTemplatePass(errorReporter))
            .add(new CheckValidVarrefsPass(errorReporter))
            .add(new CheckTemplateVisibilityPass(errorReporter, accumulatedState::registryFull))
            .add(new CheckDelegatesPass(errorReporter, accumulatedState::registryFull))
            .add(
                new CheckIndirectDepsPass(errorReporter, registry, accumulatedState::registryFull));
        if (astRewrites.combineTextNodes()) {
          passes.add(new CombineConsecutiveRawTextNodesPass());
        }
        passes.add(
            new AutoescaperPass(
                errorReporter,
                soyPrintDirectives,
                insertEscapingDirectives,
                accumulatedState::registryFull));
        passes.add(new IncrementalDomKeysPass(disableAllTypeChecking));
        if (desugarIdomFeatures && astRewrites.isAll()) {
          // always desugar before the end since the backends (besides incremental dom) cannot
          // handle
          // the nodes.
          passes.add(new DesugarStateNodesPass());
        }
        if (astRewrites.rewriteElementComposition()) {
          passes.add(
              new SoyElementCompositionPass(
                  errorReporter,
                  soyPrintDirectives,
                  accumulatedState::registryFull,
                  desugarIdomFeatures));
        }
      } else {
        if (astRewrites.combineTextNodes()) {

          passes.add(new CombineConsecutiveRawTextNodesPass());
        }
        passes.add(
            new AutoescaperPass(
                errorReporter,
                soyPrintDirectives,
                insertEscapingDirectives,
                accumulatedState::registryFull));
        passes.add(new IncrementalDomKeysPass(disableAllTypeChecking));
      }
      passes.add(new CallAnnotationPass());

      // Relies on information from the autoescaper and valid type information
      if (!disableAllTypeChecking && insertEscapingDirectives) {
        passes.add(new CheckBadContextualUsagePass(errorReporter, accumulatedState::registryFull));
      }

      // Simplification Passes.
      // These tend to simplify or canonicalize the tree in order to simplify the task of code
      // generation.

      if (desugarHtmlNodes) {
        // always desugar before the end since the backends (besides incremental dom) cannot handle
        // the nodes.
        passes.add(new DesugarHtmlNodesPass());
      }
      if (optimize) {
        passes.add(new OptimizationPass(errorReporter));
      }
      // DesugarHtmlNodesPass may chop up RawTextNodes, and OptimizationPass may produce additional
      // RawTextNodes. Stich them back together here.
      if (astRewrites.combineTextNodes()) {
        passes.add(new CombineConsecutiveRawTextNodesPass());
      }
      passes.add(new BanDuplicateNamespacesPass(errorReporter, accumulatedState::registryFull));
      building = false;
      if (!passContinuationRegistry.isEmpty()) {
        throw new IllegalStateException(
            "The following continuation rules don't match any pass: " + passContinuationRegistry);
      }
      return new PassManager(createParsePasses(errorReporter), passes.build(), accumulatedState);
    }

    /** Adds the pass as a file set pass. */
    private class PassBuilder {
      ImmutableList.Builder<CompilerFileSetPass> builder = ImmutableList.builder();

      @CanIgnoreReturnValue
      PassBuilder add(CompilerFileSetPass pass) {
        Class<?> passClass = pass.getClass();
        PassContinuationRule rule = passContinuationRegistry.remove(passClass);
        if (rule == null) {
          if (building) {
            builder.add(pass);
          }
        } else {
          switch (rule) {
            case STOP_AFTER_PASS:
              builder.add(pass);
              // fall-through
            case STOP_BEFORE_PASS:
              Preconditions.checkState(building, "Multiple STOP rules not allowed.");
              building = false;
              break;
          }
        }
        return this;
      }

      ImmutableList<CompilerFileSetPass> build() {
        return builder.build();
      }
    }
  }

  /**
   * Passes that operate purely on the AST and depend on no configuration information.
   *
   * <p>ASTs run through these passes can be safely cached across compiles to speed up interactive
   * recompiles so be very careful before adding parameters to this method. As a corrollary, we
   * definitely want to add passes here if we can since it will speed up interactive recompiles.
   */
  private static ImmutableList<CompilerFilePass> createParsePasses(ErrorReporter reporter) {
    return ImmutableList.of(
        new DesugarGroupNodesPass(),
        new BasicHtmlValidationPass(reporter),
        new InsertMsgPlaceholderNodesPass(reporter));
  }
}
