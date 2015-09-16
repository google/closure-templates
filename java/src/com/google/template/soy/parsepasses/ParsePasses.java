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

package com.google.template.soy.parsepasses;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.basetree.SyntaxVersion;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.passes.CompilerFilePass;
import com.google.template.soy.shared.SoyGeneralOptions;
import com.google.template.soy.shared.restricted.SoyFunction;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoytreeUtils;
import com.google.template.soy.types.SoyTypeRegistry;

/**
 * Configures all the parsing passes.
 *
 * <p>The parsing passes are a collection of operations that mutate/rewrite parts of the parse tree
 * in trivial/obvious ways.  These passes are logically part of parsing the literal text of the soy
 * file and each one could theoretically be done as part of the parser, but for maintainability it
 * is easier to pull them out into separate passes.  It is expected that each of these passes will
 * mutate the AST in critical ways.
 *
 * <p>The default initial parsing passes are:
 * <ul>
 *   <li>{@link RewriteGenderMsgsVisitor}
 *   <li>{@link RewriteRemaindersVisitor}
 *   <li>{@link SetFullCalleeNamesVisitor}
 *   <li>{@link ResolveExpressionTypesVisitor}
 *   <li>{@link ResolveNamesVisitor}
 *   <li>{@link ResolvePackageRelativeCssNamesVisitor}
 *   <li>{@link VerifyPhnameAttrOnlyOnPlaceholdersVisitor}
 *   <li>{@link SubstitueGlobalsVisitorPass}
 * </ul>
 */
public final class ParsePasses {
  private final ImmutableList<CompilerFilePass> passes;
  private final SoyTypeRegistry registry;
  private final ImmutableMap<String, ? extends SoyFunction> soyFunctionMap;
  private final ErrorReporter errorReporter;
  private final SyntaxVersion declaredSyntaxVersion;
  private final SoyGeneralOptions options;
  private final boolean allowUnknownGlobals;
  
  private ParsePasses(Builder builder) {
    this.registry = checkNotNull(builder.registry);
    this.soyFunctionMap = checkNotNull(builder.soyFunctionMap);
    this.errorReporter = checkNotNull(builder.errorReporter);
    this.declaredSyntaxVersion = checkNotNull(builder.declaredSyntaxVersion);
    this.options = checkNotNull(builder.opts);
    this.allowUnknownGlobals = builder.allowUnknownGlobals;

    ImmutableList.Builder<CompilerFilePass> passesBuilder = ImmutableList.builder();
    // Note: RewriteGenderMsgsVisitor must be run first due to the assertion in
    // MsgNode.getAllExprUnions().
    // TODO(lukes): document all ordering dependencies between the passes. 
    passesBuilder
        .add(new RewriteGendersPass())
        .add(new RewriteRemaindersPass())
        .add(new SetFullCalleeNamesPass());
    passesBuilder.add(new ResolveNamesPass())
        .add(new ResolveFunctionsPass())
        .add(new ResolveExpressionTypesPass())
        .add(new ResolvePackageRelativeCssNamesPass())
        .add(new VerifyPhnameAttrOnlyOnPlaceholdersPass())
        .add(new SubstitueGlobalsVisitorPass());
    this.passes = passesBuilder.build();
  }

  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    for (CompilerFilePass pass : passes) {
      pass.run(file, nodeIdGen);
    }
  }

  public static final class Builder {
    private SoyTypeRegistry registry;
    private ImmutableMap<String, ? extends SoyFunction> soyFunctionMap;
    private ErrorReporter errorReporter;
    private SyntaxVersion declaredSyntaxVersion;
    private SoyGeneralOptions opts;
    private boolean allowUnknownGlobals;

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
     * <p>This option is only available for backwards compatibility with legacy js only templates.
     */
    public Builder allowUnknownGlobals() {
      this.allowUnknownGlobals = true;
      return this;
    }

    public ParsePasses build() {
      return new ParsePasses(this);
    }
  }

  private final class RewriteGendersPass extends CompilerFilePass {
    @Override public void run(SoyFileNode file, IdGenerator nodeIdGen) {
      new RewriteGenderMsgsVisitor(nodeIdGen, errorReporter).exec(file);
    }
  }

  private final class RewriteRemaindersPass extends CompilerFilePass {
    @Override public void run(SoyFileNode file, IdGenerator nodeIdGen) {
      new RewriteRemaindersVisitor(errorReporter).exec(file);
    }
  }

  private final class SetFullCalleeNamesPass extends CompilerFilePass {
    @Override public void run(SoyFileNode file, IdGenerator nodeIdGen) {
      new SetFullCalleeNamesVisitor(errorReporter).exec(file);
    }
  }

  private final class ResolveFunctionsPass extends CompilerFilePass {
    @Override
    public void run(SoyFileNode file, IdGenerator nodeIdGen) {
      SoytreeUtils.execOnAllV2Exprs(file, new ResolveFunctionsVisitor(soyFunctionMap));
    }
  }

  private final class ResolveNamesPass extends CompilerFilePass {
    @Override public void run(SoyFileNode file, IdGenerator nodeIdGen) {
      new ResolveNamesVisitor(declaredSyntaxVersion, errorReporter).exec(file);
    }
  }

  private final class ResolveExpressionTypesPass extends CompilerFilePass {
    @Override public void run(SoyFileNode file, IdGenerator nodeIdGen) {
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

  private final class SubstitueGlobalsVisitorPass extends CompilerFilePass {
    SubstituteGlobalsVisitor substituteGlobalsVisitor =
        new SubstituteGlobalsVisitor(
            options.getCompileTimeGlobals(),
            registry,
            !allowUnknownGlobals, // shouldAssertNoUnboundGlobals
            errorReporter);

    @Override
    public void run(SoyFileNode file, IdGenerator nodeIdGen) {
      substituteGlobalsVisitor.exec(file);
    }
  }
}
