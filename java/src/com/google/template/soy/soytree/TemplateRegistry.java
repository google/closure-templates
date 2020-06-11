/*
 * Copyright 2011 Google Inc.
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
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.shared.internal.DelTemplateSelector;
import com.google.template.soy.types.TemplateType;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * A registry or index of all in-scope templates.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public interface TemplateRegistry {

  public static final TemplateRegistry EMPTY =
      FileSetTemplateRegistry.builder(ErrorReporter.exploding()).build();

  /** Returns all basic template names. */
  ImmutableSet<String> getBasicTemplateOrElementNames();

  /** Look up possible targets for a call. */
  ImmutableList<TemplateType> getTemplates(CallNode node);

  /** Gets a map of file paths to templates defined in each file. */
  ImmutableMap<String, TemplatesPerFile> getTemplatesPerFile();

  /** Gets the templates in the given file. */
  TemplatesPerFile getTemplatesPerFile(String fileName);

  /**
   * Retrieves a template or element given the template name.
   *
   * @param templateName The basic template name to retrieve.
   * @return The corresponding template/element, or null if the name is not defined.
   */
  @Nullable
  TemplateMetadata getBasicTemplateOrElement(String templateName);

  /** Returns a multimap from delegate template name to set of keys. */
  DelTemplateSelector<TemplateMetadata> getDelTemplateSelector();

  TemplateMetadata getMetadata(TemplateNode node);

  /**
   * Returns all registered templates ({@link TemplateBasicNode basic} and {@link
   * TemplateDelegateNode delegate} nodes), in no particular order.
   */
  ImmutableList<TemplateMetadata> getAllTemplates();

  /** Returns the full file paths for all files in the registry. */
  ImmutableSet<String> getAllFileNames();

  /**
   * Gets the content kind that a call results in. If used with delegate calls, the delegate
   * templates must use strict autoescaping. This relies on the fact that all delegate calls must
   * have the same kind when using strict autoescaping. This is enforced by CheckDelegatesPass.
   *
   * @param node The {@link CallBasicNode} or {@link CallDelegateNode}.
   * @return The kind of content that the call results in.
   */
  Optional<SanitizedContentKind> getCallContentKind(CallNode node);
}
