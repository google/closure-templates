/*
 * Copyright 2020 Google Inc.
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

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;

/**
 * Documents the passes this pass should run before.
 *
 * <p>Use this when you rely on the given pass performing some kind of mutation on the AST structure
 * you are producing. For example, a desugaring pass which creates new ExprNodes might want to run
 * before type checking so it doesn't have to populate type fields.
 */
@Retention(RUNTIME)
@interface RunBefore {
  Class<? extends CompilerPass>[] value();
}
