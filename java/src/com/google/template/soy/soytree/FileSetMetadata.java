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

import com.google.common.base.Preconditions;
import com.google.template.soy.base.SourceFilePath;
import com.google.template.soy.shared.internal.DelTemplateSelector;
import com.google.template.soy.types.TemplateType.TemplateKind;
import java.util.Collection;
import javax.annotation.Nullable;

/** A registry or index of all in-scope templates. */
public interface FileSetMetadata extends PartialFileSetMetadata {

  @Nullable
  TemplateMetadata getTemplate(String templateFqn);

  default TemplateMetadata getTemplate(TemplateNode node) {
    return Preconditions.checkNotNull(getTemplate(node.getTemplateName()));
  }

  /** Returns a multimap from delegate template name to set of keys. */
  DelTemplateSelector<TemplateMetadata> getDelTemplateSelector();

  @Override
  default PartialFileMetadata getPartialFile(SourceFilePath path) {
    return getFile(path);
  }

  @Override
  default Collection<? extends PartialFileMetadata> getAllPartialFiles() {
    return getAllFiles();
  }

  @Nullable
  FileMetadata getFile(SourceFilePath path);

  Collection<? extends FileMetadata> getAllFiles();

  /**
   * Returns all registered templates ({@link TemplateBasicNode basic} and {@link
   * TemplateDelegateNode delegate} nodes), in no particular order. This will include multiple
   * entries for any naming collisions.
   */
  Collection<TemplateMetadata> getAllTemplates();

  /**
   * Retrieves a template or element given the template name.
   *
   * @param templateFqn The basic template name to retrieve.
   * @return The corresponding template/element, or null if the name is not defined.
   */
  @Nullable
  default TemplateMetadata getBasicTemplateOrElement(String templateFqn) {
    TemplateMetadata metadata = getTemplate(templateFqn);
    if (metadata == null) {
      return null;
    }
    TemplateKind kind = metadata.getTemplateType().getTemplateKind();
    return kind == TemplateKind.BASIC || kind == TemplateKind.ELEMENT ? metadata : null;
  }
}
