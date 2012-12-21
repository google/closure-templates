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

package com.google.template.soy.bididirectives;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SanitizedContentOperator;
import com.google.template.soy.data.SoyData;
import com.google.template.soy.internal.i18n.BidiGlobalDir;
import com.google.template.soy.internal.i18n.SoyBidiUtils;
import com.google.template.soy.javasrc.restricted.JavaCodeUtils;
import com.google.template.soy.javasrc.restricted.JavaExpr;
import com.google.template.soy.javasrc.restricted.SoyJavaSrcFunctionUtils;
import com.google.template.soy.javasrc.restricted.SoyJavaSrcPrintDirective;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.SoyJsSrcPrintDirective;
import com.google.template.soy.tofu.restricted.SoyAbstractTofuPrintDirective;

import java.util.List;
import java.util.Set;


/**
 * A directive that maybe wraps the output within a 'span' with dir=ltr or dir=rtl. This wrapping
 * is only applied when the output text's bidi directionality is different from the bidi global
 * directionality.
 *
 * @author Kai Huang
 * @author Aharon Lanin
 */
@Singleton
public class BidiSpanWrapDirective extends SoyAbstractTofuPrintDirective
    implements SoyJsSrcPrintDirective, SoyJavaSrcPrintDirective, SanitizedContentOperator {


  /** Provider for the current bidi global directionality. */
  private final Provider<BidiGlobalDir> bidiGlobalDirProvider;


  /**
   * @param bidiGlobalDirProvider Provider for the current bidi global directionality.
   */
  @Inject
  BidiSpanWrapDirective(Provider<BidiGlobalDir> bidiGlobalDirProvider) {
    this.bidiGlobalDirProvider = bidiGlobalDirProvider;
  }


  @Override public String getName() {
    return "|bidiSpanWrap";
  }


  @Override public Set<Integer> getValidArgsSizes() {
    return ImmutableSet.of(0);
  }


  @Override public boolean shouldCancelAutoescape() {
    return false;
  }


  @Override public SoyData apply(SoyData value, List<SoyData> args) {
    boolean isHtml = true;
    String html = SoyBidiUtils.getBidiFormatter(bidiGlobalDirProvider.get().getStaticValue())
        .spanWrap(value.toString(), isHtml);
    // TODO(user): convert to HTML SanitizedContent when isHtml.
    return SoyData.createFromExistingData(html);
  }


  @Override public JsExpr applyForJsSrc(JsExpr value, List<JsExpr> args) {
    String codeSnippet = bidiGlobalDirProvider.get().getCodeSnippet();
    return new JsExpr(
        "soy.$$bidiSpanWrap(" + codeSnippet + ", " + value.getText() + ")",
        Integer.MAX_VALUE);
  }


  @Override public JavaExpr applyForJavaSrc(JavaExpr value, List<JavaExpr> args) {
    String bidiFunctionName = SoyBidiUtils.class.getName() + ".getBidiFormatter(" +
        bidiGlobalDirProvider.get().getCodeSnippet() + ").spanWrap";

    return SoyJavaSrcFunctionUtils.toStringJavaExpr(
        JavaCodeUtils.genNewStringData(
            JavaCodeUtils.genFunctionCall(
                bidiFunctionName,
                JavaCodeUtils.genCoerceString(value),
                "true")));
  }


  @Override public SanitizedContent.ContentKind getContentKind() {
    // This directive expects HTML as input and produces HTML as output.
    return SanitizedContent.ContentKind.HTML;
  }

}
