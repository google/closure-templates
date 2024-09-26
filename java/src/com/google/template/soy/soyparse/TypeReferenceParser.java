/*
 * Copyright 2024 Google Inc.
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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SourceFilePath;
import com.google.template.soy.base.internal.TypeReference;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.types.ast.GenericTypeNode;
import com.google.template.soy.types.ast.NamedTypeNode;
import com.google.template.soy.types.ast.TypeNode;
import java.util.function.Function;

/** Produces {@link TypeReference} from strings. */
public class TypeReferenceParser {
  private TypeReferenceParser() {}

  public static Function<String, TypeReference> parser() {
    return TypeReferenceParser::parse;
  }

  public static Function<String, ImmutableList<TypeReference>> listParser() {
    return TypeReferenceParser::parseList;
  }

  static TypeReference parse(String s) {
    // Take advantage of the fact that Soy generic types have the same syntax as the format we
    // expect in {javaimpl}.
    ErrorReporter reporter = ErrorReporter.create();
    TypeNode typeNode =
        SoyFileParser.parseType(escape(s), SourceFilePath.create("unused", "unused"), reporter);

    if (reporter.hasErrors()) {
      // Be lenient here since this will be reported as class not found in ValidateExternsPass.
      return TypeReference.create(s);
    }

    return parse(typeNode);
  }

  private static TypeReference parse(TypeNode node) {
    if (node instanceof NamedTypeNode) {
      return TypeReference.create(unescape(((NamedTypeNode) node).name().identifier()));
    } else if (node instanceof GenericTypeNode) {
      GenericTypeNode genericTypeNode = (GenericTypeNode) node;
      return TypeReference.create(
          unescape(genericTypeNode.name()),
          genericTypeNode.arguments().stream()
              .map(TypeReferenceParser::parse)
              .collect(toImmutableList()));
    } else {
      // Be lenient here since this will be reported as class not found in ValidateExternsPass.
      return TypeReference.create(unescape(node.toString()));
    }
  }

  /**
   * @param s a comma delimited list of types.
   */
  static ImmutableList<TypeReference> parseList(String s) {
    s = s.replaceAll("\\s*,\\s*$", ""); // Allow trailing ',' for backward compatibility.
    TypeReference nested = parse("T<" + s + ">");
    return nested.parameters();
  }

  /** Java allows '$' in class names but Soy types only allow dotted ident. */
  private static String escape(String s) {
    return s.replace("$", "__dolla__");
  }

  private static String unescape(String s) {
    return s.replace("__dolla__", "$");
  }
}
