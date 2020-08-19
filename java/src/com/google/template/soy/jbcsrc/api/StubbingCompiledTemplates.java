/*
 * Copyright 2020 Google Inc.
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

import static com.google.common.base.StandardSystemProperty.JAVA_CLASS_PATH;
import static com.google.common.base.StandardSystemProperty.PATH_SEPARATOR;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.html.types.SafeHtml;
import com.google.common.html.types.SafeScript;
import com.google.common.html.types.SafeStyleSheet;
import com.google.common.html.types.SafeUrl;
import com.google.common.html.types.TrustedResourceUrl;
import com.google.errorprone.annotations.Immutable;
import com.google.template.soy.data.LoggingAdvisingAppendable;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.SoyRecord;
import com.google.template.soy.jbcsrc.api.StubbingSoySauce.ImmutablePredicate;
import com.google.template.soy.jbcsrc.api.StubbingSoySauce.StubFactory;
import com.google.template.soy.jbcsrc.api.StubbingSoySauce.TemplateStub;
import com.google.template.soy.jbcsrc.shared.CompiledTemplate;
import com.google.template.soy.jbcsrc.shared.CompiledTemplates;
import com.google.template.soy.jbcsrc.shared.Names;
import com.google.template.soy.jbcsrc.shared.RenderContext;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.function.Function;

/**
 * Special test-only subclass of {@link CompiledTemplates} that returns stub templates for template
 * names matching a given predicate. This is used for legacy unit tests; please contact soy-dev@ if
 * you think you need to use this.
 */
final class StubbingCompiledTemplates extends CompiledTemplates {
  private final ImmutablePredicate<String> stubMatcher;
  private final StubFactory stubFactory;
  private final CompiledTemplates nonStubbingCompiledTemplates;

  static StubbingCompiledTemplates create(
      ImmutablePredicate<String> stubMatcher,
      StubFactory stubFactory,
      ImmutableSet<String> delTemplateNames,
      ClassLoader loader) {
    CompiledTemplates nonStubbingCompiledTemplates =
        new CompiledTemplates(delTemplateNames, loader);
    ClassLoader stubbingClassLoader =
        new StubbingClassLoader(loader, stubMatcher, nonStubbingCompiledTemplates);
    return new StubbingCompiledTemplates(
        stubMatcher,
        stubFactory,
        delTemplateNames,
        stubbingClassLoader,
        nonStubbingCompiledTemplates);
  }

  private StubbingCompiledTemplates(
      ImmutablePredicate<String> stubMatcher,
      StubFactory stubFactory,
      ImmutableSet<String> delTemplateNames,
      ClassLoader stubbingClassLoader,
      CompiledTemplates nonStubbingCompiledTemplates) {
    super(delTemplateNames, stubbingClassLoader);
    this.stubMatcher = stubMatcher;
    this.stubFactory = stubFactory;
    this.nonStubbingCompiledTemplates = nonStubbingCompiledTemplates;
  }

  @Override
  public ContentKind getTemplateContentKind(String name) {
    // Need to use the non-stubbing CompiledTemplates object so we get a real class to look up the
    // content kind.
    return nonStubbingCompiledTemplates.getTemplateContentKind(name);
  }

  @Override
  public CompiledTemplate.Factory getTemplateFactory(String name) {
    ContentKind contentKind = getTemplateContentKind(name);
    if (!stubMatcher.test(name)) {
      return super.getTemplateFactory(name);
    }
    switch (contentKind) {
      case HTML:
        return new Factory<>(
            contentKind, stubFactory.createHtmlTemplate(name), SafeHtml::getSafeHtmlString);
      case JS:
        return new Factory<>(
            contentKind, stubFactory.createJsTemplate(name), SafeScript::getSafeScriptString);
      case URI:
        return new Factory<>(
            contentKind, stubFactory.createUriTemplate(name), SafeUrl::getSafeUrlString);
      case TRUSTED_RESOURCE_URI:
        return new Factory<>(
            contentKind,
            stubFactory.createTrustedResourceUriTemplate(name),
            TrustedResourceUrl::getTrustedResourceUrlString);
      case ATTRIBUTES:
        return new Factory<SanitizedContent>(
            contentKind,
            stubFactory.createAttributesTemplate(name),
            (sanitizedContent) -> {
              Preconditions.checkState(
                  sanitizedContent.getContentKind() == ContentKind.ATTRIBUTES,
                  "The sanitized content returned from createAttributesTemplate() must have"
                      + " contentKind == ATTRIBUTES");
              return sanitizedContent.toString();
            });
      case CSS:
        return new Factory<>(
            contentKind,
            stubFactory.createCssTemplate(name),
            SafeStyleSheet::getSafeStyleSheetString);
      case TEXT:
        return new Factory<>(contentKind, stubFactory.createTextTemplate(name), text -> text);
    }
    throw new AssertionError("Unhandled content kind: " + contentKind);
  }

  @Override
  protected TemplateData getTemplateData(String name) {
    if (stubMatcher.test(name)) {
      return null;
    }
    return super.getTemplateData(name);
  }

  /** Immutable sub-interface of {@link Function}. */
  @Immutable
  private interface ImmutableFunction<T, R> extends Function<T, R> {}

  private static class Factory<T> extends CompiledTemplate.Factory {
    private final ContentKind contentKind;
    private final TemplateStub<T> stub;
    private final ImmutableFunction<T, String> toStringFunction;

    private Factory(
        ContentKind contentKind,
        TemplateStub<T> stub,
        ImmutableFunction<T, String> toStringFunction) {
      this.contentKind = contentKind;
      this.stub = Preconditions.checkNotNull(stub);
      this.toStringFunction = toStringFunction;
    }

    @Override
    public CompiledTemplate create(SoyRecord params, SoyRecord ij) {
      return new CompiledTemplate() {
        @Override
        public RenderResult render(LoggingAdvisingAppendable appendable, RenderContext context)
            throws IOException {
          appendable.append(
              toStringFunction.apply(
                  Preconditions.checkNotNull(stub.render(params.recordAsMap()))));
          return RenderResult.done();
        }

        @Override
        public ContentKind kind() {
          return contentKind;
        }
      };
    }
  }

  private static class StubbingClassLoader extends URLClassLoader {
    private final ImmutablePredicate<String> stubMatcher;
    private final CompiledTemplates nonStubbingCompiledTemplates;

    private StubbingClassLoader(
        ClassLoader backing,
        ImmutablePredicate<String> stubMatcher,
        CompiledTemplates nonStubbingCompiledTemplates) {
      super(getClassPathUrls(), backing);
      this.stubMatcher = stubMatcher;
      this.nonStubbingCompiledTemplates = nonStubbingCompiledTemplates;
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
      if (name.startsWith(Names.CLASS_PREFIX)) {
        String soyTemplateName = Names.soyTemplateNameFromJavaClassName(name);
        // Normalize names of factory classes to match Soy template names, for the convenience of
        // the user-defined predicates.
        if (soyTemplateName.indexOf('$') != -1) {
          soyTemplateName = soyTemplateName.substring(0, soyTemplateName.indexOf('$'));
        }
        // Private templates are not stubbable, so do a check here and don't attempt to stub them if
        // they are private. If we throw a ClassNotFoundException here, the slow path will happily
        // attempt to look up the factory and the template will be stubbed.
        boolean isPrivateTemplate = false;
        try {
          nonStubbingCompiledTemplates.getTemplateFactory(soyTemplateName);
        } catch (IllegalArgumentException thrownForPrivateTemplates) {
          isPrivateTemplate = true;
        }
        if (!isPrivateTemplate && stubMatcher.test(soyTemplateName)) {
          // Force loading the class via CompiledTemplates, which will return the stub template
          // instead.
          throw new ClassNotFoundException("Forcing invokedynamic to select the slowpath.");
        }
        // For other Soy templates, we need to load the class manually using this ClassLoader, so
        // that their ClassLoader references point back to this class. This is important because
        // TemplateCallFactory uses the calling-class's ClassLoader to look up the subtemplates at
        // call sites (invoked automatically by Java by the invokedynamic instruction). We need this
        // class to be in control of which templates are stubbed so that we can properly swap out
        // stub templates when a call chain finds itself in one.
        Class<?> c = findLoadedClass(name);
        if (c == null) {
          return super.findClass(name);
        }
        return c;
      }
      // For non-Soy templates, delegate to the containing ClassLoader.
      return super.loadClass(name);
    }

    // Below code lifted from ClassPathUtil.java.
    private static URL[] parseJavaClassPath() {
      ImmutableList.Builder<URL> urls = ImmutableList.builder();
      for (String entry : Splitter.on(PATH_SEPARATOR.value()).split(JAVA_CLASS_PATH.value())) {
        try {
          try {
            urls.add(new File(entry).toURI().toURL());
          } catch (SecurityException e) { // File.toURI checks to see if the file is a directory
            urls.add(new URL("file", null, new File(entry).getAbsolutePath()));
          }
        } catch (MalformedURLException e) {
          throw new AssertionError("malformed class path entry: " + entry, e);
        }
      }
      return urls.build().toArray(new URL[0]);
    }

    /** Returns the URLs in the class path. */
    private static URL[] getClassPathUrls() {
      return StubbingClassLoader.class.getClassLoader() instanceof URLClassLoader
          ? ((URLClassLoader) StubbingClassLoader.class.getClassLoader()).getURLs()
          : parseJavaClassPath();
    }
  }
}
