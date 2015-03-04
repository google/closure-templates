/*
 * Copyright 2015 Google Inc.
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

package com.google.template.soy.soyparse;

import com.google.template.soy.soytree.MsgPluralNode;
import com.google.template.soy.soytree.XidNode;

/**
 * Fake {@link com.google.template.soy.soytree.SoyNode} instances for use in error handling.
 *
 * <p>Traditionally, Soy has thrown Java exceptions for all kinds of errors (lexical, syntactic,
 * internal bugs, ...). This meant that Soy could display at most one error per run.
 *
 * <p>In contrast, compilers usually try to continue when faced with an error, with the goal
 * of presenting a complete and useful error report to the programmer. Soy is moving to this
 * approach.
 *
 * <p>To continue parsing when possible, {@link TemplateParser} inserts the nodes below into
 * the parse tree in place of malformed nodes of the same type.
 *
 *
 * @author brndn@google.com (Brendan Linn)
 */
final class ErrorNodes {
  private ErrorNodes() {}

  static final MsgPluralNode MSG_PLURAL_NODE = new MsgPluralNode(-1, "plural");
  static final XidNode XID_NODE = new XidNode(-1, "error");
}
