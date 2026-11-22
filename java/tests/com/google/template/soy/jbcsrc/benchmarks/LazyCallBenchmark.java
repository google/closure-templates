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

package com.google.template.soy.jbcsrc.benchmarks;

import com.google.caliper.BeforeExperiment;
import com.google.caliper.Benchmark;
import com.google.caliper.Param;
import com.google.caliper.api.VmOptions;
import com.google.common.collect.ImmutableMap;
import com.google.template.soy.SoyFileSet;
import com.google.template.soy.jbcsrc.api.SoySauce;
import java.io.IOException;

/**
 * Measures the cost of lazy {@code {call}}s inside html params, which are rendered through {@code
 * NodeBuilder.render}.
 */
@VmOptions({"-Xms4g", "-Xmx4g"})
public class LazyCallBenchmark {

  private static final String TEMPLATES =
      """
      {namespace bench}

      {template item}
        {@param i: int}
        <p>{$i}</p>
      {/template}

      {template wrapper}
        {@param children: html}
        <div>{$children}</div>
      {/template}

      {template root}
        {@param items: int}
        {call wrapper}
          {param children kind="html"}
            {for $i in range($items)}
              {call item eval="lazy"}{param i: $i /}{/call}
            {/for}
          {/param}
        {/call}
      {/template}
      """;

  private static final SoySauce SAUCE =
      SoyFileSet.builder().add(TEMPLATES, "lazy_call_benchmark.soy").build().compileTemplates();

  @Param({"1", "50"})
  int items;

  @Param({"false", "true"})
  boolean fastLazyCalls;

  private ImmutableMap<String, Object> data;
  private final StringBuilder out = new StringBuilder();

  @BeforeExperiment
  void setUp() throws IOException {
    data = ImmutableMap.of("items", items);
    var unused = renderOnce();
  }

  private int renderOnce() throws IOException {
    var unused =
        SAUCE
            .renderTemplate("bench.root")
            .setData(data)
            .setFastLazyCalls(fastLazyCalls)
            .renderHtml(out);
    int len = out.length();
    out.setLength(0);
    return len;
  }

  @Benchmark
  int render(int reps) throws IOException {
    int accum = 0;
    for (int i = 0; i < reps; i++) {
      accum += renderOnce();
    }
    return accum;
  }
}
