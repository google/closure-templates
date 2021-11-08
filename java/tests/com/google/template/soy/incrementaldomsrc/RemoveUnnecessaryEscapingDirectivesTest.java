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
package com.google.template.soy.incrementaldomsrc;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Joiner;
import com.google.template.soy.internal.i18n.SoyBidiUtils;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.testing.SoyFileSetParserBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class RemoveUnnecessaryEscapingDirectivesTest {

  private static final String CSP_NONCE =
      "  {@inject? csp_nonce: any}  /** Created by ContentSecurityPolicyNonceInjectionPass. */";

  @Test
  public void testRemoveEscapeHtml() throws Exception {
    template(join("  {@param p: ?}", "<div>{$p}</div>"))
        .escapesTo(join("  {@param p: ?}", "<div>{$p |escapeHtml}</div>"))
        .andHasUnnecessaryDirectivesRemovedTo(join("  {@param p: ?}", "<div>{$p}</div>"));
  }

  @Test
  public void testRemoveEscapeHtmlAttribute() throws Exception {
    template(join("  {@param p: ?}", "<div data-foo={$p}></div>"))
        .escapesTo(join("  {@param p: ?}", "<div data-foo={$p |escapeHtmlAttributeNospace}></div>"))
        .andHasUnnecessaryDirectivesRemovedTo(join("  {@param p: ?}", "<div data-foo={$p}></div>"));

    template(join("  {@param p: ?}", "<div data-foo='{$p}'></div>"))
        .escapesTo(join("  {@param p: ?}", "<div data-foo='{$p |escapeHtmlAttribute}'></div>"))
        .andHasUnnecessaryDirectivesRemovedTo(
            join("  {@param p: ?}", "<div data-foo='{$p}'></div>"));

    template(join("  {@param p: ?}", "<script src='{$p}'></script>"))
        .escapesTo(
            join(
                "  {@param p: ?}",
                CSP_NONCE,
                "<script src='{$p |filterTrustedResourceUri |escapeHtmlAttribute}'{if $csp_nonce}"
                    + " nonce=\"{$csp_nonce |filterCspNonceValue |escapeHtmlAttribute}\"{/if}>"
                    + "</script>"))
        .andHasUnnecessaryDirectivesRemovedTo(
            join(
                "  {@param p: ?}",
                CSP_NONCE,
                "<script src='{$p |filterTrustedResourceUri}'{if $csp_nonce} nonce=\"{$csp_nonce"
                    + " |filterCspNonceValue}\"{/if}></script>"));
  }

  private static String join(String... lines) {
    return Joiner.on("\n").join(lines);
  }

  private interface EscaperAssertion {
    RewrittenAsssertion escapesTo(String escapedTemplate);
  }

  private interface RewrittenAsssertion {
    void andHasUnnecessaryDirectivesRemovedTo(String removedDirectivesTemplate);
  }

  private EscaperAssertion template(final String template) {
    return new EscaperAssertion() {
      private String compose(String template) {
        return String.format("{namespace ns}\n\n{template foo}\n%s\n{/template}", template);
      }

      @Override
      public RewrittenAsssertion escapesTo(String escapedTemplate) {
        final SoyFileSetNode file =
            SoyFileSetParserBuilder.forFileContents(compose(template))
                .runAutoescaper(true)
                .parse()
                .fileSet();
        assertThat(file.getChild(0).toSourceString().trim()).isEqualTo(compose(escapedTemplate));
        return new RewrittenAsssertion() {
          @Override
          public void andHasUnnecessaryDirectivesRemovedTo(String removedDirectivesTemplate) {
            new RemoveUnnecessaryEscapingDirectives(
                    SoyBidiUtils.decodeBidiGlobalDirFromJsOptions(0, true))
                .run(file);
            assertThat(file.getChild(0).toSourceString().trim())
                .isEqualTo(compose(removedDirectivesTemplate));
          }
        };
      }
    };
  }
}
