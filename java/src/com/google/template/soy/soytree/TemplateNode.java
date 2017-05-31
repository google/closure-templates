/*
 * Copyright 2008 Google Inc.
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

package com.google.template.soy.soytree;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.BaseUtils;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.base.internal.TriState;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.soytree.SoyNode.RenderUnitNode;
import com.google.template.soy.soytree.defn.HeaderParam;
import com.google.template.soy.soytree.defn.TemplateParam;
import com.google.template.soy.soytree.defn.TemplateParam.DeclLoc;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * Node representing a template.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public abstract class TemplateNode extends AbstractBlockCommandNode implements RenderUnitNode {

  /** Priority for delegate templates. */
  public enum Priority {
    STANDARD(0),
    HIGH_PRIORITY(1);

    private final int value;

    Priority(int value) {
      this.value = value;
    }

    public int getValue() {
      return value;
    }

    @Override
    public String toString() {
      return Integer.toString(value);
    }
  }

  // TODO(sparhami): Add error for unused alias.

  private static final SoyErrorKind INVALID_ALIAS_FOR_LAST_PART_OF_NAMESPACE =
      SoyErrorKind.of(
          "Not allowed to alias the last part of the file''s namespace ({0}) "
              + "to another namespace ({1}).");

  private static final SoyErrorKind DUPLICATE_ALIAS =
      SoyErrorKind.of("Duplicate alias definition ''{0}''.");

  /**
   * Info from the containing Soy file's {@code delpackage} and {@code namespace} declarations.
   *
   * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
   *
   * <p>Note: Currently, there are only 2 delegate priority values: 0 and 1. Delegate templates that
   * are not in a delegate package are given priority 0 (lowest). Delegate templates in a delegate
   * package are given priority 1. There is currently no syntax for the user to override these
   * default priority values.
   */
  @Immutable
  public static class SoyFileHeaderInfo {

    /** Map from aliases to namespaces for this file. */
    public final ImmutableMap<String, String> aliasToNamespaceMap;

    /** Map from aliases to namespaces for this file. */
    public final ImmutableList<AliasDeclaration> aliasDeclarations;

    @Nullable public final String delPackageName;
    final Priority priority;
    @Nullable public final String namespace;
    public final AutoescapeMode defaultAutoescapeMode;
    public final TriState strictHtmlMode;

    public SoyFileHeaderInfo(
        ErrorReporter errorReporter,
        @Nullable Identifier delpackageName,
        NamespaceDeclaration namespaceDeclaration,
        Collection<AliasDeclaration> aliases) {
      this(
          delpackageName == null ? null : delpackageName.identifier(),
          namespaceDeclaration.getNamespace(),
          namespaceDeclaration.getDefaultAutoescapeMode(),
          namespaceDeclaration.getStrictHtmlMode(),
          createAliasMap(errorReporter, namespaceDeclaration, aliases),
          ImmutableList.copyOf(aliases));
    }

    @VisibleForTesting
    public SoyFileHeaderInfo(String namespace) {
      this(
          null,
          namespace,
          AutoescapeMode.NONCONTEXTUAL,
          TriState.UNSET, // Don't use stricthtml mode for testing.
          ImmutableMap.<String, String>of(),
          ImmutableList.<AliasDeclaration>of());
    }

    private SoyFileHeaderInfo(
        @Nullable String delPackageName,
        String namespace,
        AutoescapeMode defaultAutoescapeMode,
        TriState strictHtmlMode,
        ImmutableMap<String, String> aliasToNamespaceMap,
        ImmutableList<AliasDeclaration> aliasDeclarations) {
      this.delPackageName = delPackageName;
      this.priority = (delPackageName == null) ? Priority.STANDARD : Priority.HIGH_PRIORITY;
      this.namespace = namespace;
      this.defaultAutoescapeMode = defaultAutoescapeMode;
      this.strictHtmlMode = strictHtmlMode;
      this.aliasToNamespaceMap = aliasToNamespaceMap;
      this.aliasDeclarations = aliasDeclarations;
    }

    private static ImmutableMap<String, String> createAliasMap(
        ErrorReporter errorReporter,
        NamespaceDeclaration namespaceDeclaration,
        Collection<AliasDeclaration> aliases) {
      Map<String, String> map = Maps.newLinkedHashMap();
      String aliasForFileNamespace =
          BaseUtils.extractPartAfterLastDot(namespaceDeclaration.getNamespace());
      for (AliasDeclaration aliasDeclaration : aliases) {
        String aliasNamespace = aliasDeclaration.getNamespace();
        String alias = aliasDeclaration.getAlias();
        if (alias.equals(aliasForFileNamespace)
            && !aliasNamespace.equals(namespaceDeclaration.getNamespace())) {
          errorReporter.report(
              aliasDeclaration.getLocation(),
              INVALID_ALIAS_FOR_LAST_PART_OF_NAMESPACE,
              namespaceDeclaration.getNamespace(),
              aliasNamespace);
        }
        if (map.containsKey(alias)) {
          errorReporter.report(aliasDeclaration.getLocation(), DUPLICATE_ALIAS, alias);
        }
        map.put(alias, aliasNamespace);
      }
      return ImmutableMap.copyOf(map);
    }
  }

  // -----------------------------------------------------------------------------------------------
  // TemplateNode body.

  /** Info from the containing Soy file's header declarations. */
  private final SoyFileHeaderInfo soyFileHeaderInfo;

  /** This template's name. */
  private final String templateName;

  /** This template's partial name. Only applicable for V2. */
  @Nullable private final String partialTemplateName;

  /** A string suitable for display in user msgs as the template name. */
  private final String templateNameForUserMsgs;

  /** Visibility of this template. */
  private final Visibility visibility;

  /** The mode of autoescaping for this template. */
  private final AutoescapeMode autoescapeMode;

  /** Strict mode context. Nonnull iff autoescapeMode is strict. */
  @Nullable private final ContentKind contentKind;

  /** Required CSS namespaces. */
  private final ImmutableList<String> requiredCssNamespaces;

  /** Base namespace for package-relative class names. */
  private final String cssBaseNamespace;

  /** The full SoyDoc, including the start/end tokens, or null. */
  private String soyDoc;

  /** The description portion of the SoyDoc (before declarations), or null. */
  private String soyDocDesc;

  /** If the template is using strict html mode. */
  private final boolean strictHtml;

  /** The params from template header or SoyDoc. Null if no decls and no SoyDoc. */
  @Nullable private ImmutableList<TemplateParam> params;

  /** The injected params from template header. Null if no decls. */
  @Nullable private ImmutableList<TemplateParam> injectedParams;

  private int maxLocalVariableTableSize = -1;

  // TODO(user): Remove.
  private final String commandText;

  /**
   * Main constructor. This is package-private because Template*Node instances should be built using
   * the Template*NodeBuilder classes.
   *
   * @param nodeBuilder Builder containing template initialization params.
   * @param cmdName The command name.
   * @param soyFileHeaderInfo Info from the containing Soy file's header declarations.
   * @param visibility Visibility of this template.
   * @param params The params from template header or SoyDoc. Null if no decls and no SoyDoc.
   */
  TemplateNode(
      TemplateNodeBuilder nodeBuilder,
      String cmdName,
      SoyFileHeaderInfo soyFileHeaderInfo,
      Visibility visibility,
      @Nullable ImmutableList<TemplateParam> params) {
    super(nodeBuilder.getId(), nodeBuilder.sourceLocation, cmdName);
    maybeSetSyntaxVersionUpperBound(nodeBuilder.getSyntaxVersionBound());
    this.soyFileHeaderInfo = soyFileHeaderInfo;
    this.templateName = nodeBuilder.getTemplateName();
    this.partialTemplateName = nodeBuilder.getPartialTemplateName();
    this.templateNameForUserMsgs = nodeBuilder.getTemplateNameForUserMsgs();
    this.visibility = visibility;
    this.autoescapeMode = nodeBuilder.getAutoescapeMode();
    this.contentKind = nodeBuilder.getContentKind();
    this.requiredCssNamespaces = nodeBuilder.getRequiredCssNamespaces();
    this.cssBaseNamespace = nodeBuilder.getCssBaseNamespace();
    this.soyDoc = nodeBuilder.getSoyDoc();
    this.soyDocDesc = nodeBuilder.getSoyDocDesc();
    if (nodeBuilder.getStrictHtmlMode() != TriState.UNSET) {
      // use the value that is explicitly set in template.
      this.strictHtml = nodeBuilder.getStrictHtmlMode() == TriState.ENABLED;
    } else if (soyFileHeaderInfo.strictHtmlMode != TriState.UNSET
        && contentKind == ContentKind.HTML) {
      // If the value is not set, HTML templates will inherit from namespace declaration.
      this.strictHtml = soyFileHeaderInfo.strictHtmlMode == TriState.ENABLED;
    } else {
      // Default value is false.
      this.strictHtml = false;
    }
    // Split out @inject params into a separate list because we don't want them
    // to be visible to code that looks at the template's calling signature.
    ImmutableList.Builder<TemplateParam> regularParams = ImmutableList.builder();
    ImmutableList.Builder<TemplateParam> injectedParams = ImmutableList.builder();
    if (params != null) {
      for (TemplateParam param : params) {
        if (param.isInjected()) {
          injectedParams.add(param);
        } else {
          regularParams.add(param);
        }
      }
    }
    // Note: These used to be nullable, but now return an empty list.
    this.params = regularParams.build();
    this.injectedParams = injectedParams.build();
    this.commandText = nodeBuilder.getCmdText().trim();
  }

  /**
   * Copy constructor.
   * @param orig The node to copy.
   */
  protected TemplateNode(TemplateNode orig, CopyState copyState) {
    super(orig, copyState);
    this.soyFileHeaderInfo = orig.soyFileHeaderInfo; // immutable
    this.templateName = orig.templateName;
    this.partialTemplateName = orig.partialTemplateName;
    this.templateNameForUserMsgs = orig.templateNameForUserMsgs;
    this.visibility = orig.visibility;
    this.autoescapeMode = orig.autoescapeMode;
    this.contentKind = orig.contentKind;
    this.requiredCssNamespaces = orig.requiredCssNamespaces;
    this.cssBaseNamespace = orig.cssBaseNamespace;
    this.soyDoc = orig.soyDoc;
    this.soyDocDesc = orig.soyDocDesc;
    // TODO(lukes): params and injectedParams are not really immutable, just mostly.  Consider
    // cloning them here and modifying SoyTreeUtils.cloneNode to reassign these as well.
    this.params = orig.params; // immutable
    this.injectedParams = orig.injectedParams;
    this.maxLocalVariableTableSize = orig.maxLocalVariableTableSize;
    this.strictHtml = orig.strictHtml;
    this.commandText = orig.commandText;
  }

  /** Returns info from the containing Soy file's header declarations. */
  public SoyFileHeaderInfo getSoyFileHeaderInfo() {
    return soyFileHeaderInfo;
  }

  /** Returns the name of the containing delegate package, or null if none. */
  public String getDelPackageName() {
    return soyFileHeaderInfo.delPackageName;
  }

  /** Returns a template name suitable for display in user msgs. */
  public String getTemplateNameForUserMsgs() {
    return templateNameForUserMsgs;
  }

  /** Returns this template's name. */
  public String getTemplateName() {
    return templateName;
  }

  /** Returns this template's partial name. Only applicable for V2 (null for V1). */
  @Nullable
  public String getPartialTemplateName() {
    return partialTemplateName;
  }

  /** Returns the visibility of this template. */
  public Visibility getVisibility() {
    return visibility;
  }

  /** Returns the mode of autoescaping. */
  public AutoescapeMode getAutoescapeMode() {
    return autoescapeMode;
  }

  /** Returns if this template is in strict html mode. */
  public boolean isStrictHtml() {
    return strictHtml;
  }

  /** Returns the content kind for strict autoescaping. Nonnull iff autoescapeMode is strict. */
  @Override
  @Nullable
  public ContentKind getContentKind() {
    return contentKind;
  }

  /**
   * Returns required CSS namespaces.
   *
   * <p>CSS "namespaces" are monikers associated with CSS files that by convention, dot-separated
   * lowercase names. They don't correspond to CSS features, but are processed by external tools
   * that impose dependencies from templates to CSS.
   */
  public ImmutableList<String> getRequiredCssNamespaces() {
    return requiredCssNamespaces;
  }

  /**
   * Returns the base CSS namespace for resolving package-relative class names. Package relative
   * class names are ones beginning with a percent (%). The compiler will replace the percent sign
   * with the name of the current CSS package converted to camel-case form.
   *
   * <p>Packages are defined using dotted-id syntax (foo.bar), which is identical to the syntax for
   * required CSS namespaces. If no base CSS namespace is defined, it will use the first required
   * css namespace, if any are present. If there is no base CSS name, and no required css
   * namespaces, then use of package-relative class names will be reported as an error.
   */
  public String getCssBaseNamespace() {
    return cssBaseNamespace;
  }

  public void setMaxLocalVariableTableSize(int size) {
    this.maxLocalVariableTableSize = size;
  }

  public int getMaxLocalVariableTableSize() {
    return maxLocalVariableTableSize;
  }

  /** Clears the SoyDoc text, description, and param descriptions. */
  public void clearSoyDocStrings() {
    soyDoc = null;
    soyDocDesc = null;

    assert params != null; // prevent warnings
    List<TemplateParam> newParams = Lists.newArrayListWithCapacity(params.size());
    for (TemplateParam origParam : params) {
      newParams.add(origParam.copyEssential());
    }
    params = ImmutableList.copyOf(newParams);
  }

  /** Returns the SoyDoc, or null. */
  @Nullable
  public String getSoyDoc() {
    return soyDoc;
  }

  /** Returns the description portion of the SoyDoc (before @param tags), or null. */
  @Nullable
  public String getSoyDocDesc() {
    return soyDocDesc;
  }

  /** Returns the params from template header or SoyDoc. */
  public ImmutableList<TemplateParam> getParams() {
    return params;
  }

  /** Returns the injected params from template header. */
  public ImmutableList<TemplateParam> getInjectedParams() {
    return injectedParams;
  }

  /** Returns all params from template header or SoyDoc, both regular and injected. */
  @Nullable
  public Iterable<TemplateParam> getAllParams() {
    return Iterables.concat(params, injectedParams);
  }

  @Override
  public SoyFileNode getParent() {
    return (SoyFileNode) super.getParent();
  }

  @Override
  public String getCommandText() {
    return commandText;
  }

  @Override
  public String toSourceString() {
    StringBuilder sb = new StringBuilder();

    // SoyDoc.
    if (soyDoc != null) {
      sb.append(soyDoc).append("\n");
    }

    // Begin tag.
    sb.append(getTagString()).append("\n");

    // Header.
    if (params != null) {
      for (TemplateParam param : params) {
        if (param.declLoc() != DeclLoc.HEADER) {
          continue;
        }
        HeaderParam headerParam = (HeaderParam) param;
        sb.append("  {@param");
        if (!headerParam.isRequired()) {
          sb.append('?');
        }
        sb.append(' ')
            .append(headerParam.name())
            .append(": ")
            .append(headerParam.type())
            .append("}");
        if (headerParam.desc() != null) {
          sb.append("  /** ").append(headerParam.desc()).append(" */");
        }
        sb.append("\n");
      }
    }

    // Body.
    // If first or last char of template body is a space, must be turned into '{sp}'.
    StringBuilder bodySb = new StringBuilder();
    appendSourceStringForChildren(bodySb);
    int bodyLen = bodySb.length();
    if (bodyLen != 0) {
      if (bodyLen != 1 && bodySb.charAt(bodyLen - 1) == ' ') {
        bodySb.replace(bodyLen - 1, bodyLen, "{sp}");
      }
      if (bodySb.charAt(0) == ' ') {
        bodySb.replace(0, 1, "{sp}");
      }
    }
    sb.append(bodySb);
    sb.append("\n");

    // End tag.
    sb.append("{/").append(getCommandName()).append("}\n");

    return sb.toString();
  }


  /**
   * Construct a StackTraceElement that will point to the given source location of the current
   * template.
   */
  public StackTraceElement createStackTraceElement(SourceLocation srcLocation) {
    if (partialTemplateName == null) {
      // V1 soy templates.
      return new StackTraceElement(
          /* declaringClass */ "(UnknownSoyNamespace)",
          /* methodName */ templateName,
          srcLocation.getFileName(),
          srcLocation.getBeginLine());
    } else {
      // V2 soy templates.
      return new StackTraceElement(
          /* declaringClass */ soyFileHeaderInfo.namespace,
          // The partial template name begins with a '.' that causes the stack trace element to
          // print "namespace..templateName" otherwise.
          /* methodName */ partialTemplateName.substring(1),
          srcLocation.getFileName(),
          srcLocation.getBeginLine());
    }
  }
}
