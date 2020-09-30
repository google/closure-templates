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
package com.google.template.soy.jbcsrc;

import com.google.template.soy.jbcsrc.restricted.Statement;

/**
 * A simple way to add an extra statement to the SoyNodeCompiler.
 *
 * <p>TODO(lukes): this is a pretty terrible name...
 */
interface ExtraCodeCompiler {
  ExtraCodeCompiler NO_OP =
      new ExtraCodeCompiler() {
        @Override
        public Statement compile(
            ExpressionCompiler exprCompiler,
            AppendableExpression appendable,
            DetachState detachState) {
          return Statement.NULL_STATEMENT;
        }
      };

  Statement compile(
      ExpressionCompiler exprCompiler, AppendableExpression appendable, DetachState detachState);
}
