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

import com.google.common.collect.ImmutableCollection;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.soytree.SoyNode;

/**
 * Container for the result of a parse, including the root of the parse tree
 * and any errors encountered.
 *
 * @param <T> Type of the root of the parse tree. Should be either
 *     {@link com.google.template.soy.soytree.SoyFileSetNode} or
 *     {@link com.google.template.soy.soytree.SoyFileNode}, which are the roots produced by
 *     {@link com.google.template.soy.soyparse.SoyFileSetParser} and
 *     {@link com.google.template.soy.soyparse.SoyFileParser} respectively.
 * @author brndn@google.com (Brendan Linn)
 */
public final class ParseResult<T extends SoyNode> {
  private final T root;
  private final ImmutableCollection<? extends SoySyntaxException> parseErrors;

  /**
   * @param root The root of the parse tree.
   * @param parseErrors Errors encountered during parsing.
   */
  public ParseResult(T root, ImmutableCollection<? extends SoySyntaxException> parseErrors) {
    this.root = root;
    this.parseErrors = parseErrors;
  }

  /**
   * Whether parsing was successful.
   */
  public boolean isSuccess() {
    return parseErrors.isEmpty();
  }

  /**
   * Returns the root of the parse tree.
   */
  public T getParseTree() {
    return root;
  }

  /**
   * Returns the errors encountered during parsing.
   */
  public ImmutableCollection<? extends SoySyntaxException> getParseErrors() {
    return parseErrors;
  }
}
