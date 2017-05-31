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

package com.google.template.soy.html;

/**
 * The namespaces that Elements can be in, excluding MathML. Used by {@link HtmlTransformVisitor} to
 * keep track of the current element namespace. This allows it to handle self-closing tags somewhat
 * correctly. Treat as package-private.
 */
public enum InferredElementNamespace {
  SVG,
  XHTML
}
