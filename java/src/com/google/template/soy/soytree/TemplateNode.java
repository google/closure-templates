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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.basetree.SyntaxVersionBound;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.soytree.SoyNode.RenderUnitNode;
import com.google.template.soy.soytree.defn.HeaderParam;
import com.google.template.soy.soytree.defn.TemplateParam;
import com.google.template.soy.soytree.defn.TemplateParam.DeclLoc;

import java.util.List;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * Node representing a template.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author Kai Huang
 */
public abstract class TemplateNode extends AbstractBlockCommandNode implements RenderUnitNode {


  /** Priorities range from 0 to MAX_PRIORITY, inclusive. */
  public static final int MAX_PRIORITY = 1;


  /**
   * Info from the containing Soy file's {@code delpackage} and {@code namespace} declarations.
   *
   * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
   *
   * <p> Note: Currently, there are only 2 delegate priority values: 0 and 1. Delegate templates
   * that are not in a delegate package are given priority 0 (lowest). Delegate templates in a
   * delegate package are given priority 1. There is currently no syntax for the user to override
   * these default priority values.
   */
  @Immutable
  public static class SoyFileHeaderInfo {

    @Nullable public final String delPackageName;
    public final int defaultDelPriority;
    @Nullable public final String namespace;
    public final AutoescapeMode defaultAutoescapeMode;

    public SoyFileHeaderInfo(SoyFileNode soyFileNode) {
      this(soyFileNode.getDelPackageName(), soyFileNode.getNamespace(),
          soyFileNode.getDefaultAutoescapeMode());
    }

    public SoyFileHeaderInfo(String namespace) {
      this(null, namespace, AutoescapeMode.TRUE);
    }

    public SoyFileHeaderInfo(
        @Nullable String delPackageName, String namespace, AutoescapeMode defaultAutoescapeMode) {
      this.delPackageName = delPackageName;
      this.defaultDelPriority = (delPackageName == null) ? 0 : 1;
      this.namespace = namespace;
      this.defaultAutoescapeMode = defaultAutoescapeMode;
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

  /** Whether this template is private. */
  private final boolean isPrivate;

  /** The mode of autoescaping for this template. */
  private final AutoescapeMode autoescapeMode;

  /** Strict mode context. Nonnull iff autoescapeMode is strict. */
  @Nullable private final ContentKind contentKind;

  /** Required CSS namespaces. */
  private final ImmutableList<String> requiredCssNamespaces;

  /** The full SoyDoc, including the start/end tokens, or null. */
  private String soyDoc;

  /** The description portion of the SoyDoc (before declarations), or null. */
  private String soyDocDesc;

  /** The params from template header or SoyDoc. Null if no decls and no SoyDoc. */
  @Nullable private ImmutableList<TemplateParam> params;


  /**
   * Main constructor. This is package-private because Template*Node instances should be built using
   * the Template*NodeBuilder classes.
   *
   * @param id The id for this node.
   * @param syntaxVersionBound The lowest known upper bound (exclusive!) for the syntax version of
   *     this node.
   * @param cmdName The command name.
   * @param cmdText The command text.
   * @param soyFileHeaderInfo Info from the containing Soy file's header declarations.
   * @param templateName This template's name.
   * @param partialTemplateName This template's partial name. Only applicable for V2; null for V1.
   * @param templateNameForUserMsgs A string suitable for display in user msgs as the template name.
   * @param isPrivate Whether this template is private.
   * @param autoescapeMode The mode of autoescaping for this template.
   * @param contentKind Strict mode context. Nonnull iff autoescapeMode is strict.
   * @param requiredCssNamespaces CSS namespaces required to render the template.
   * @param soyDoc The full SoyDoc, including the start/end tokens, or null.
   * @param soyDocDesc The description portion of the SoyDoc (before declarations), or null.
   * @param params The params from template header or SoyDoc. Null if no decls and no SoyDoc.
   */
  TemplateNode(
      int id, @Nullable SyntaxVersionBound syntaxVersionBound, String cmdName, String cmdText,
      SoyFileHeaderInfo soyFileHeaderInfo, String templateName,
      @Nullable String partialTemplateName, String templateNameForUserMsgs, boolean isPrivate,
      AutoescapeMode autoescapeMode, ContentKind contentKind,
      ImmutableList<String> requiredCssNamespaces, String soyDoc, String soyDocDesc,
      @Nullable ImmutableList<TemplateParam> params) {

    super(id, cmdName, cmdText);
    maybeSetSyntaxVersionBound(syntaxVersionBound);
    this.soyFileHeaderInfo = soyFileHeaderInfo;
    this.templateName = templateName;
    this.partialTemplateName = partialTemplateName;
    this.templateNameForUserMsgs = templateNameForUserMsgs;
    this.isPrivate = isPrivate;
    this.autoescapeMode = autoescapeMode;
    this.contentKind = contentKind;
    this.requiredCssNamespaces = requiredCssNamespaces;
    this.soyDoc = soyDoc;
    this.soyDocDesc = soyDocDesc;
    this.params = params;
  }


  /**
   * Copy constructor.
   * @param orig The node to copy.
   */
  protected TemplateNode(TemplateNode orig) {
    super(orig);
    this.soyFileHeaderInfo = orig.soyFileHeaderInfo;  // immutable
    this.templateName = orig.templateName;
    this.partialTemplateName = orig.partialTemplateName;
    this.templateNameForUserMsgs = orig.templateNameForUserMsgs;
    this.isPrivate = orig.isPrivate;
    this.autoescapeMode = orig.autoescapeMode;
    this.contentKind = orig.contentKind;
    this.requiredCssNamespaces = orig.requiredCssNamespaces;
    this.soyDoc = orig.soyDoc;
    this.soyDocDesc = orig.soyDocDesc;
    this.params = orig.params;  // immutable
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
  @Nullable public String getPartialTemplateName() {
    return partialTemplateName;
  }


  /** Returns whether this template is private. */
  public boolean isPrivate() {
    return isPrivate;
  }


  /** Returns the mode of autoescaping, if any, done for this template. */
  public AutoescapeMode getAutoescapeMode() {
    return autoescapeMode;
  }


  /** Returns the content kind for strict autoescaping. Nonnull iff autoescapeMode is strict. */
  @Override @Nullable public ContentKind getContentKind() {
    return contentKind;
  }


  /**
   * Returns required CSS namespaces.
   *
   * CSS "namespaces" are monikers associated with CSS files that by convention, dot-separated
   * lowercase names. They don't correspond to CSS features, but are processed by external tools
   * that impose dependencies from templates to CSS.
   */
  public ImmutableList<String> getRequiredCssNamespaces() {
    return requiredCssNamespaces;
  }


  /** Clears the SoyDoc text, description, and param descriptions. */
  public void clearSoyDocStrings() {
    soyDoc = null;
    soyDocDesc = null;

    assert params != null;  // prevent warnings
    List<TemplateParam> newParams = Lists.newArrayListWithCapacity(params.size());
    for (TemplateParam origParam : params) {
      newParams.add(origParam.cloneEssential());
    }
    params = ImmutableList.copyOf(newParams);
  }


  /** Returns the SoyDoc, or null. */
  public String getSoyDoc() {
    return soyDoc;
  }


  /** Returns the description portion of the SoyDoc (before @param tags), or null. */
  public String getSoyDocDesc() {
    return soyDocDesc;
  }


  /** Returns the params from template header or SoyDoc. Null if no decls and no SoyDoc. */
  @Nullable public List<TemplateParam> getParams() {
    return params;
  }


  @Override public SoyFileNode getParent() {
    return (SoyFileNode) super.getParent();
  }


  @Override public String toSourceString() {

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
        sb.append("  {@param ").append(headerParam.name()).append(": ")
            .append(headerParam.typeSrc()).append("}");
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
      if (bodyLen != 1 && bodySb.charAt(bodyLen-1) == ' ') {
        bodySb.replace(bodyLen-1, bodyLen, "{sp}");
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
          srcLocation.getLineNumber());
    } else {
      // V2 soy templates.
      return new StackTraceElement(
          /* declaringClass */ soyFileHeaderInfo.namespace,
          // The partial template name begins with a '.' that causes the stack trace element to
          // print "namespace..templateName" otherwise.
          /* methodName */ partialTemplateName.substring(1),
          srcLocation.getFileName(),
          srcLocation.getLineNumber());
    }
  }
}
