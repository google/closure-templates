/*
 * Copyright 2015 Google Inc.
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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.msgs.SoyMsgBundle;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for SoyMsgBundle.
 *
 */
@RunWith(JUnit4.class)
public class SoyMsgBundleImplTest {

  @Test
  public void testBasic() {

    List<SoyMsg> inMsgs = Lists.newArrayList();
    SourceLocation source1 = new SourceLocation("/path/to/source1", 10, 1, 10, 10);
    inMsgs.add(
        SoyMsg.builder()
            .setId(0x123)
            .setLocaleString("x-zz")
            .setDesc("Boo message.")
            .setSourceLocation(source1)
            .setParts(ImmutableList.<SoyMsgPart>of(SoyMsgRawTextPart.of("Boo!")))
            .build());
    inMsgs.add(
        SoyMsg.builder()
            .setId(0xABC)
            .setLocaleString("x-zz")
            .setMeaning("abc")
            .setDesc("")
            .setIsHidden(true)
            .setContentType("text/html")
            .setParts(
                ImmutableList.<SoyMsgPart>of(
                    SoyMsgRawTextPart.of("Hello, "),
                    new SoyMsgPlaceholderPart("NAME"),
                    SoyMsgRawTextPart.of("!")))
            .build());
    SourceLocation source2 = new SourceLocation("/path/to/source2", 20, 1, 20, 10);
    inMsgs.add(
        SoyMsg.builder()
            .setId(0x123)
            .setLocaleString("x-zz")
            .setDesc("Boo message 2.")
            .setIsHidden(false)
            .setSourceLocation(source2)
            .setParts(ImmutableList.<SoyMsgPart>of(SoyMsgRawTextPart.of("Boo 2!")))
            .build());
    SoyMsgBundle msgBundle = new SoyMsgBundleImpl("x-zz", inMsgs);

    assertEquals("x-zz", msgBundle.getLocaleString());
    assertThat(msgBundle.isRtl()).isFalse();
    assertEquals(2, msgBundle.getNumMsgs());

    List<SoyMsg> outMsgs = Lists.newArrayList();
    for (SoyMsg msg : msgBundle) {
      outMsgs.add(msg);
    }
    assertEquals(2, outMsgs.size());

    SoyMsg booMsg = msgBundle.getMsg(0x123);
    assertEquals(booMsg, outMsgs.get(0));
    assertEquals(0x123, booMsg.getId());
    assertEquals("x-zz", booMsg.getLocaleString());
    assertEquals(null, booMsg.getMeaning());
    assertEquals("Boo message.", booMsg.getDesc());
    assertEquals(false, booMsg.isHidden());
    assertEquals(null, booMsg.getContentType());
    assertEquals(2, booMsg.getSourceLocations().size());
    assertTrue(booMsg.getSourceLocations().containsAll(Lists.newArrayList(source1, source2)));
    List<SoyMsgPart> booMsgParts = booMsg.getParts();
    assertEquals(1, booMsgParts.size());
    assertEquals("Boo!", ((SoyMsgRawTextPart) booMsgParts.get(0)).getRawText());

    SoyMsg helloMsg = msgBundle.getMsg(0xABC);
    assertEquals(helloMsg, outMsgs.get(1));
    assertEquals(0xABC, helloMsg.getId());
    assertEquals("x-zz", helloMsg.getLocaleString());
    assertEquals("abc", helloMsg.getMeaning());
    assertEquals("", helloMsg.getDesc());
    assertEquals(true, helloMsg.isHidden());
    assertEquals("text/html", helloMsg.getContentType());
    assertEquals(0, helloMsg.getSourceLocations().size());
    List<SoyMsgPart> helloMsgParts = helloMsg.getParts();
    assertEquals(3, helloMsgParts.size());
    assertEquals("Hello, ", ((SoyMsgRawTextPart) helloMsgParts.get(0)).getRawText());
    assertEquals("NAME", ((SoyMsgPlaceholderPart) helloMsgParts.get(1)).getPlaceholderName());
    assertEquals("!", ((SoyMsgRawTextPart) helloMsgParts.get(2)).getRawText());
  }

  @Test
  public void testIsRtl() {
    assertThat(new SoyMsgBundleImpl("ar", ImmutableList.<SoyMsg>of()).isRtl()).isTrue();
    assertThat(new SoyMsgBundleImpl("iw", ImmutableList.<SoyMsg>of()).isRtl()).isTrue();
    assertThat(new SoyMsgBundleImpl("fr", ImmutableList.<SoyMsg>of()).isRtl()).isFalse();
    assertThat(new SoyMsgBundleImpl("en", ImmutableList.<SoyMsg>of()).isRtl()).isFalse();
  }
}
