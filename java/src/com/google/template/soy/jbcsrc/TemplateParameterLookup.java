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

import com.google.template.soy.data.SoyRecord;
import com.google.template.soy.data.SoyValueProvider;
import com.google.template.soy.exprtree.AbstractLocalVarDefn;
import com.google.template.soy.jbcsrc.restricted.Expression;
import com.google.template.soy.jbcsrc.restricted.JbcSrcPluginContext;
import com.google.template.soy.jbcsrc.restricted.SoyExpression;
import com.google.template.soy.soytree.defn.TemplateParam;

/** A mechanism to lookup expressions for accessing template parameters. */
interface TemplateParameterLookup {
  /**
   * Returns an expression for a given {@code @param} or {@code @inject} parameter.
   *
   * <p>The expression will be for a {@link SoyValueProvider}.
   */
  Expression getParam(TemplateParam param);

  /**
   * Returns an expression for a given local variable.
   *
   * <p>The type of the expression will be based on the kind of variable being accessed.
   */
  Expression getLocal(AbstractLocalVarDefn<?> local);

  /**
   * Returns an expression for a given {@code @param} or {@code @inject} parameter.
   *
   * <p>The type of the expression will be based on the kind of variable being accessed.
   */
  Expression getLocal(SyntheticVarName varName);

  /**
   * Returns an expression that produces the current {@link
   * com.google.template.soy.jbcsrc.shared.RenderContext}.
   */
  RenderContextExpression getRenderContext();

  /**
   * Returns the plugin context object. This is required for the plugin apis and should be used in
   * preference to {@link #getRenderContext()} whenever possible.
   */
  default JbcSrcPluginContext getPluginContext() {
    // return a lazy delegate.  Most plugins never even need the context, but accessing
    // getRenderContext() will copy the field into the inner class as a side effect.  using a lazy
    // delegate we can avoid that in the common case.
    return new JbcSrcPluginContext() {
      @Override
      public Expression getBidiGlobalDir() {
        return getRenderContext().getBidiGlobalDir();
      }

      @Override
      public Expression getULocale() {
        return getRenderContext().getULocale();
      }

      @Override
      public Expression getAllRequiredCssNamespaces(SoyExpression template) {
        return getRenderContext().getAllRequiredCssNamespaces(template);
      }
    };
  }

  /**
   * Returns the current template's parameter dictionary. The returned expression will have a {@link
   * Expression#resultType()} of {@link SoyRecord}.
   */
  Expression getParamsRecord();

  /**
   * Returns the current template's ij dictionary. The returned expression will have a {@link
   * Expression#resultType()} of {@link SoyRecord}.
   */
  Expression getIjRecord();
}
