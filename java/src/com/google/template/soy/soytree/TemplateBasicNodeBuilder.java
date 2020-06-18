/*
 * Copyright 2013 Google Inc.
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

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.soytree.TemplateNode.SoyFileHeaderInfo;
import java.util.List;

/**
 * Builder for TemplateBasicNode.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public class TemplateBasicNodeBuilder extends TemplateNodeBuilder<TemplateBasicNodeBuilder> {

  /** @param soyFileHeaderInfo Info from the containing Soy file's header declarations. */
  public TemplateBasicNodeBuilder(
      SoyFileHeaderInfo soyFileHeaderInfo, ErrorReporter errorReporter) {
    super(soyFileHeaderInfo, errorReporter);
  }

  @Override
  public TemplateBasicNodeBuilder setCommandValues(
      Identifier templateName, List<CommandTagAttribute> attrs) {
    this.cmdText = templateName.identifier() + " " + Joiner.on(' ').join(attrs);
    setCommonCommandValues(attrs);

    visibility = Visibility.PUBLIC;
    for (CommandTagAttribute attribute : attrs) {
      Identifier name = attribute.getName();
      if (COMMON_ATTRIBUTE_NAMES.contains(name.identifier())) {
        continue;
      }
      switch (name.identifier()) {
        case "visibility":
          visibility = attribute.valueAsVisibility(errorReporter);
          break;
        default:
          errorReporter.report(
              name.location(),
              CommandTagAttribute.UNSUPPORTED_ATTRIBUTE_KEY,
              name.identifier(),
              "template",
              ImmutableList.builder().add("visibility").addAll(COMMON_ATTRIBUTE_NAMES).build());
      }
    }

    setTemplateNames(soyFileHeaderInfo.getNamespace() + templateName.identifier(), templateName);
    return this;
  }

  @Override
  public TemplateBasicNode build() {
    Preconditions.checkState(id != null && cmdText != null);
    return new TemplateBasicNode(this, soyFileHeaderInfo, visibility, params);
  }

  @Override
  protected TemplateBasicNodeBuilder self() {
    return this;
  }
}
