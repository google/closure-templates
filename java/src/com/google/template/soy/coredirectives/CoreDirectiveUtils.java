/*
 * Copyright 2009 Google Inc.
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

package com.google.template.soy.coredirectives;

import com.google.template.soy.soytree.PrintDirectiveNode;


/**
 * Utilities for common operations related to core directives (|escapeHtml, |noAutoescape, |id).
 *
 * @author Kai Huang
 */
public class CoreDirectiveUtils {

  private CoreDirectiveUtils() {}


  public static boolean isCoreDirective(PrintDirectiveNode directiveNode) {

    String directiveName = directiveNode.getName();
    return directiveName.equals(IdDirective.NAME) ||
           directiveName.equals(NoAutoescapeDirective.NAME) ||
           directiveName.equals(EscapeHtmlDirective.NAME);
  }


  public static boolean isNoAutoescapeOrIdDirective(PrintDirectiveNode directiveNode) {

    String directiveName = directiveNode.getName();
    return directiveName.equals(IdDirective.NAME) ||
           directiveName.equals(NoAutoescapeDirective.NAME);
  }


  public static boolean isEscapeHtmlDirective(PrintDirectiveNode directiveNode) {

    return directiveNode.getName().equals(EscapeHtmlDirective.NAME);
  }

}
