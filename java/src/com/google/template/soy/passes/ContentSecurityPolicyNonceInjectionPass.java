/*
 * Copyright 2017 Google Inc.
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

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.soytree.HtmlAttributeNode;
import com.google.template.soy.soytree.HtmlAttributeValueNode;
import com.google.template.soy.soytree.HtmlOpenTagNode;
import com.google.template.soy.soytree.IfCondNode;
import com.google.template.soy.soytree.IfNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TagName;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.defn.TemplateParam;
import com.google.template.soy.types.ast.NamedTypeNode;
import javax.annotation.Nullable;

/**
 * A compiler pass that adds CSP nonce injections to {@code <script>} and {@code <style>} tags.
 *
 * <p>This makes it easy for applications using soy to set <a
 * href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Content-Security-Policy/script-src#Unsafe_inline_script">{@code
 * unsafe-inline}</a> Content security policy setting.
 *
 * <p>For example, given this soy template.
 *
 * <pre><code>
 *
 * &lt;script>var x = 'foo'&lt;/script>
 *
 * </code></pre>
 *
 * <p>We 'know' that this script is safe because it was written by the author (rather than an
 * attacker), so Soy will rewrite it to look like:
 *
 * <pre><code>
 *
 * &lt;script{if $csp_nonce} nonce="{$csp_nonce}"{/if}>var x = 'foo'&lt;/script>
 *
 * </code></pre>
 *
 * <p>Then if the user configures a {@code csp_nonce} in their CSP settings and as an injected
 * variable to rendering, all author controlled scripts and styles will be authorized.
 */
@RunBefore({
  ResolveNamesPass.class, // since it adds new varref nodes
  AutoescaperPass.class, // since it inserts print directives
})
public final class ContentSecurityPolicyNonceInjectionPass implements CompilerFilePass {
  public static final String CSP_NONCE_VARIABLE_NAME = "csp_nonce";

  private static final SoyErrorKind IJ_CSP_NONCE_REFERENCE =
      SoyErrorKind.of(
          "Found a use of the injected parameter ''csp_nonce''. This parameter is reserved "
              + "by the Soy compiler for Content Security Policy support.");

  private static final SoyErrorKind MANUAL_NONCE =
      SoyErrorKind.of(
          "Found a ''nonce'' attribute on a tag that is supported by Soy auto-nonce support. "
              + "Instead of manually adding nonces you should just supply the ''csp_nonce'' "
              + "injected parameter and rely on the Soy compiler to add nonce attributes.");

  private final ErrorReporter errorReporter;

  ContentSecurityPolicyNonceInjectionPass(ErrorReporter errorReporter) {
    this.errorReporter = errorReporter;
  }

  @Override
  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    // first, make sure that the user hasn't specified any injected parameters called 'csp_nonce'
    // Search for injected params named 'csp_nonce'.
    for (TemplateNode template : file.getTemplates()) {
      for (TemplateParam param : template.getAllParams()) {
        if (param.isInjected() && param.name().equals(CSP_NONCE_VARIABLE_NAME)) {
          errorReporter.report(param.nameLocation(), IJ_CSP_NONCE_REFERENCE);
        }
      }
    }
    for (TemplateNode template : file.getTemplates()) {
      TemplateParam defn = null;
      for (HtmlOpenTagNode openTag :
          SoyTreeUtils.getAllNodesOfType(template, HtmlOpenTagNode.class)) {
        if (isTagNonceable(openTag)) {
          if (defn == null) {
            defn = createDefn();
            template.addCspNonceParam(defn);
          }
          // this should point to the character immediately before the '>' or '/>' at the end of the
          // open tag
          SourceLocation insertionLocation =
              openTag
                  .getSourceLocation()
                  .getEndPoint()
                  .offset(0, openTag.isSelfClosing() ? -2 : -1)
                  .asLocation(openTag.getSourceLocation().getFilePath());
          openTag.addChild(createCspInjection(insertionLocation, nodeIdGen, defn));
        }
      }
    }
  }

  private TemplateParam createDefn() {
    return new TemplateParam(
        CSP_NONCE_VARIABLE_NAME,
        SourceLocation.UNKNOWN,
        SourceLocation.UNKNOWN,
        // We don't use string because the targets don't have the dependency on
        // goog.soy.data.UnsanitizedText.
        NamedTypeNode.create(SourceLocation.UNKNOWN, "any"),
        /* isInjected= */ true,
        /* optional= */ true,
        /* desc= */ "Created by ContentSecurityPolicyNonceInjectionPass.",
        /* defaultValue= */ null);
  }

  private boolean isTagNonceable(HtmlOpenTagNode tag) {
    TagName nameObj = tag.getTagName();
    if (!nameObj.isStatic()) {
      return false;
    }
    String name = nameObj.getStaticTagNameAsLowerCase();
    if (name.equals("script")
        || name.equals("style")
        || (name.equals("link") && isNonceableLink(tag))) {
      HtmlAttributeNode manualNonce = tag.getDirectAttributeNamed("nonce");
      if (manualNonce != null) {
        errorReporter.report(manualNonce.getSourceLocation(), MANUAL_NONCE);
        return false;
      }
      return true;
    }
    return false;
  }

  @Nullable
  private String getStaticDirectAttributeValue(HtmlOpenTagNode tag, String attribute) {
    HtmlAttributeNode attr = tag.getDirectAttributeNamed(attribute);
    return attr == null ? null : attr.getStaticContent();
  }

  // See https://html.spec.whatwg.org/#obtaining-a-resource-from-a-link-element
  private boolean isNonceableLink(HtmlOpenTagNode tag) {
    String relAttrValue = getStaticDirectAttributeValue(tag, "rel");
    if (relAttrValue == null) {
      return false;
    }
    if (Ascii.equalsIgnoreCase("import", relAttrValue)) {
      return true;
    }
    if (Ascii.equalsIgnoreCase("preload", relAttrValue)) {
      String asAttrValue = getStaticDirectAttributeValue(tag, "as");
      if (asAttrValue == null) {
        return false;
      }
      return Ascii.equalsIgnoreCase(asAttrValue, "script")
          || Ascii.equalsIgnoreCase(asAttrValue, "style");
    }
    return false;
  }

  /**
   * Generates an AST fragment that looks like: {if $csp_nonce} nonce="{$csp_nonce}"{/if}
   *
   * @param insertionLocation The location where it is being inserted
   * @param nodeIdGen The id generator to use
   */
  private static IfNode createCspInjection(
      SourceLocation insertionLocation, IdGenerator nodeIdGen, TemplateParam defn) {
    IfNode ifNode = new IfNode(nodeIdGen.genId(), insertionLocation);
    IfCondNode ifCondNode =
        new IfCondNode(
            nodeIdGen.genId(),
            insertionLocation,
            SourceLocation.UNKNOWN,
            "if",
            referenceCspNonce(insertionLocation, defn));
    ifNode.addChild(ifCondNode);
    HtmlAttributeNode nonceAttribute =
        new HtmlAttributeNode(
            nodeIdGen.genId(), insertionLocation, insertionLocation.getBeginPoint());
    ifCondNode.addChild(nonceAttribute);
    nonceAttribute.addChild(new RawTextNode(nodeIdGen.genId(), "nonce", insertionLocation));
    HtmlAttributeValueNode attributeValue =
        new HtmlAttributeValueNode(
            nodeIdGen.genId(), insertionLocation, HtmlAttributeValueNode.Quotes.DOUBLE);
    nonceAttribute.addChild(attributeValue);
    // NOTE: we do not need to insert any print directives here, unlike the old implementation since
    // we are running before the autoescaper, so the escaper should insert whatever print directives
    // are appropriate.
    PrintNode printNode =
        new PrintNode(
            nodeIdGen.genId(),
            insertionLocation,
            /* isImplicit= */ true, // Implicit.  {$csp_nonce} not {print $csp_nonce}
            referenceCspNonce(insertionLocation, defn),
            /* attributes= */ ImmutableList.of(),
            ErrorReporter.exploding());
    attributeValue.addChild(printNode);
    return ifNode;
  }

  private static VarRefNode referenceCspNonce(
      SourceLocation insertionLocation, TemplateParam defn) {
    return new VarRefNode(CSP_NONCE_VARIABLE_NAME, insertionLocation, defn);
  }
}
