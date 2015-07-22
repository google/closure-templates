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

import static com.google.template.soy.jbcsrc.BytecodeUtils.SOY_VALUE_PROVIDER_TYPE;
import static com.google.template.soy.jbcsrc.BytecodeUtils.SOY_VALUE_TYPE;

import com.google.template.soy.data.SoyValueProvider;
import com.google.template.soy.jbcsrc.api.RenderResult;

import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;

/**
 * A helper for generating detach operations in soy expressions.
 */
interface ExpressionDetacher {
  interface Factory {
    /**
     * Returns a new {@link ExpressionDetacher}.  Any given soy expression requires at most one
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
   * An {@link ExpressionDetacher} that simply returns the {@link RenderResult} returned from
   * {@link SoyValueProvider#status()} if it isn't done.
   * 
   * <p>Generates code that looks like:<pre>    {@code
   *   
   *   SoyValueProvider expr = ...;
   *   if (!expr.status().isDone()) { 
   *     return expr.status();
   *   }
   *   expr.resolve();
   * }</pre>
   */
  static final class BasicDetacher implements ExpressionDetacher {
    static final BasicDetacher INSTANCE = new BasicDetacher();

    private BasicDetacher() {}

    @Override public Expression resolveSoyValueProvider(final Expression soyValueProvider) {
      soyValueProvider.checkAssignableTo(SOY_VALUE_PROVIDER_TYPE);
      return new Expression(SOY_VALUE_TYPE) {
        @Override
        void doGen(CodeBuilder adapter) {
          // We use a bunch of dup() operations in order to save extra field reads and method
          // invocations.  This makes the expression api difficult/confusing to use.  So instead
          // call a bunch of unchecked invocations.
          // Legend: SVP = SoyValueProvider, RS = ResolveStatus, Z = boolean, SV = SoyValue
          soyValueProvider.gen(adapter);                                  // Stack: SVP
          adapter.dup();                                                  // Stack: SVP, SVP
          MethodRef.SOY_VALUE_PROVIDER_STATUS.invokeUnchecked(adapter);   // Stack: SVP, RS
          adapter.dup();                                                  // Stack: SVP, RS, RS
          MethodRef.RENDER_RESULT_IS_DONE.invokeUnchecked(adapter);       // Stack: SVP, RS, Z
          Label end = new Label();
          // if isReady goto end
          adapter.ifZCmp(Opcodes.IFNE, end);                              // Stack: SVP, RS
          adapter.returnValue();
          adapter.mark(end);
          adapter.pop();                                                  // Stack: SVP
          MethodRef.SOY_VALUE_PROVIDER_RESOLVE.invokeUnchecked(adapter);  // Stack: SV
        }
      };
    }
    
  }
}
