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

import com.google.template.soy.base.SoySyntaxException;

import javax.annotation.Nullable;


/**
 * Utilities for creating and modifying SoySyntaxException objects.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author Kai Huang
 */
public class SoySyntaxExceptionUtils {


  private SoySyntaxExceptionUtils() {}


  /**
   * Creates a SoySyntaxException, with meta info filled in based on the given Soy node.
   *
   * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
   *
   * @param message The error message.
   * @param node The node from which to derive the exception meta info.
   * @return The new SoySyntaxException object.
   */
  public static SoySyntaxException createWithNode(String message, SoyNode node) {

    return associateNode(SoySyntaxException.createWithoutMetaInfo(message), node);
  }


  /**
   * Creates a SoySyntaxException, with meta info filled in based on the given Soy node.
   *
   * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
   *
   * @param message The error message, or null to use the message from the cause.
   * @param cause The cause of this exception.
   * @param node The node from which to derive the exception meta info.
   * @return The new SoySyntaxException object.
   */
  public static SoySyntaxException createCausedWithNode(
      @Nullable String message, Throwable cause, SoyNode node) {

    return associateNode(SoySyntaxException.createCausedWithoutMetaInfo(message, cause), node);
  }


  /**
   * Adds meta info to an existing SoySyntaxException. The meta info is derived from a Soy node.
   *
   * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
   *
   * @param sse The SoySyntaxException to add meta info to.
   * @param node The node from which to derive the exception meta info.
   * @return The same SoySyntaxException object, for convenience.
   */
  public static SoySyntaxException associateNode(SoySyntaxException sse, SoyNode node) {

    TemplateNode template = node.getNearestAncestor(TemplateNode.class);
    String templateName = (template != null) ? template.getTemplateNameForUserMsgs() : null;

    return sse.associateMetaInfo(node.getSourceLocation(), null, templateName);
  }

}
