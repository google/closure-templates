/*
 * Copyright 2014 Google Inc.
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

package com.google.template.soy.conformance;

import com.google.common.base.Preconditions;
import com.google.common.reflect.TypeToken;
import com.google.template.soy.basetree.Node;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.soytree.SoyFileSetNode;

/**
 * Abstract base class for a conformance rule applying to one particular node type.
 *
 * @author brndn@google.com (Brendan Linn)
 */
public abstract class Rule<T extends Node> {

  @SuppressWarnings("unchecked")
  private final Class<T> nodeClass = (Class<T>) new TypeToken<T>(getClass()) {}.getRawType();

  protected final SoyErrorKind error;

  /**
   * Subclasses should override this constructor and use {@code
   * errorReporter.report(someSourceLocation, error)} to report conformance errors.
   */
  protected Rule(SoyErrorKind error) {
    // SoyFileSetNode leaves no way to whitelist exceptions.
    if (nodeClass == SoyFileSetNode.class) {
      throw new IllegalStateException(
          "Rule<SoyFileSetNode> does not work with whitelists. Use Rule<SoyFileNode> instead.");
    }
    this.error = Preconditions.checkNotNull(error);
  }

  /**
   * Checks whether the given node is relevant for this rule, and, if so, checks whether the node
   * conforms to the rule. Intended to be called only from {@link CheckConformance#applyRule}.
   */
  final void doCheckConformance(Node node, ErrorReporter errorReporter) {
    if (nodeClass.isAssignableFrom(node.getClass())) {
      checkConformance(nodeClass.cast(node), errorReporter);
    }
  }

  /**
   * Checks whether the node conforms to the given rule. The base implementation is a no-op;
   * subclasses should typically override it.
   *
   * <p>Recursion is handled by the conformance framework; that is, implementations should not call
   * this method on the children of {@code node}.
   */
  protected void checkConformance(T node, ErrorReporter errorReporter) {}
}
