/*
 * Copyright 2010 Google Inc.
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

package com.google.template.soy.parsepasses.contextautoesc;

/**
 * Utilities for dealing with the names of derived templates.
 *
 * <p>
 * A derived template is a template that is called in a specific context.
 * A user may specify some templates that are called in multiple contexts or a single obscure
 * context such as the template {@code foo} below:
 * <pre class="prettyprint">
 * {template main}
 *   &lt;a onclick="alert({call foo/})"&gt;{call foo /}&lt;/a&gt;
 * {/template}
 * {template <b>foo</b> private="true" autoescape="contextual"}
 *   {print $msg}
 * {/template}
 * </pre>
 *
 * There is no single escaping context which makes sense for {@code $msg}.  So the auto-escaper
 * derives an extra template producing something like:
 * <pre class="prettyprint">
 * {template main}
 *   &lt;a onclick="alert({call foo/})"&gt;{call foo /}&lt;/a&gt;
 * {/template}
 * {template <abbr title="derived for PCDATA">foo</abbr> private="true"}
 *   {print $msg <b>|escapeHtml</b>}
 * {/template}
 * {template <abbr title="derived for JS">foo__X1234</abbr> private="true"}
 *   {print $msg <b>|escapeJsValue</b>}
 * {/template}
 * </pre>
 *
 * <p>
 * Each derived template has a name that is built by name mangling an original template name with
 * the template's start {@link Context context}.
 * A derived template's name (or qualified name) looks like:<pre>
 *     qualifiedName ::== baseName [separator context]
 *                                 ^^^^^^^^^^^^^^^^^^^
 *                                          |
 *                                        suffix
 * </pre>
 *
 * <p>
 * The base name is the name of a template in the original Soy source.
 * The separator is a fixed string.
 * The context is derived from the {@link Context#packedBits} of the template's start context.
 * The separator and context together form a suffix.
 *
 * <p>
 * As shown above, the suffix is optional.  The suffix is omitted for any template whose context
 * is the default starting context: {@link Context#HTML_PCDATA pcdata}.
 *
 * @author Mike Samuel
 */
public final class DerivedTemplateUtils {

  /** Separates the base name from the packed bits. */
  private static final String CONTEXT_SEPARATOR = "__C";

  /**
   * The suffix as described above.
   *
   * @return the empty string when the suffix is optional.
   */
  public static String getSuffix(Context startContext) {
    if (Context.HTML_PCDATA.equals(startContext)) {
      // The default when autoescape=true.
      return "";
    } else {
      return CONTEXT_SEPARATOR + Integer.toString(startContext.packedBits(), 16);
    }
  }

  /**
   * The base name for the given template name whether derived or not.
   */
  public static String getBaseName(String templateName) {
    int separatorIndex = templateName.lastIndexOf(CONTEXT_SEPARATOR);
    return separatorIndex < 0 ? templateName : templateName.substring(0, separatorIndex);
  }

  /**
   * A derived name for a template derived from the given base template and the given start context.
   */
  public static String getQualifiedName(String baseName, Context startContext) {
    return getBaseName(baseName) + getSuffix(startContext);
  }

  private DerivedTemplateUtils() {
    // Not instantiable.
  }
}
