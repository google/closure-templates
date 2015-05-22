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

package com.google.template.soy.jbcsrc.api;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableSet;
import com.google.template.soy.data.SoyRecord;
import com.google.template.soy.soytree.TemplateDelegateNode;
import com.google.template.soy.soytree.TemplateDelegateNode.DelTemplateKey;
import com.google.template.soy.soytree.TemplateRegistry;
import com.google.template.soy.soytree.TemplateRegistry.DelegateTemplateConflictException;

/**
 * A helper for performing deltemplate selection.
 * 
 * <p>TODO(lukes): currently this delegates to the TemplateRegistry for most of the logic.  This 
 * will eventually need to change (simply so we can stop depending on the ASTs at runtime).  We 
 * could take some inspiration from the jssrc implementation and have the templates register 
 * themselves into a static data structure when loaded.  For pure variant selection it should be
 * easy to do this, but delpackages and defaults (aka priorities) make it complex.
 */
public final class DelTemplateSelectorImpl implements DelTemplateSelector {
  /** A factory for {@link DelTemplateSelectorImpl}. */
  public static final class Factory {
    private final TemplateRegistry registry;
    private final CompiledTemplates templates;

    public Factory(TemplateRegistry registry, CompiledTemplates templates) {
      this.registry = checkNotNull(registry);
      this.templates = checkNotNull(templates);
    }

    /** Returns a new selector with the given active packages. */
    public DelTemplateSelector create(Iterable<String> activeDelPackages) {
      return new DelTemplateSelectorImpl(registry, templates,
          ImmutableSet.copyOf(activeDelPackages));
    }
  }

  // A factory for a template that always renders the empty string.
  private static final CompiledTemplate.Factory EMPTY_FACTORY = new CompiledTemplate.Factory() {
    final CompiledTemplate emptyTemplate = new CompiledTemplate() {
      @Override public RenderResult render(AdvisingAppendable appendable, RenderContext context) {
        return RenderResult.done();
      }
    };

    @Override public CompiledTemplate create(SoyRecord params, SoyRecord ij) {
      return emptyTemplate;
    }
  };

  private final TemplateRegistry registry;
  private final ImmutableSet<String> activeDelPackages;
  private final CompiledTemplates templates;

  private DelTemplateSelectorImpl(
      TemplateRegistry registry, 
      CompiledTemplates templates, 
      ImmutableSet<String> activeDelTemplate) {
    this.registry = checkNotNull(registry);
    this.activeDelPackages = checkNotNull(activeDelTemplate);
    this.templates = checkNotNull(templates);
  }

  @Override public CompiledTemplate.Factory selectDelTemplate(
      String calleeName, String variant, boolean allowEmpty) {
    DelTemplateKey delegateKey = DelTemplateKey.create(calleeName, variant);

    TemplateDelegateNode callee;
    try {
      callee = registry.selectDelTemplate(delegateKey, activeDelPackages);
    } catch (DelegateTemplateConflictException e) {
      // TODO(lukes): better exception type? make DelegateTemplateConflictException unchecked?
      throw new RuntimeException(e);
    }
    if (callee == null) {
      if (allowEmpty) {
        return EMPTY_FACTORY;
      }
      throw new IllegalArgumentException(
          "Found no active impl for delegate call to '" + calleeName
              + "' (and no attribute allowemptydefault=\"true\").");
    }
    return templates.getTemplateFactory(callee.getTemplateName());
  }
}
