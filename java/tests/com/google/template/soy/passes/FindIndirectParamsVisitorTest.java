/*
 * Copyright 2009 Google Inc.
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

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.ExplodingErrorReporter;
import com.google.template.soy.passes.FindIndirectParamsVisitor.IndirectParamsInfo;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.TemplateRegistry;
import com.google.template.soy.soytree.defn.TemplateParam;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for FindIndirectParamsVisitor.
 *
 */
@RunWith(JUnit4.class)
public final class FindIndirectParamsVisitorTest {

  @Test
  public void testFindIndirectParams() {

    String fileContent1 =
        "{namespace alpha autoescape=\"deprecated-noncontextual\"}\n"
            + "\n"
            + "/** @param? a0 @param? b3 */\n"
            + // 'b3' listed by alpha.zero
            "{template .zero}\n"
            + "  {call .zero data=\"all\" /}\n"
            + // recursive call should not cause 'a0' to be added
            "  {call .one data=\"all\" /}\n"
            + "  {call .two /}\n"
            + "  {call beta.zero /}\n"
            + "  {call .five data=\"all\"}\n"
            + "    {param a5: $a0 /}\n"
            + "    {param b2: 88 /}\n"
            + "  {/call}\n"
            + "{/template}\n"
            + "\n"
            + "/** @param? a1 */\n"
            + "{template .one}\n"
            + "  {call .three data=\"all\" /}\n"
            + "  {call .four /}\n"
            + "  {$a1}\n"
            + "{/template}\n"
            + "\n"
            + "/** @param? a2 */\n"
            + "{template .two}\n"
            + "  {$a2}\n"
            + "{/template}\n"
            + "\n"
            + "/** @param? a3 */\n"
            + "{template .three}\n"
            + "  {call beta.one data=\"all\" /}\n"
            + "  {$a3}\n"
            + "{/template}\n"
            + "\n"
            + "/** @param? a4 */\n"
            + "{template .four}\n"
            + "  {call external.one /}\n"
            + "  {$a4}\n"
            + "{/template}\n"
            + "\n"
            + "/** @param? a5 @param? b4 */\n"
            + // 'b4' listed by alpha.five
            "{template .five}\n"
            + "  {call beta.two data=\"all\" /}\n"
            + "  {call beta.three data=\"all\" /}\n"
            + "  {call beta.four data=\"all\" /}\n"
            + "  {$b4}\n"
            + "  {$a5}\n"
            + "{/template}\n"
            + "\n"
            + "/** @param? a6 */\n"
            + "{template .six}\n"
            + "  {$a6}\n"
            + "{/template}\n";

    String fileContent2 =
        "{namespace beta autoescape=\"deprecated-noncontextual\"}\n"
            + "\n"
            + "/** @param? b0 */\n"
            + "{template .zero}\n"
            + "  {$b0}\n"
            + "{/template}\n"
            + "\n"
            + "/** @param? b1 */\n"
            + "{template .one}\n"
            + "  {call alpha.six data=\"all\" /}\n"
            + "  {$b1}\n"
            + "{/template}\n"
            + "\n"
            + "/** @param? b2 */\n"
            + "{template .two}\n"
            + "  {$b2}\n"
            + "{/template}\n"
            + "\n"
            + "/** @param? b3 */\n"
            + "{template .three}\n"
            + "  {$b3}\n"
            + "{/template}\n"
            + "\n"
            + "/** @param? b4 */\n"
            + "{template .four}\n"
            + "  {$b4}\n"
            + "{/template}\n";

    ErrorReporter boom = ExplodingErrorReporter.get();
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(fileContent1, fileContent2)
            .errorReporter(boom)
            .parse()
            .fileSet();

    TemplateRegistry registry = new TemplateRegistry(soyTree, boom);

    SoyFileNode a = soyTree.getChild(0);
    TemplateNode a0 = a.getChild(0);
    TemplateNode a1 = a.getChild(1);
    //TemplateNode a2 = a.getChild(2);
    TemplateNode a3 = a.getChild(3);
    //TemplateNode a4 = a.getChild(4);
    TemplateNode a5 = a.getChild(5);
    TemplateNode a6 = a.getChild(6);
    SoyFileNode b = soyTree.getChild(1);
    //TemplateNode b0 = b.getChild(0);
    TemplateNode b1 = b.getChild(1);
    //TemplateNode b2 = b.getChild(2);
    TemplateNode b3 = b.getChild(3);
    TemplateNode b4 = b.getChild(4);

    IndirectParamsInfo ipi = new FindIndirectParamsVisitor(registry).exec(a0);
    assertThat(ipi.mayHaveIndirectParamsInExternalCalls).isFalse();
    assertThat(ipi.mayHaveIndirectParamsInExternalDelCalls).isFalse();

    Map<String, TemplateParam> ipMap = ipi.indirectParams;
    assertThat(ipMap).hasSize(6);
    assertThat(ipMap).doesNotContainKey("a0");
    assertThat(ipMap).containsKey("a1");
    assertThat(ipMap).doesNotContainKey("a2");
    assertThat(ipMap).containsKey("a3");
    assertThat(ipMap).doesNotContainKey("a4");
    assertThat(ipMap).doesNotContainKey("a5");
    assertThat(ipMap).containsKey("a6");
    assertThat(ipMap).doesNotContainKey("b0");
    assertThat(ipMap).containsKey("b1");
    assertThat(ipMap).doesNotContainKey("b2");
    assertThat(ipMap).containsKey("b3");
    assertThat(ipMap).containsKey("b4");

    Multimap<String, TemplateNode> pktcm = ipi.paramKeyToCalleesMultimap;
    assertThat(pktcm).valuesForKey("a1").isEqualTo(ImmutableSet.of(a1));
    assertThat(pktcm).valuesForKey("a3").isEqualTo(ImmutableSet.of(a3));
    assertThat(pktcm).valuesForKey("a6").isEqualTo(ImmutableSet.of(a6));
    assertThat(pktcm).valuesForKey("b1").isEqualTo(ImmutableSet.of(b1));
    assertThat(pktcm).valuesForKey("b3").isEqualTo(ImmutableSet.of(b3));
    // 'b4' listed by alpha.five
    assertThat(pktcm).valuesForKey("b4").isEqualTo(ImmutableSet.of(a5, b4));
  }
}
