/*
 * Copyright 2018 Google Inc.
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

package com.google.template.soy.passes;

import com.google.common.collect.ImmutableList;

/** A compiler pass */
public interface CompilerPass {

  default ImmutableList<Class<? extends CompilerPass>> runBefore() {
    RunBefore ann = getClass().getAnnotation(RunBefore.class);
    if (ann != null) {
      // TODO(lukes): consider ClassValue if this is slow
      return ImmutableList.copyOf(ann.value());
    }
    return ImmutableList.of();
  }

  default ImmutableList<Class<? extends CompilerPass>> runAfter() {
    RunAfter ann = getClass().getAnnotation(RunAfter.class);
    if (ann != null) {
      // TODO(lukes): consider ClassValue if this is slow
      return ImmutableList.copyOf(ann.value());
    }
    return ImmutableList.of();
  }

  default String name() {
    String localName = getClass().getSimpleName();
    if (localName.endsWith("Pass")) {
      localName = localName.substring(0, localName.length() - "Pass".length());
    }
    return localName;
  }
}
