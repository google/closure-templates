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

package com.google.template.soy.data;

import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.internal.RenderableThunk;

/**
 * An internal only class to allow the render package to create lazy versions of SanitizedContent.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public final class LazySanitizedContents {

  /** Creates a SanitizedContent that wraps the given thunk. */
  public static SanitizedContent forThunk(RenderableThunk value, ContentKind kind) {
    return SanitizedContent.createLazy(value, kind, SanitizedContents.getDefaultDir(kind));
  }

  private LazySanitizedContents() {}
}
