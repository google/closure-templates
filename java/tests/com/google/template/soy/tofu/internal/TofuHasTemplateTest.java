package com.google.template.soy.tofu.internal;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.shared.internal.NoOpScopedData;
import com.google.template.soy.tofu.SoyTofu;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static org.junit.Assert.*;


/** Unit tests for `hasTemplate` method of {@link SoyTofu}. */
@RunWith(JUnit4.class)
public class TofuHasTemplateTest {
  private static final String SOY_FILE =
    Joiner.on('\n')
          .join(
            "{namespace sample}",
            "",
            "/** */",
            "{template .example}",
            "  hello world",
            "{/template}");

  private SoyTofu tofu;

  @Before
  public void setUp() throws Exception {
    tofu =
      new BaseTofu(
        new NoOpScopedData(),
        SoyFileSetParserBuilder.forFileContents(SOY_FILE).parse().fileSet(),
        ImmutableMap.of());
  }

  @Test
  public void testHasTemplate() throws Exception {
    assertTrue("SoyTofu should report `true` for `hasTemplate` call with valid template",
      tofu.hasTemplate("sample.example"));
    assertFalse("SoyTofu should report `false` for `hasTemplate` call with unrecognized template",
      tofu.hasTemplate("some.unrecognized.path"));
  }
}
