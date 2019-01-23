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

package com.google.template.soy.sharedpasses.opti;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.template.soy.SoyFileSetParser.ParseResult;
import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.sharedpasses.render.RenderException;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.testing.TestAnnotations.ExperimentalFeatures;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for PrerenderVisitor.
 *
 */
@RunWith(JUnit4.class)
public class PrerenderVisitorTest {

  private Description testDescription;

  @Rule
  public final TestWatcher testWatcher =
      new TestWatcher() {
        @Override
        protected void starting(Description description) {
          testDescription = description;
        }
      };

  @Test
  public void testPrerenderBasic() throws Exception {
    String templateBody =
        "{let $boo: 8 /}\n"
            + "{$boo}\n"
            + "{if $boo > 4}\n"
            + "  {sp}+ 7 equals {$boo + 7}.\n"
            + "{/if}\n";
    assertThat(prerender(templateBody)).isEqualTo("8 + 7 equals 15.");
  }

  @Test
  public void testPrerenderWithDirectives() throws Exception {
    String printNodesSource =
        "{let $boo: 8 /}\n"
            + "{'aaa+bbb = ccc' |escapeUri}   {sp}\n"
            + "{'0123456789' |truncate:5,true}   {sp}\n"
            + "{'0123456789' |truncate:$boo,false}   {sp}\n"
            + "{'0123456789' |insertWordBreaks:5}   {sp}\n"
            + "{'0123456789' |insertWordBreaks:$boo}   {sp}\n";
    String expectedResult =
        "aaa%2Bbbb%20%3D%20ccc    "
            + "01...    "
            + "01234567    "
            + "01234<wbr>56789    "
            + "01234567<wbr>89    ";
    assertThat(prerender(printNodesSource)).isEqualTo(expectedResult);
  }

  @Test
  public void testPrerenderWithUnsupportedNode() throws Exception {
    // Cannot prerender MsgFallbackGroupNode.
    String templateBody = "{msg desc=\"\"}\n" + "  Hello world.\n" + "{/msg}\n";
    try {
      prerender(templateBody);
      fail();
    } catch (RenderException re) {
      assertThat(re).hasMessageThat().contains("Cannot prerender MsgFallbackGroupNode.");
    }

    // Cannot prerender Debugger.
    templateBody = "{let $boo: 8 /}\n" + "{if $boo > 4}\n" + "{debugger}" + "{/if}\n";
    try {
      prerender(templateBody);
      fail();
    } catch (RenderException re) {
      assertThat(re).hasMessageThat().contains("Cannot prerender DebuggerNode.");
    }

    // This should work because the if-condition is false, thus skipping the DebuggerNode.
    templateBody = "{let $boo: 8 /}\n" + "{$boo}\n" + "{if $boo < 4}\n" + "{debugger}" + "{/if}\n";
    assertThat(prerender(templateBody)).isEqualTo("8");
  }

  @Test
  public void testPrerenderWithUndefinedData() throws Exception {
    String templateBody =
        "{@param foo : ? }\n" + "{let $boo: 8 /}\n" + "{if $boo > 4}\n" + "  {$foo}\n" + "{/if}\n";
    try {
      prerender(templateBody);
      fail();
    } catch (RenderException re) {
      // Test passes.
    }

    // This should work because the if-condition is false, thus skipping the undefined data.
    templateBody =
        "{@param foo : ? }\n"
            + "{let $boo: 8 /}\n"
            + "{$boo}\n"
            + "{if $boo < 4}\n"
            + "  {$foo}\n"
            + "{/if}\n";
    assertThat(prerender(templateBody)).isEqualTo("8");
  }

  @Test
  public void testPrerenderWithDirectiveError() throws Exception {
    try {
      prerender("  {'blah' |bidiSpanWrap}\n");
      fail();
    } catch (Exception e) {
      assertThat(e).isInstanceOf(RenderException.class);
      assertThat(e)
          .hasMessageThat()
          .contains("Cannot prerender a node with some impure print directive.");
    }
  }

  @Test
  public void testPrerenderWithKeyNodeError() throws Exception {
    try {
      prerender("<div {key 'foo'}></div>");
      fail();
    } catch (Exception e) {
      assertThat(e).isInstanceOf(RenderException.class);
      assertThat(e).hasMessageThat().contains("Cannot prerender KeyNode.");
    }
  }

  // -----------------------------------------------------------------------------------------------
  // Helpers.

  /**
   * Renders the given input string (should be a template body) and returns the result.
   *
   * @param input The input string to prerender.
   * @return The rendered result.
   * @throws Exception If there's an error.
   */
  private String prerender(String input) throws Exception {
    ExperimentalFeatures experimentalFeatures =
        testDescription.getAnnotation(ExperimentalFeatures.class);
    ParseResult result =
        SoyFileSetParserBuilder.forTemplateContents(input)
            .enableExperimentalFeatures(
                experimentalFeatures == null
                    ? ImmutableList.of()
                    : ImmutableList.copyOf(experimentalFeatures.value()))
            .parse();

    TemplateNode template = result.fileSet().getChild(0).getChild(0);
    StringBuilder outputSb = new StringBuilder();
    PrerenderVisitor prerenderVisitor =
        new PrerenderVisitor(
            new PreevalVisitorFactory(),
            outputSb,
            ImmutableMap.of(template.getTemplateName(), template));
    prerenderVisitor.exec(template);
    return outputSb.toString();
  }
}
