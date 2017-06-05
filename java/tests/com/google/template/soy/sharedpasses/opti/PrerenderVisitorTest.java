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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Guice;
import com.google.template.soy.SoyFileSetParser.ParseResult;
import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.SoyModule;
import com.google.template.soy.data.SoyValueConverter;
import com.google.template.soy.shared.restricted.SoyJavaPrintDirective;
import com.google.template.soy.sharedpasses.render.RenderException;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for PrerenderVisitor.
 *
 */
@RunWith(JUnit4.class)
public class PrerenderVisitorTest {

  @Test
  public void testPrerenderBasic() throws Exception {

    String templateBody =
        "{let $boo: 8 /}\n"
            + "{$boo}\n"
            + "{if $boo > 4}\n"
            + "  {sp}+ 7 equals {$boo + 7}.\n"
            + "{/if}\n";
    assertEquals("8 + 7 equals 15.", prerender(templateBody));
  }

  @Test
  public void testPrerenderWithDirectives() throws Exception {

    String printNodesSource =
        "{let $boo: 8 /}\n"
            + "{'<b>&</b>' |escapeHtml}   {sp}\n"
            + "{'aaa+bbb = ccc' |escapeUri}   {sp}\n"
            + "{'0123456789' |truncate:5,true}   {sp}\n"
            + "{'0123456789' |truncate:$boo,false}   {sp}\n"
            + "{'0123456789' |escapeHtml |insertWordBreaks:5}   {sp}\n"
            + "{'0123456789' |insertWordBreaks:$boo |escapeHtml}   {sp}\n";
    String expectedResult =
        "&lt;b&gt;&amp;&lt;/b&gt;    "
            + "aaa%2Bbbb%20%3D%20ccc    "
            + "01...    "
            + "01234567    "
            + "01234<wbr>56789    "
            + "01234567&lt;wbr&gt;89    ";
    assertEquals(expectedResult, prerender(printNodesSource));
  }

  @Test
  public void testPrerenderWithUnsupportedNode() throws Exception {

    // Cannot prerender MsgFallbackGroupNode.
    String templateBody = "{msg desc=\"\"}\n" + "  Hello world.\n" + "{/msg}\n";
    try {
      prerender(templateBody);
      fail();
    } catch (RenderException re) {
      assertTrue(re.getMessage().contains("Cannot prerender MsgFallbackGroupNode."));
    }

    // Cannot prerender CssNode.
    templateBody =
        "{let $boo: 8 /}\n"
            + "{if $boo > 4}\n"
            + "  <div class=\"{css foo}\">blah</div>\n"
            + "{/if}\n";
    try {
      prerender(templateBody);
      fail();
    } catch (RenderException re) {
      assertTrue(re.getMessage().contains("Cannot prerender CssNode."));
    }

    // This should work because the if-condition is false, thus skipping the CssNode.
    templateBody =
        "{let $boo: 8 /}\n"
            + "{$boo}\n"
            + "{if $boo < 4}\n"
            + "  <div class=\"{css foo}\">blah</div>\n"
            + "{/if}\n";
    assertEquals("8", prerender(templateBody));
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
    assertEquals("8", prerender(templateBody));
  }

  @Test
  public void testPrerenderWithDirectiveError() throws Exception {

    try {
      prerender("  {'blah' |bidiSpanWrap}\n");
      fail();
    } catch (Exception e) {
      assertTrue(
          e instanceof RenderException
              && e.getMessage()
                  .contains("Cannot prerender a node with some impure print directive."));
    }
  }

  // -----------------------------------------------------------------------------------------------
  // Helpers.

  @Inject ImmutableMap<String, ? extends SoyJavaPrintDirective> soyJavaDirectivesMap;

  @Before
  public void setUp() {
    Guice.createInjector(new SoyModule()).injectMembers(this);
  }
  /**
   * Renders the given input string (should be a template body) and returns the result.
   *
   * @param input The input string to prerender.
   * @return The rendered result.
   * @throws Exception If there's an error.
   */
  private String prerender(String input) throws Exception {
    ParseResult result = SoyFileSetParserBuilder.forTemplateContents(input).parse();

    StringBuilder outputSb = new StringBuilder();
    PrerenderVisitor prerenderVisitor =
        new PrerenderVisitorFactory(
                soyJavaDirectivesMap,
                new PreevalVisitorFactory(SoyValueConverter.UNCUSTOMIZED_INSTANCE))
            .create(outputSb, result.registry());
    prerenderVisitor.exec(result.fileSet().getChild(0).getChild(0));
    return outputSb.toString();
  }
}
