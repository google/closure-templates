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

import com.google.common.collect.Multimap;
import com.google.template.soy.passes.IndirectParamsCalculator.IndirectParamsInfo;
import com.google.template.soy.soytree.FileSetMetadata;
import com.google.template.soy.soytree.TemplateMetadata;
import com.google.template.soy.testing.SoyFileSetParserBuilder;
import com.google.template.soy.types.TemplateType.Parameter;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for IndirectParamsCalculator.
 */
@RunWith(JUnit4.class)
public final class IndirectParamsCalculatorTest {

  @Test
  public void testFindIndirectParams() {

    String alpha =
        "{namespace alpha}\n"
            + "import * as beta from 'no-path-2';\n"
            + "{template zero}\n"
            + "  {@param? a0: ?}\n"
            + "  {@param? b3: ?}\n" // 'b3' listed by alpha.zero
            + "  {call zero data=\"all\" /}\n"
            + "  {call one data=\"all\" /}\n" // recursive call should not cause 'a0' to be added
            + "  {call two /}\n"
            + "  {call five data=\"all\"}\n"
            + "    {param a5: $a0 /}\n"
            + "    {param b2: 88 /}\n"
            + "  {/call}\n"
            + "{/template}\n"
            + "\n"
            + "{template one}\n"
            + "  {@param? a1: ?}\n"
            + "  {call three data=\"all\" /}\n"
            + "  {call four /}\n"
            + "  {$a1}\n"
            + "{/template}\n"
            + "\n"
            + "{template two}\n"
            + "  {@param? a2: ?}\n"
            + "  {$a2}\n"
            + "{/template}\n"
            + "\n"
            + "{template three}\n"
            + "  {@param? a3: ?}\n"
            + "  {call beta.one data=\"all\" /}\n"
            + "  {$a3}\n"
            + "{/template}\n"
            + "\n"
            + "{template four}\n"
            + "  {@param? a4: ?}\n"
            + "  {$a4}\n"
            + "{/template}\n"
            + "\n"
            + "{template five}\n"
            + "  {@param? a5: ?}\n"
            + "  {@param? b4: ?}\n" // 'b4' listed by alpha.five
            + "  {call beta.two data=\"all\" /}\n"
            + "  {call beta.three data=\"all\" /}\n"
            + "  {call beta.four data=\"all\" /}\n"
            + "  {$b4}\n"
            + "  {$a5}\n"
            + "{/template}\n";

    String beta =
        "{namespace beta}\n"
            + "import * as gamma from 'no-path-3';\n"
            + "{template zero}\n"
            + "  {@param? b0: ?}\n"
            + "  {$b0}\n"
            + "{/template}\n"
            + "\n"
            + "{template one}\n"
            + "  {@param? b1: ?}\n"
            + "  {call gamma.six data=\"all\" /}\n"
            + "  {$b1}\n"
            + "{/template}\n"
            + "\n"
            + "{template two}\n"
            + "  {@param? b2: ?}\n"
            + "  {$b2}\n"
            + "{/template}\n"
            + "\n"
            + "{template three}\n"
            + "  {@param? b3: ?}\n"
            + "  {$b3}\n"
            + "{/template}\n"
            + "\n"
            + "{template four}\n"
            + "  {@param? b4: ?}\n"
            + "  {$b4}\n"
            + "{/template}\n";

    String gamma =
        "{namespace gamma}\n"
            + "{template six}\n"
            + "  {@param? a6: ?}\n"
            + "  {$a6}\n"
            + "{/template}\n";

    FileSetMetadata registry =
        SoyFileSetParserBuilder.forFileContents(alpha, beta, gamma).parse().registry();

    TemplateMetadata a0 = registry.getBasicTemplateOrElement("alpha.zero");
    TemplateMetadata a1 = registry.getBasicTemplateOrElement("alpha.one");
    // TemplateMetadata a2 = registry.getBasicTemplateOrElement("alpha.two");
    TemplateMetadata a3 = registry.getBasicTemplateOrElement("alpha.three");
    // TemplateMetadata a4 = registry.getBasicTemplateOrElement("alpha.four");
    TemplateMetadata a5 = registry.getBasicTemplateOrElement("alpha.five");
    TemplateMetadata a6 = registry.getBasicTemplateOrElement("gamma.six");
    TemplateMetadata b1 = registry.getBasicTemplateOrElement("beta.one");
    // TemplateMetadata b2 = registry.getBasicTemplateOrElement("beta.two");
    TemplateMetadata b3 = registry.getBasicTemplateOrElement("beta.three");
    TemplateMetadata b4 = registry.getBasicTemplateOrElement("beta.four");

    IndirectParamsInfo ipi =
        new IndirectParamsCalculator(registry).calculateIndirectParams(a0.getTemplateType());
    assertThat(ipi.mayHaveIndirectParamsInExternalCalls).isFalse();

    Map<String, Parameter> ipMap = ipi.indirectParams;
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
    assertThat(ipMap).hasSize(6);

    Multimap<String, TemplateMetadata> pktcm = ipi.paramKeyToCalleesMultimap;
    assertThat(pktcm).valuesForKey("a1").containsExactly(a1);
    assertThat(pktcm).valuesForKey("a3").containsExactly(a3);
    assertThat(pktcm).valuesForKey("a6").containsExactly(a6);
    assertThat(pktcm).valuesForKey("b1").containsExactly(b1);
    assertThat(pktcm).valuesForKey("b3").containsExactly(b3);
    // 'b4' listed by alpha.five
    assertThat(pktcm).valuesForKey("b4").containsExactly(a5, b4);
  }

  @Test
  public void testFindIndirectParamsModifiables() {

    String fileContent =
        "{namespace ns}"
            + "{template test}{call modifiable data=\"all\" /}{/template}"
            + "{template modifiable modifiable=\"true\" usevarianttype=\"string\"}"
            + "  {@param m: ?}"
            + "  {call x data=\"all\" /}"
            + "{/template}"
            + "{template variant visibility=\"private\" modifies=\"modifiable\" variant=\"'foo'\"}"
            + "  {@param m: ?}"
            + "  {call y data=\"all\" /}"
            + "{/template}"
            + "{template x}{@param x: ?}{/template}"
            + "{template y}{@param y: ?}{/template}";

    FileSetMetadata registry =
        SoyFileSetParserBuilder.forFileContents(fileContent).parse().registry();

    TemplateMetadata test = registry.getBasicTemplateOrElement("ns.test");
    IndirectParamsInfo ipi =
        new IndirectParamsCalculator(registry).calculateIndirectParams(test.getTemplateType());
    assertThat(ipi.indirectParams.keySet()).containsExactly("m", "x", "y");
  }

  @Test
  public void testFindIndirectParamsModifiablesWithLegacyNamespace() {

    String fileContent =
        "{namespace ns}"
            + "{template test}{call modifiable data=\"all\" /}{/template}"
            + "{template test2}{delcall legacy data=\"all\" /}{/template}"
            + "{template modifiable modifiable=\"true\" usevarianttype=\"string\""
            + "     legacydeltemplatenamespace=\"legacy\"}"
            + "  {@param m: ?}"
            + "  {call x data=\"all\" /}"
            + "{/template}"
            + "{template variant visibility=\"private\" modifies=\"modifiable\" variant=\"'foo'\"}"
            + "  {@param m: ?}"
            + "  {call y data=\"all\" /}"
            + "{/template}"
            + "{template x}{@param x: ?}{/template}"
            + "{template y}{@param y: ?}{/template}"
            + "{deltemplate legacy variant=\"'bar'\"}{@param z: ?}{/deltemplate}";

    FileSetMetadata registry =
        SoyFileSetParserBuilder.forFileContents(fileContent).parse().registry();

    TemplateMetadata test = registry.getBasicTemplateOrElement("ns.test");
    IndirectParamsInfo ipi =
        new IndirectParamsCalculator(registry).calculateIndirectParams(test.getTemplateType());
    assertThat(ipi.indirectParams.keySet()).containsExactly("m", "x", "y", "z");

    TemplateMetadata test2 = registry.getBasicTemplateOrElement("ns.test2");
    IndirectParamsInfo ipi2 =
        new IndirectParamsCalculator(registry).calculateIndirectParams(test2.getTemplateType());
    assertThat(ipi2.indirectParams.keySet()).containsExactly("m", "x", "y", "z");
  }
}
