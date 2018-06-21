/*
 * Copyright 2016 Google Inc.
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

package com.google.template.soy.jssrc.internal;

import com.google.template.soy.soytree.CallDelegateNode;
import com.google.template.soy.soytree.TemplateDelegateNode;
import javax.inject.Inject;

/**
 * Transforms the name of a delegate template (as it appears in source code) into a name suitable
 * for use in internal data structures. In the base class, the transformation is the identity, but
 * subclasses can override it.
 */
public class DelTemplateNamer {
  @Inject
  public DelTemplateNamer() {}

  /**
   * Gets the name for a delegate template, which is used to refer a delegate template along with
   * the variant.
   *
   * @param node A node representing a delegate template
   * @return A string that is used to refer to the delegate template.
   */
  final String getDelegateName(TemplateDelegateNode node) {
    return getDelegateName(node.getDelTemplateName());
  }

  /**
   * Gets the name for a delegate template, which is used to refer a delegate template along with
   * the variant.
   *
   * @param node A node representing a call to a delegate template.
   * @return A string that is used to refer to the delegate template.
   */
  final String getDelegateName(CallDelegateNode node) {
    return getDelegateName(node.getDelCalleeName());
  }

  /**
   * Gets the name for a delegate template, which is used to refer a delegate template along with
   * the variant.
   *
   * @param delTemplateName The name of the delegate template.
   * @return A string that is used to refer to the delegate template.
   */
  protected String getDelegateName(String delTemplateName) {
    return delTemplateName;
  }
}
