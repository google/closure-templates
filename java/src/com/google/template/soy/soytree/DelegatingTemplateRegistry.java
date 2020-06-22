/*
 * Copyright 2020 Google Inc.
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

package com.google.template.soy.soytree;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.shared.internal.DelTemplateSelector;
import com.google.template.soy.types.TemplateType;
import java.util.Optional;

/**
 * Implementation of {@link TemplateRegistry} that delegates all calls to another instance of {@link
 * TemplateRegistry}. Used for building chains of registries.
 */
public abstract class DelegatingTemplateRegistry implements TemplateRegistry {

  protected DelegatingTemplateRegistry() {}

  abstract TemplateRegistry getDelegate();

  @Override
  public ImmutableMap<String, TemplatesPerFile> getTemplatesPerFile() {
    return getDelegate().getTemplatesPerFile();
  }

  @Override
  public TemplatesPerFile getTemplatesPerFile(String fileName) {
    return getDelegate().getTemplatesPerFile(fileName);
  }

  @Override
  public ImmutableSet<String> getBasicTemplateOrElementNames() {
    return getDelegate().getBasicTemplateOrElementNames();
  }

  @Override
  public ImmutableList<TemplateType> getTemplates(CallNode node) {
    return getDelegate().getTemplates(node);
  }

  @Override
  public TemplateMetadata getBasicTemplateOrElement(String templateName) {
    return getDelegate().getBasicTemplateOrElement(templateName);
  }

  @Override
  public DelTemplateSelector<TemplateMetadata> getDelTemplateSelector() {
    return getDelegate().getDelTemplateSelector();
  }

  @Override
  public TemplateMetadata getMetadata(TemplateNode node) {
    return getDelegate().getMetadata(node);
  }

  @Override
  public ImmutableList<TemplateMetadata> getAllTemplates() {
    return getDelegate().getAllTemplates();
  }

  @Override
  public ImmutableSet<String> getAllFileNames() {
    return getDelegate().getAllFileNames();
  }

  @Override
  public Optional<SanitizedContentKind> getCallContentKind(CallNode node) {
    return getDelegate().getCallContentKind(node);
  }
}
