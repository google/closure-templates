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
package com.google.template.soy.jbcsrc.shared;

import static com.google.common.truth.Truth.assertThat;

import com.google.template.soy.data.LoggingAdvisingAppendable;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SanitizedContents;
import com.google.template.soy.data.internal.ParamStore;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import javax.annotation.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class CompiledTemplateMetaFactoryTest {

  @TemplateMetadata(contentKind = SanitizedContent.ContentKind.HTML)
  static CompiledTemplate renderMethod() throws Throwable {
    return null;
  }

  @Nullable
  static StackFrame renderMethod(
      @Nullable StackFrame frame,
      ParamStore params,
      LoggingAdvisingAppendable appendable,
      RenderContext renderContext)
      throws IOException {
    appendable.append("hello");
    return null;
  }

  @Test
  public void tagsWithContentKind() throws Throwable {
    var t =
        (CompiledTemplate)
            CompiledTemplateMetaFactory.createCompiledTemplate(
                MethodHandles.lookup(), "renderMethod", CompiledTemplate.class);
    var buffer = LoggingAdvisingAppendable.buffering();
    assertThat(t.render(null, null, buffer, null)).isNull();
    assertThat(buffer.getAsSoyValue()).isEqualTo(SanitizedContents.constantHtml("hello"));
  }
}
