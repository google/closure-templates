/*
 * Copyright 2019 Google Inc.
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

import com.google.template.soy.jbcsrc.restricted.Expression;
import com.google.template.soy.jbcsrc.restricted.FieldRef;
import com.google.template.soy.jbcsrc.restricted.JbcSrcPluginContext;
import com.google.template.soy.jbcsrc.restricted.LocalVariable;
import com.google.template.soy.soytree.defn.TemplateParam;

abstract class AbstractTemplateParameterLookup implements TemplateParameterLookup {
  abstract FieldRef getParamField(TemplateParam param);

  abstract FieldRef getParamsRecordField();

  abstract FieldRef getIjRecordField();

  abstract Expression getCompiledTemplate();

  @Override
  public final Expression getParam(TemplateParam param) {
    return accessAndMaybeAdjustVisibility(getParamField(param));
  }

  @Override
  public final Expression getParamsRecord() {
    return accessAndMaybeAdjustVisibility(getParamsRecordField());
  }

  @Override
  public final Expression getIjRecord() {
    return accessAndMaybeAdjustVisibility(getIjRecordField());
  }

  @Override
  public final JbcSrcPluginContext getPluginContext() {
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
      public Expression getAllRequiredCssNamespaces(Expression template) {
        return getRenderContext().getAllRequiredCssNamespaces(template);
      }
    };
  }

  private Expression accessAndMaybeAdjustVisibility(FieldRef ref) {
    Expression owner = getCompiledTemplate();
    // if we are accessing the field relative to 'this' that means it will always succeed.
    // Otherwise we need to raise the visibility to 'package private' so it can be accessed by the
    // inner classes being generated
    if (!(owner instanceof LocalVariable)
        || !((LocalVariable) owner).variableName().equals("this")) {
      ref = ref.setVisibility(0);
    }
    return ref.accessor(owner);
  }
}
