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

package com.google.template.soy.jbcsrc.shared;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.template.soy.base.internal.BaseUtils;
import com.google.template.soy.internal.exemptions.NamespaceExemptions;

/**
 * Utilities for translating soy symbols to and from strings that are suitable for use in java class
 * files. These utilities are shared between the compiler and the runtime system.
 */
public final class Names {
  // Note: This and the plugin path below write into META-INF/services even though they aren't
  // services, because the surrounding tools already merge data in META-INF/services files.
  // (The tools do *not* merge other files in META-INF, which is why we hijack the services dir.)
  public static final String META_INF_DELTEMPLATE_PATH =
      "META-INF/services/com.google.template.soy.deltemplates";

  public static final String META_INF_PLUGIN_PATH =
      "META-INF/services/com.google.template.soy.plugins";

  public static final String CLASS_PREFIX = "com.google.template.soy.jbcsrc.gen.";
  public static final String INTERNAL_CLASS_PREFIX = CLASS_PREFIX.replace('.', '/');

  private Names() {}

  /**
   * Translate a user controlled Soy name to a form that is safe to encode in a java class, method
   * or field name.
   *
   * <p>Soy identifiers are very simple, they are restricted to the following regex: {@code
   * [a-zA-Z_]([a-zA-Z_0-9])*}. So a template name is just one or more identifiers separated by
   * {@code .} characters. To escape it, we simply replace all '.'s with '_'s, and all '_'s with
   * '__' and prefix with a package name.
   */
  public static String javaClassNameFromSoyTemplateName(String soyTemplate) {
    checkArgument(
        BaseUtils.isDottedIdentifier(soyTemplate), "%s is not a valid template name.", soyTemplate);
    int lastDot = soyTemplate.lastIndexOf('.');
    checkArgument(lastDot != -1, "%s should contain a dot", soyTemplate);
    String soyNamespace = soyTemplate.substring(0, lastDot);
    return NamespaceExemptions.isKnownDuplicateNamespace(soyNamespace)
        ? CLASS_PREFIX + soyTemplate
        : CLASS_PREFIX + soyNamespace;
  }

  /**
   * Translate a user controlled Soy name to a form that is safe to encode in a java class, method
   * or field name.
   *
   * <p>Soy identifiers are very simple, they are restricted to the following regex: {@code
   * [a-zA-Z_]([a-zA-Z_0-9])*}. So a template name is just one or more identifiers separated by
   * {@code .} characters. To escape it, we simply replace all '.'s with '_'s, and all '_'s with
   * '__' and prefix with a package name.
   */
  public static String javaClassNameFromSoyNamespace(String soyTemplateNamespace) {
    checkArgument(
        BaseUtils.isDottedIdentifier(soyTemplateNamespace),
        "%s is not a valid template namespace.",
        soyTemplateNamespace);
    checkArgument(!NamespaceExemptions.isKnownDuplicateNamespace(soyTemplateNamespace));
    return CLASS_PREFIX + soyTemplateNamespace;
  }

  /** Returns the name for the rendering methods for the given template. */
  public static String renderMethodNameFromSoyTemplateName(String soyTemplate) {
    checkArgument(
        BaseUtils.isDottedIdentifier(soyTemplate), "%s is not a valid template name.", soyTemplate);
    int lastDot = soyTemplate.lastIndexOf('.');
    String soyNamespace = soyTemplate.substring(0, lastDot);
    if (NamespaceExemptions.isKnownDuplicateNamespace(soyNamespace)) {
      return "render";
    }
    return soyTemplate.substring(lastDot + 1);
  }

  /** Returns the name for the {@code ()->CompileTemplate} method for the given template. */
  public static String templateMethodNameFromSoyTemplateName(String soyTemplate) {
    checkArgument(
        BaseUtils.isDottedIdentifier(soyTemplate), "%s is not a valid template name.", soyTemplate);
    int lastDot = soyTemplate.lastIndexOf('.');
    String soyNamespace = soyTemplate.substring(0, lastDot);
    if (NamespaceExemptions.isKnownDuplicateNamespace(soyNamespace)) {
      return "template";
    }
    return "template$" + soyTemplate.substring(lastDot + 1);
  }

  /**
   * Given the soy namespace and file name returns the path where the corresponding resource should
   * be stored.
   */
  public static String javaFileName(String soyNamespace, String fileName) {
    checkArgument(
        BaseUtils.isDottedIdentifier(soyNamespace),
        "%s is not a valid soy namspace name.",
        soyNamespace);

    String javaClassName = javaClassNameFromSoyTemplateName(soyNamespace + ".foo");
    String javaPackageName = javaClassName.substring(0, javaClassName.lastIndexOf('.'));
    // convert to a resource path
    return javaPackageName.replace('.', '/') + '/' + fileName;
  }

  /**
   * Rewrites the given stack trace by replacing all references to generated jbcsrc types with the
   * original template names.
   */
  public static void rewriteStackTrace(Throwable throwable) {
    StackTraceElement[] stack = throwable.getStackTrace();
    for (int i = 0; i < stack.length; i++) {
      StackTraceElement curr = stack[i];
      if (curr.getClassName().startsWith(CLASS_PREFIX)) {
        stack[i] =
            new StackTraceElement(
                curr.getClassName().substring(CLASS_PREFIX.length()),
                // Method name == templateName or 'render'. according to
                // renderMethodNameFromSoyTemplateName
                curr.getMethodName(),
                curr.getFileName(),
                curr.getLineNumber());
      }
    }
    throwable.setStackTrace(stack);
  }
}
