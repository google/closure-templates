/*
 * Copyright 2012 Google Inc.
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

import com.google.template.soy.base.internal.LegacyInternalSyntaxException;

/**
 * Utilities for creating and modifying LegacyInternalSyntaxException objects.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public class SoySyntaxExceptionUtils {

  private SoySyntaxExceptionUtils() {}

  /**
   * Adds meta info to an existing LegacyInternalSyntaxException. The meta info is derived from a
   * Soy node.
   *
   * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
   *
   * @param sse The SoySyntaxException to add meta info to.
   * @param node The node from which to derive the exception meta info.
   * @return The same SoySyntaxException object, for convenience.
   */
  public static LegacyInternalSyntaxException associateNode(
      LegacyInternalSyntaxException sse, SoyNode node) {

    TemplateNode template = node.getNearestAncestor(TemplateNode.class);
    String templateName = (template != null) ? template.getTemplateNameForUserMsgs() : null;

    return sse.associateMetaInfo(node.getSourceLocation(), null, templateName);
  }
}
