/*
 * Copyright 2017 Google Inc.
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

import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.soytree.TemplateNode.SoyFileHeaderInfo;

/** Shared utilities for the 'soyparse' package. */
final class SoyParseUtils {

  private static final SoyErrorKind CALL_COLLIDES_WITH_NAMESPACE_ALIAS =
      SoyErrorKind.of("Call collides with namespace alias ''{0}''.");

  /** Given a template call and file header info, return the expanded callee name if possible. */
  @SuppressWarnings("unused") // called in SoyFileParser.jj
  public static final String calculateFullCalleeName(
      Identifier ident, SoyFileHeaderInfo header, ErrorReporter errorReporter) {

    String name = ident.identifier();
    switch (ident.type()) {
      case DOT_IDENT:
        // Case 1: Source callee name is partial.
        return header.namespace + name;
      case DOTTED_IDENT:
        // Case 2: Source callee name is a proper dotted ident.
        int firstDot = name.indexOf('.');
        String alias = header.aliasToNamespaceMap.get(name.substring(0, firstDot));

        // Case 2a: Source callee name's first part is an alias.
        if (alias != null) {
          return alias + name.substring(firstDot);
        }

        // Case 2b: Source callee name's first part is not an alias.
        return name;
      case SINGLE_IDENT:
        // Case 3: Source callee name is a single ident (not dotted).
        if (header.aliasToNamespaceMap.containsKey(name)) {
          errorReporter.report(ident.location(), CALL_COLLIDES_WITH_NAMESPACE_ALIAS, name);
        }
        return name;
      default:
        throw new AssertionError();
    }
  }
}
