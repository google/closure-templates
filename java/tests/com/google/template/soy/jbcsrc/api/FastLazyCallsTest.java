/*
 * Copyright 2026 Google Inc.
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

package com.google.template.soy.jbcsrc.api;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableMap;
import com.google.template.soy.SoyFileSet;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests that {@link SoySauce.Renderer#setFastLazyCalls} doesn't change rendered output. */
// Uses the string-based API so this test doesn't depend on generated template classes.
@SuppressWarnings("deprecation")
@RunWith(JUnit4.class)
public final class FastLazyCallsTest {

  private static final String TEMPLATES =
      """
      {namespace ns}

      {template item}
        {@param i: int}
        {@param label: string}
        <li>{$label}-{$i}</li>
      {/template}

      {template wrapper}
        {@param children: html}
        <ul>{$children}{$children}</ul>
      {/template}

      {template staticLazyCalls}
        {@param n: int}
        {call wrapper}
          {param children kind="html"}
            {for $i in range($n)}
              {call item eval="lazy"}{param i: $i /}{param label: 'x' /}{/call}
            {/for}
          {/param}
        {/call}
      {/template}

      {template dynamicLazyCall}
        {call dynamicLazyCallImpl}{param tpl: item /}{/call}
      {/template}

      {template dynamicLazyCallImpl}
        {@param tpl: template (i: int, label: string) => html}
        {call wrapper}
          {param children kind="html"}
            {call $tpl eval="lazy"}{param i: 1 /}{param label: 'y' /}{/call}
          {/param}
        {/call}
      {/template}
      """;

  private static final SoySauce SAUCE =
      SoyFileSet.builder().add(TEMPLATES, "fast_lazy_calls_test.soy").build().compileTemplates();

  private static String render(String template, Map<String, ?> data, boolean fastLazyCalls) {
    return SAUCE
        .renderTemplate(template)
        .setData(data)
        .setFastLazyCalls(fastLazyCalls)
        .renderHtml()
        .get()
        .getContent();
  }

  @Test
  public void staticLazyCalls_sameOutput() {
    var data = ImmutableMap.of("n", 3);
    String expected = render("ns.staticLazyCalls", data, /* fastLazyCalls= */ false);
    assertThat(expected)
        .isEqualTo(
            "<ul><li>x-0</li><li>x-1</li><li>x-2</li><li>x-0</li><li>x-1</li><li>x-2</li></ul>");
    assertThat(render("ns.staticLazyCalls", data, /* fastLazyCalls= */ true)).isEqualTo(expected);
  }

  @Test
  public void dynamicLazyCall_sameOutput() {
    var data = ImmutableMap.<String, Object>of();
    String expected = render("ns.dynamicLazyCall", data, /* fastLazyCalls= */ false);
    assertThat(expected).isEqualTo("<ul><li>y-1</li><li>y-1</li></ul>");
    assertThat(render("ns.dynamicLazyCall", data, /* fastLazyCalls= */ true)).isEqualTo(expected);
  }
}
