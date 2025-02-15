/*
 * Copyright 2025 Google Inc.
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

package com.google.template.soy.exprtree;

import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.basetree.CopyState;

/**
 * Node representing a Soy integer value. Note that Soy supports up to JavaScript
 * +-Number.MAX_SAFE_INTEGER at the least; Java and Python backends support full 64 bit longs.
 */
public abstract class NumberNode extends AbstractPrimitiveNode {

  public NumberNode(SourceLocation sourceLocation) {
    super(sourceLocation);
  }

  protected NumberNode(NumberNode orig, CopyState copyState) {
    super(orig, copyState);
  }

  public abstract double doubleValue();

  public abstract long longValue();

  public abstract int intValue();
}
