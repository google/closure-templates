/*
 * Copyright 2026 Google Inc.
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

import com.google.template.soy.exprtree.AbstractLocalVarDefn;
import com.google.template.soy.jbcsrc.restricted.Expression;
import com.google.template.soy.jbcsrc.restricted.JbcSrcPluginContext;
import com.google.template.soy.jbcsrc.restricted.LocalVariable;
import com.google.template.soy.soytree.defn.TemplateParam;
import java.util.Optional;

/**
 * Implementation of {@link TemplateParameterLookup} that delegates all calls to another instance of
 * {@link TemplateParameterLookup}.
 */
class DelegatingTemplateParameterLookup implements TemplateParameterLookup {
  private final TemplateParameterLookup delegate;

  DelegatingTemplateParameterLookup(TemplateParameterLookup delegate) {
    this.delegate = delegate;
  }

  @Override
  public LocalVariable getStackFrame() {
    return delegate.getStackFrame();
  }

  @Override
  public Expression getParam(TemplateParam param) {
    return delegate.getParam(param);
  }

  @Override
  public Optional<Expression> getParamsRecord() {
    return delegate.getParamsRecord();
  }

  @Override
  public Expression getLocal(AbstractLocalVarDefn<?> local) {
    return delegate.getLocal(local);
  }

  @Override
  public Expression getLocal(SyntheticVarName varName) {
    return delegate.getLocal(varName);
  }

  @Override
  public RenderContextExpression getRenderContext() {
    return delegate.getRenderContext();
  }

  @Override
  public JbcSrcPluginContext getPluginContext() {
    return delegate.getPluginContext();
  }

  @Override
  public AppendableExpression getAppendable() {
    return delegate.getAppendable();
  }
}
