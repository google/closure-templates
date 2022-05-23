/*
 * Copyright 2021 Google Inc.
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

package com.google.template.soy.plugin.java;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import java.util.List;

/** Interface for validating a Java method. */
public interface MethodChecker {

  /** Classification for {@link Response}. */
  enum Code {
    EXISTS,
    NO_SUCH_CLASS,
    NO_SUCH_METHOD_NAME,
    NO_SUCH_METHOD_SIG,
    NO_SUCH_RETURN_TYPE,
    NOT_PUBLIC
  }

  /** Response object for {@link #findMethod}. */
  class Response {
    private final Code code;
    private final ReadMethodData method;
    private final ImmutableCollection<String> suggestions;

    public static Response error(Code code) {
      return new Response(code, null, ImmutableList.of());
    }

    public static Response error(Code code, ImmutableCollection<String> suggestions) {
      return new Response(code, null, suggestions);
    }

    public static Response success(ReadMethodData data) {
      return new Response(Code.EXISTS, data, ImmutableList.of());
    }

    private Response(Code code, ReadMethodData method, ImmutableCollection<String> suggestions) {
      this.code = code;
      this.method = method;
      this.suggestions = suggestions;
    }

    public Code getCode() {
      return code;
    }

    public ReadMethodData getMethod() {
      return method;
    }

    public ImmutableCollection<String> getSuggesions() {
      return suggestions;
    }
  }

  Response findMethod(
      String className, String methodName, String returnType, List<String> arguments);
}
