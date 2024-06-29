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

import com.google.common.base.Suppliers;
import com.google.template.soy.jbcsrc.restricted.BytecodeUtils;
import com.google.template.soy.jbcsrc.restricted.CodeBuilder;
import com.google.template.soy.jbcsrc.restricted.Expression;
import com.google.template.soy.jbcsrc.restricted.MethodRefs;
import com.google.template.soy.jbcsrc.restricted.Statement;
import java.util.function.Supplier;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;

/** A helper for generating detach operations in soy expressions. */
interface ExpressionDetacher {
  interface Factory {
    /**
     * Returns a new {@link ExpressionDetacher}. Any given soy expression requires at most one
     * detacher.
     *
     * <p>The reattachPoint should be {@link CodeBuilder#mark(Label) marked} by the caller at a
     * location where the stack depth is 0 and will be used to 'reattach' execution if the compiled
     * expression needs to perform a detach operation.
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

  /** Returns an expression for the same SoyValueProvider, after it is resolved. */
  Expression waitForSoyValueProvider(Expression soyValueProvider);

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
   * <p>This assumes that all SoyValueProviders will be already resolved and simply calls resolve().
   */
  final class NullDetatcher implements ExpressionDetacher, Factory {
    static final NullDetatcher INSTANCE = new NullDetatcher();

    @Override
    public NullDetatcher createExpressionDetacher(Label reattachPoint) {
      return this;
    }

    @Override
    public Expression waitForSoyValueProvider(Expression soyValueProvider) {
      soyValueProvider.checkAssignableTo(BytecodeUtils.SOY_VALUE_PROVIDER_TYPE);
      return soyValueProvider;
    }

    @Override
    public Expression resolveSoyValueProvider(Expression soyValueProvider) {
      soyValueProvider.checkAssignableTo(BytecodeUtils.SOY_VALUE_PROVIDER_TYPE);
      if (BytecodeUtils.isDefinitelyAssignableFrom(
          BytecodeUtils.SOY_VALUE_TYPE, soyValueProvider.resultType())) {
        return soyValueProvider;
      }
      return soyValueProvider.invoke(MethodRefs.SOY_VALUE_PROVIDER_RESOLVE);
    }

    @Override
    public Expression resolveSoyValueProviderList(Expression soyValueProviderList) {
      soyValueProviderList.checkAssignableTo(BytecodeUtils.LIST_TYPE);
      return MethodRefs.RUNTIME_CHECK_RESOLVED_LIST.invoke(soyValueProviderList);
    }

    @Override
    public Expression resolveSoyValueProviderMap(Expression soyValueProviderMap) {
      soyValueProviderMap.checkAssignableTo(BytecodeUtils.MAP_TYPE);
      return MethodRefs.RUNTIME_CHECK_RESOLVED_MAP.invoke(soyValueProviderMap);
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
  final class BasicDetacher implements ExpressionDetacher {
    private final Supplier<Statement> saveOperationSupplier;

    private boolean hasDetaches;

    BasicDetacher(Supplier<Statement> saveOperationSupplier) {
      this.saveOperationSupplier = Suppliers.memoize(saveOperationSupplier::get);
    }

    public boolean hasDetaches() {
      return hasDetaches;
    }

    @Override
    public Expression waitForSoyValueProvider(Expression soyValueProvider) {
      soyValueProvider.checkAssignableTo(BytecodeUtils.SOY_VALUE_PROVIDER_TYPE);
      if (BytecodeUtils.isDefinitelyAssignableFrom(SOY_VALUE_TYPE, soyValueProvider.resultType())) {
        return soyValueProvider;
      }
      Statement saveOperation = saveOperationSupplier.get();
      hasDetaches = true;
      return new Expression(soyValueProvider.resultType()) {
        @Override
        protected void doGen(CodeBuilder cb) {
          // We use a bunch of dup() operations in order to save extra field reads and method
          // invocations.  This makes it difficult/confusing to use the expression api. So instead
          // call a bunch of unchecked invocations.
          // Legend: SVP = SoyValueProvider, RR = RenderResult, Z = boolean, SV = SoyValue
          soyValueProvider.gen(cb); // Stack: SVP
          cb.dup(); // Stack: SVP, SVP
          MethodRefs.SOY_VALUE_PROVIDER_STATUS.invokeUnchecked(cb); // Stack: SVP, RR
          if (DetachState.FORCE_EVERY_DETACH_POINT) {
            cb.visitLdcInsn(DetachState.ForceDetachPointsForTesting.uniqueCallSite());
            DetachState.ForceDetachPointsForTesting.MAYBE_FORCE_CONTINUE_AFTER_RENDER_RESULT
                .invokeUnchecked(cb);
          }
          cb.dup(); // Stack: SVP, RR, RR
          MethodRefs.RENDER_RESULT_IS_DONE.invokeUnchecked(cb); // Stack: SVP, RR, Z
          Label end = new Label();
          // if isDone goto end
          cb.ifZCmp(Opcodes.IFNE, end); // Stack: SVP, RR

          saveOperation.gen(cb);
          cb.returnValue();
          cb.mark(end);
          cb.pop(); // Stack: SVP
        }
      };
    }

    @Override
    public Expression resolveSoyValueProvider(Expression soyValueProvider) {
      soyValueProvider.checkAssignableTo(SOY_VALUE_PROVIDER_TYPE);
      // if this expression is already assignable to a SoyValue, we don't need to do
      // anything.
      if (BytecodeUtils.isDefinitelyAssignableFrom(SOY_VALUE_TYPE, soyValueProvider.resultType())) {
        return soyValueProvider;
      }
      return waitForSoyValueProvider(soyValueProvider)
          .invoke(MethodRefs.SOY_VALUE_PROVIDER_RESOLVE);
    }

    @Override
    public Expression resolveSoyValueProviderList(Expression soyValueProviderList) {
      soyValueProviderList.checkAssignableTo(BytecodeUtils.LIST_TYPE);
      Statement saveOperation = saveOperationSupplier.get();
      hasDetaches = true;
      return new Expression(soyValueProviderList.resultType()) {
        @Override
        protected void doGen(CodeBuilder cb) {
          // We use a bunch of dup() operations in order to save extra field reads and method
          // invocations.  This makes it difficult/confusing to use the expression api. So instead
          // call a bunch of unchecked invocations.
          // Legend: List = SoyValueProviderList, RR = RenderResult, Z = boolean
          soyValueProviderList.gen(cb); // Stack: List
          cb.dup(); // Stack: List, List
          MethodRefs.RUNTIME_GET_LIST_STATUS.invokeUnchecked(cb); // Stack: List, RR
          if (DetachState.FORCE_EVERY_DETACH_POINT) {
            cb.visitLdcInsn(DetachState.ForceDetachPointsForTesting.uniqueCallSite());
            DetachState.ForceDetachPointsForTesting.MAYBE_FORCE_CONTINUE_AFTER_RENDER_RESULT
                .invokeUnchecked(cb);
          }
          cb.dup(); // Stack: List, RR, RR
          MethodRefs.RENDER_RESULT_IS_DONE.invokeUnchecked(cb); // Stack: List, RR, Z
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
    public Expression resolveSoyValueProviderMap(Expression soyValueProviderMap) {
      soyValueProviderMap.checkAssignableTo(BytecodeUtils.MAP_TYPE);
      Statement saveOperation = saveOperationSupplier.get();
      hasDetaches = true;
      return new Expression(soyValueProviderMap.resultType()) {
        @Override
        protected void doGen(CodeBuilder cb) {
          // We use a bunch of dup() operations in order to save extra field reads and method
          // invocations.  This makes it difficult/confusing to use the expression api. So instead
          // call a bunch of unchecked invocations.
          // Legend: Map = SoyValueProviderMap, RR = RenderResult, Z = boolean
          soyValueProviderMap.gen(cb); // Stack: Map
          cb.dup(); // Stack: Map, Map
          MethodRefs.RUNTIME_GET_MAP_STATUS.invokeUnchecked(cb); // Stack: Map, RR
          if (DetachState.FORCE_EVERY_DETACH_POINT) {
            cb.visitLdcInsn(DetachState.ForceDetachPointsForTesting.uniqueCallSite());
            DetachState.ForceDetachPointsForTesting.MAYBE_FORCE_CONTINUE_AFTER_RENDER_RESULT
                .invokeUnchecked(cb);
          }
          cb.dup(); // Stack: Map, RR, RR
          MethodRefs.RENDER_RESULT_IS_DONE.invokeUnchecked(cb); // Stack: Map, RR, Z
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
