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

package com.google.template.soy.jbcsrc;

import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.SOY_VALUE_PROVIDER_TYPE;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.SOY_VALUE_TYPE;

import com.google.template.soy.data.SoyValueProvider;
import com.google.template.soy.jbcsrc.restricted.BytecodeUtils;
import com.google.template.soy.jbcsrc.restricted.CodeBuilder;
import com.google.template.soy.jbcsrc.restricted.Expression;
import com.google.template.soy.jbcsrc.restricted.MethodRef;
import com.google.template.soy.jbcsrc.restricted.Statement;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;

/** A helper for generating detach operations in soy expressions. */
interface ExpressionDetacher {
  interface Factory {
    /**
     * Returns a new {@link ExpressionDetacher}. Any given soy expression requires at most one
     * detacher.
     */
    ExpressionDetacher createExpressionDetacher(Label reattachPoint);
  }

  /**
   * Returns an expression for the SoyValue that is resolved by the given SoyValueProvider,
   * potentially detaching if it is not {@link SoyValueProvider#status() resolvable}.
   *
   * @param soyValueProvider an expression yielding a SoyValueProvider
   * @return an expression yielding a SoyValue returned by {@link SoyValueProvider#resolve()}.
   */
  Expression resolveSoyValueProvider(Expression soyValueProvider);

  /**
   * Given a list of SoyValueProviders, await for all members to be resolved.
   *
   * @param soyValueProviderList an expression yielding a list of SoyValueProviders
   * @return an expression yielding the soyValueProviderList, but it is guaranteed that all items
   *     will be ready to be resolved.
   */
  Expression resolveSoyValueProviderList(Expression soyValueProviderList);

  /**
   * Given a map that values are SoyValueProviders, await for all members to be resolved.
   *
   * @param soyValueProviderMap an expression yielding a map that values are SoyValueProviders.
   * @return an expression yielding the soyValueProviderMap, but it is guaranteed that all values
   *     will be ready to be resolved.
   */
  Expression resolveSoyValueProviderMap(Expression soyValueProviderMap);

  /**
   * An {@link ExpressionDetacher} for use by the {@link ExpressionCompiler#createConstantCompiler}.
   *
   * <p>This assumes that no SoyValueProviders will be accessed and has no-op implementations of the
   * list and map resolvers for use by the proto-init code.
   */
  static final class NullDetatcher implements ExpressionDetacher, Factory {
    static final NullDetatcher INSTANCE = new NullDetatcher();

    @Override
    public NullDetatcher createExpressionDetacher(Label reattachPoint) {
      return this;
    }

    @Override
    public Expression resolveSoyValueProvider(final Expression soyValueProvider) {
      throw new AssertionError("shouldn't be called");
    }

    // These 2 are used by proto-init code for repeated fields.

    @Override
    public Expression resolveSoyValueProviderList(final Expression soyValueProviderList) {
      soyValueProviderList.checkAssignableTo(BytecodeUtils.LIST_TYPE);
      return soyValueProviderList;
    }

    @Override
    public Expression resolveSoyValueProviderMap(final Expression soyValueProviderMap) {
      soyValueProviderMap.checkAssignableTo(BytecodeUtils.MAP_TYPE);
      return soyValueProviderMap;
    }
  }

  /**
   * An {@link ExpressionDetacher} that simply returns the {@link
   * com.google.template.soy.jbcsrc.api.RenderResult.RenderResult} returned from {@link
   * SoyValueProvider#status()} if it isn't done.
   *
   * <p>Generates code that looks like:
   *
   * <pre>{@code
   * SoyValueProvider expr = ...;
   * if (!expr.status().isDone()) {
   *   return expr.status();
   * }
   * expr.resolve();
   * }</pre>
   */
  static final class BasicDetacher implements ExpressionDetacher {
    static final BasicDetacher INSTANCE = new BasicDetacher(Statement.NULL_STATEMENT);
    private final Statement saveOperation;

    BasicDetacher(Statement saveOperation) {
      this.saveOperation = saveOperation;
    }

    @Override
    public Expression resolveSoyValueProvider(final Expression soyValueProvider) {
      soyValueProvider.checkAssignableTo(SOY_VALUE_PROVIDER_TYPE);
      return new Expression(SOY_VALUE_TYPE) {
        @Override
        protected void doGen(CodeBuilder adapter) {
          // We use a bunch of dup() operations in order to save extra field reads and method
          // invocations.  This makes it difficult/confusing to use the expression api. So instead
          // call a bunch of unchecked invocations.
          // Legend: SVP = SoyValueProvider, RR = RenderResult, Z = boolean, SV = SoyValue
          soyValueProvider.gen(adapter); // Stack: SVP
          adapter.dup(); // Stack: SVP, SVP
          MethodRef.SOY_VALUE_PROVIDER_STATUS.invokeUnchecked(adapter); // Stack: SVP, RR
          adapter.dup(); // Stack: SVP, RR, RR
          MethodRef.RENDER_RESULT_IS_DONE.invokeUnchecked(adapter); // Stack: SVP, RR, Z
          Label end = new Label();
          // if isDone goto end
          adapter.ifZCmp(Opcodes.IFNE, end); // Stack: SVP, RR

          saveOperation.gen(adapter);
          adapter.returnValue();
          adapter.mark(end);
          adapter.pop(); // Stack: SVP
          MethodRef.SOY_VALUE_PROVIDER_RESOLVE.invokeUnchecked(adapter); // Stack: SV
        }
      };
    }

    @Override
    public Expression resolveSoyValueProviderList(final Expression soyValueProviderList) {
      soyValueProviderList.checkAssignableTo(BytecodeUtils.LIST_TYPE);
      return new Expression(soyValueProviderList.resultType()) {
        @Override
        protected void doGen(CodeBuilder cb) {
          // We use a bunch of dup() operations in order to save extra field reads and method
          // invocations.  This makes it difficult/confusing to use the expression api. So instead
          // call a bunch of unchecked invocations.
          // Legend: List = SoyValueProviderList, RR = RenderResult, Z = boolean
          soyValueProviderList.gen(cb); // Stack: List
          cb.dup(); // Stack: List, List
          MethodRef.RUNTIME_GET_LIST_STATUS.invokeUnchecked(cb); // Stack: List, RR
          cb.dup(); // Stack: List, RR, RR
          MethodRef.RENDER_RESULT_IS_DONE.invokeUnchecked(cb); // Stack: List, RR, Z
          Label end = new Label();
          // if isDone goto end
          cb.ifZCmp(Opcodes.IFNE, end); // Stack: List, RR

          saveOperation.gen(cb);
          cb.returnValue();
          cb.mark(end);
          cb.pop(); // Stack: List
        }
      };
    }

    @Override
    public Expression resolveSoyValueProviderMap(final Expression soyValueProviderMap) {
      soyValueProviderMap.checkAssignableTo(BytecodeUtils.MAP_TYPE);
      return new Expression(soyValueProviderMap.resultType()) {
        @Override
        protected void doGen(CodeBuilder cb) {
          // We use a bunch of dup() operations in order to save extra field reads and method
          // invocations.  This makes it difficult/confusing to use the expression api. So instead
          // call a bunch of unchecked invocations.
          // Legend: Map = SoyValueProviderMap, RR = RenderResult, Z = boolean
          soyValueProviderMap.gen(cb); // Stack: Map
          cb.dup(); // Stack: Map, Map
          MethodRef.RUNTIME_GET_MAP_STATUS.invokeUnchecked(cb); // Stack: Map, RR
          cb.dup(); // Stack: Map, RR, RR
          MethodRef.RENDER_RESULT_IS_DONE.invokeUnchecked(cb); // Stack: Map, RR, Z
          Label end = new Label();
          // if isDone go to end
          cb.ifZCmp(Opcodes.IFNE, end); // Stack: Map, RR

          saveOperation.gen(cb);
          cb.returnValue();
          cb.mark(end);
          cb.pop(); // Stack: Map
        }
      };
    }
  }
}
