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

package com.google.template.soy.msgs.restricted;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SourceFilePath;
import com.google.template.soy.base.SourceLocation;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test for {@link SoyMsg}. */
@RunWith(JUnit4.class)
public final class SoyMsgTest {

  private static final SourceLocation SOURCE =
      new SourceLocation(SourceFilePath.create("/path/to/source1"), 10, 1, 10, 10);
  private static final String TEMPLATE = "ns.foo.templates.tmpl";

  private static final SoyMsg MSG =
      SoyMsg.builder()
          .setId(2222)
          .setAlternateId(123456)
          .setLocaleString("de-DE")
          .setDesc("Fake description")
          .setMeaning("Fake meaning")
          .setIsHidden(true)
          .setContentType("html")
          .setIsPlrselMsg(true)
          .addSourceLocation(SOURCE, TEMPLATE)
          .setParts(ImmutableList.<SoyMsgPart>of(SoyMsgRawTextPart.of("Boo!")))
          .build();

  private static final SoyMsg MSG_MINIMAL =
      SoyMsg.builder()
          .setId(2222)
          .setParts(ImmutableList.<SoyMsgPart>of(SoyMsgRawTextPart.of("Boo!")))
          .build();

  @Test
  public void toBuilder_unchangedCopy() {
    assertThat(MSG.toBuilder().build()).isEqualTo(MSG);
  }

  @Test
  public void toBuilder_unchangedCopy_minimal() {
    assertThat(MSG_MINIMAL.toBuilder().build()).isEqualTo(MSG_MINIMAL);
  }

  @Test
  public void toBuilder_modify() {
    assertThat(
            MSG_MINIMAL.toBuilder()
                .setAlternateId(123456)
                .setLocaleString("de-DE")
                .setDesc("Fake description")
                .setMeaning("Fake meaning")
                .setIsHidden(true)
                .setContentType("html")
                .setIsPlrselMsg(true)
                .addSourceLocation(SOURCE, TEMPLATE)
                .build())
        .isEqualTo(MSG);
  }
}
