/*
 * Copyright 2008 Google Inc.
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

package com.google.template.soy.soytree;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;

import com.google.common.base.Joiner;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.IncrementingIdGenerator;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.soyparse.SoyFileParser;
import java.io.StringReader;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for RawTextNode.
 *
 */
@RunWith(JUnit4.class)
public final class RawTextNodeTest {

  @Test
  public void testToSourceString() {
    RawTextNode rtn = new RawTextNode(0, "Aa`! \n \r \t { }", SourceLocation.UNKNOWN);
    assertEquals("Aa`! {\\n} {\\r} {\\t} {lb} {rb}", rtn.toSourceString());
  }

  @Test
  public void testTabCharNode() {
    TemplateNode template = parseTemplate("{\\t}");

    RawTextNode charNode = (RawTextNode) template.getChild(0);
    assertThat(charNode.toSourceString()).isEqualTo("{\\t}");
    assertThat(charNode.getRawText()).isEqualTo("\t");
    assertThat(charNode.isCommandCharacter()).isTrue();
  }

  @Test
  public void testSpCharNode() {
    TemplateNode template = parseTemplate("{sp}");

    RawTextNode charNode = (RawTextNode) template.getChild(0);
    assertThat(charNode.toSourceString()).isEqualTo("{sp}");
    assertThat(charNode.getRawText()).isEqualTo(" ");
  }

  @Test
  public void testNilCharNode() {
    TemplateNode template = parseTemplate("{nil}");

    RawTextNode charNode = (RawTextNode) template.getChild(0);
    assertThat(charNode.toSourceString()).isEqualTo("{nil}");
    assertThat(charNode.getRawText()).isEmpty();
    assertThat(charNode.isEmpty()).isTrue();
    assertThat(charNode.isNilCommandChar()).isTrue();
  }

  @Test
  public void testCarriageReturnCharNode() {
    TemplateNode template = parseTemplate("{\\r}");

    RawTextNode charNode = (RawTextNode) template.getChild(0);
    assertThat(charNode.toSourceString()).isEqualTo("{\\r}");
    assertThat(charNode.getRawText()).isEqualTo("\r");
  }

  @Test
  public void testNewlineCharNode() {
    TemplateNode template = parseTemplate("{\\n}");

    RawTextNode charNode = (RawTextNode) template.getChild(0);
    assertThat(charNode.toSourceString()).isEqualTo("{\\n}");
    assertThat(charNode.getRawText()).isEqualTo("\n");
  }

  @Test
  public void testLeftBraceCharNode() {
    TemplateNode template = parseTemplate("{lb}");

    RawTextNode charNode = (RawTextNode) template.getChild(0);
    assertThat(charNode.toSourceString()).isEqualTo("{lb}");
    assertThat(charNode.getRawText()).isEqualTo("{");
  }

  @Test
  public void testRightBraceCharNode() {
    TemplateNode template = parseTemplate("{rb}");

    RawTextNode charNode = (RawTextNode) template.getChild(0);
    assertThat(charNode.toSourceString()).isEqualTo("{rb}");
    assertThat(charNode.getRawText()).isEqualTo("}");
  }

  @Test
  public void testNbspCharNode() {
    TemplateNode template = parseTemplate("{nbsp}");

    RawTextNode charNode = (RawTextNode) template.getChild(0);
    assertThat(charNode.toSourceString()).isEqualTo("{nbsp}");
    assertThat(charNode.getRawText()).isEqualTo("\u00A0");
  }

  /** Parses the given input as a template content. */
  private static TemplateNode parseTemplate(String input) {
    String soyFile =
        Joiner.on('\n')
            .join("{namespace ns}", "", "{template .t stricthtml=\"false\"}", input, "{/template}");
    SoyFileNode node =
        new SoyFileParser(
                new IncrementingIdGenerator(),
                new StringReader(soyFile),
                "test.soy",
                ErrorReporter.exploding())
            .parseSoyFile();
    if (node != null) {
      return (TemplateNode) node.getChild(0);
    }
    return null;
  }
}
