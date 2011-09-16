/*
 * Copyright 2010 Google Inc.
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

package com.google.template.soy.parsepasses.contextautoesc;

import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.TemplateNode;

import javax.annotation.ParametersAreNullableByDefault;

/**
 * Indicates failure to propagate contexts through a template or an existing escaping directive on a
 * <code>{print}</code> that is inconsistent with the contexts in which it appears.
 */
@ParametersAreNullableByDefault
public final class SoyAutoescapeException extends SoySyntaxException {


  /**
   * @param node The node at which the problem was noticed or null.
   */
  public SoyAutoescapeException(SoyNode node, String message, Throwable cause) {
    super(message, cause);
    if (node != null) {
      setContextNode(node);
    }
  }

  public SoyAutoescapeException(SoyNode node, String message) {
    this(node, message, null);
  }

  public SoyAutoescapeException(String message, Throwable cause) {
    this(null, message, cause);
  }

  public SoyAutoescapeException(String message) {
    this(null, message, null);
  }

  /**
   * Associates useful debugging information (file location, template name) with this exception.
   */
  public void setContextNode(SoyNode contextNode) {
    TemplateNode containingTemplate = contextNode.getNearestAncestor(TemplateNode.class);
    if (containingTemplate != null) {
      setTemplateName(containingTemplate.getTemplateNameForUserMsgs());
    }
    SourceLocation location = contextNode.getLocation();
    if (location.isKnown()) {
      setSourceLocation(location);
    } else {
      SoyFileNode containingFile = contextNode.getNearestAncestor(SoyFileNode.class);
      setFilePath(containingFile.getFilePath());
    }
  }

  public void maybeSetContextNode(SoyNode contextNode) {
    if (!getSourceLocation().isKnown()) {
      setContextNode(contextNode);
    }
  }
}
