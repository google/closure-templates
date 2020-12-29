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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.BaseUtils;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.base.internal.TemplateContentKind;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.exprtree.AbstractVarDefn;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.VarDefn;
import com.google.template.soy.soytree.CommandTagAttribute.CommandTagAttributesHolder;
import com.google.template.soy.soytree.SoyNode.ExprHolderNode;
import com.google.template.soy.soytree.SoyNode.RenderUnitNode;
import com.google.template.soy.soytree.defn.AttrParam;
import com.google.template.soy.soytree.defn.TemplateHeaderVarDefn;
import com.google.template.soy.soytree.defn.TemplateParam;
import com.google.template.soy.soytree.defn.TemplateStateVar;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.TemplateImportType;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Node representing a template.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public abstract class TemplateNode extends AbstractBlockCommandNode
    implements RenderUnitNode, ExprHolderNode, CommandTagAttributesHolder {

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
   *
   * <p>TODO(lukes): merge this object with SoyFileNode. The track nearly identical information.
   */
  public static class SoyFileHeaderInfo {
    /** A header with no aliases, used for parsing non-files. */
    public static final SoyFileHeaderInfo EMPTY = new SoyFileHeaderInfo("sample.ns");

    /** Map from aliases to namespaces for this file. */
    private final ImmutableMap<String, String> aliasToNamespaceMap;

    /** Map from aliases to namespaces for this file. */
    private final ImmutableList<AliasDeclaration> aliasDeclarations;

    /**
     * The names of all import symbols that can be referenced, (e.g. "foo" and "myBar" in: "import
     * {foo, bar as myBar} from ...").
     */
    private final ImmutableList<String> importSymbols;

    @Nullable private final DelPackageDeclaration delPackage;
    private final Priority priority;
    @Nullable private final String namespace;

    private final Set<String> usedAliases;

    public SoyFileHeaderInfo(
        ErrorReporter errorReporter,
        @Nullable DelPackageDeclaration delPackage,
        NamespaceDeclaration namespaceDeclaration,
        Collection<AliasDeclaration> aliases,
        Collection<String> importSymbols) {
      this(
          delPackage,
          namespaceDeclaration.getNamespace(),
          createAliasMap(errorReporter, namespaceDeclaration, aliases),
          ImmutableList.copyOf(aliases),
          ImmutableList.copyOf(importSymbols));
    }

    @VisibleForTesting
    public SoyFileHeaderInfo(String namespace) {
      this(null, namespace, ImmutableMap.of(), ImmutableList.of(), ImmutableList.of());
    }

    private SoyFileHeaderInfo(
        @Nullable DelPackageDeclaration delPackage,
        String namespace,
        ImmutableMap<String, String> aliasToNamespaceMap,
        ImmutableList<AliasDeclaration> aliasDeclarations,
        ImmutableList<String> importSymbols) {
      this.delPackage = delPackage;
      this.priority = (delPackage == null) ? Priority.STANDARD : Priority.HIGH_PRIORITY;
      this.namespace = namespace;
      this.aliasToNamespaceMap = aliasToNamespaceMap;
      this.aliasDeclarations = aliasDeclarations;
      this.importSymbols = importSymbols;
      this.usedAliases = new HashSet<>();
    }

    private SoyFileHeaderInfo(SoyFileHeaderInfo orig) {
      this.delPackage = orig.delPackage;
      this.priority = orig.priority;
      this.namespace = orig.namespace;
      this.aliasToNamespaceMap = orig.aliasToNamespaceMap;
      this.aliasDeclarations = orig.aliasDeclarations;
      this.importSymbols = orig.importSymbols;
      this.usedAliases = new HashSet<>(orig.usedAliases);
    }

    /** Resolves an potentially-aliased name against the aliases in this file. */
    public Identifier resolveAlias(Identifier identifier) {
      String fullName = identifier.identifier();
      SourceLocation sourceLocation = identifier.location();
      String firstIdent;
      String remainder;
      int i = fullName.indexOf('.');
      if (i > 0) {
        firstIdent = fullName.substring(0, i);
        remainder = fullName.substring(i);
      } else {
        firstIdent = fullName;
        remainder = "";
      }

      // If this references an import, don't try to resolve as an alias.
      if (importSymbols.contains(firstIdent)) {
        return identifier;
      }

      String alias = aliasToNamespaceMap.get(firstIdent);
      if (alias != null) {
        usedAliases.add(firstIdent);
      }
      return alias == null
          ? Identifier.create(fullName, sourceLocation)
          : Identifier.create(alias + remainder, fullName, sourceLocation);
    }

    public boolean hasAlias(String alias) {
      return aliasToNamespaceMap.containsKey(alias);
    }

    public boolean aliasUsed(String alias) {
      return usedAliases.contains(alias);
    }

    public String getNamespace() {
      return namespace;
    }

    public String getDelPackageName() {
      return delPackage == null ? null : delPackage.name().identifier();
    }

    public DelPackageDeclaration getDelPackage() {
      return delPackage;
    }

    public ImmutableList<AliasDeclaration> getAliases() {
      return aliasDeclarations;
    }

    public Priority getPriority() {
      return priority;
    }

    private static ImmutableMap<String, String> createAliasMap(
        ErrorReporter errorReporter,
        NamespaceDeclaration namespaceDeclaration,
        Collection<AliasDeclaration> aliases) {
      Map<String, String> map = Maps.newLinkedHashMap();
      String aliasForFileNamespace =
          BaseUtils.extractPartAfterLastDot(namespaceDeclaration.getNamespace());
      for (AliasDeclaration aliasDeclaration : aliases) {
        String aliasNamespace = aliasDeclaration.namespace().identifier();
        String alias = aliasDeclaration.alias().identifier();
        if (alias.equals(aliasForFileNamespace)
            && !aliasNamespace.equals(namespaceDeclaration.getNamespace())) {
          errorReporter.report(
              aliasDeclaration.alias().location(),
              INVALID_ALIAS_FOR_LAST_PART_OF_NAMESPACE,
              namespaceDeclaration.getNamespace(),
              aliasNamespace);
        }
        if (map.containsKey(alias)) {
          errorReporter.report(aliasDeclaration.alias().location(), DUPLICATE_ALIAS, alias);
        }
        map.put(alias, aliasNamespace);
      }
      return ImmutableMap.copyOf(map);
    }

    public SoyFileHeaderInfo copy() {
      return new SoyFileHeaderInfo(this);
    }
  }

  // -----------------------------------------------------------------------------------------------
  // TemplateNode body.

  /** Info from the containing Soy file's header declarations. */
  private final SoyFileHeaderInfo soyFileHeaderInfo;

  /** This template's name. */
  private final String templateName;

  /** This template's partial name. */
  private final Identifier partialTemplateName;

  /** Visibility of this template. */
  private final Visibility visibility;

  /** Whitespace handling mode for this template. */
  private final WhitespaceMode whitespaceMode;

  /** Strict mode context. Nonnull. */
  private final TemplateContentKind contentKind;

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

  /** Additional metadata for serialization and verification across templates. */
  private HtmlElementMetadataP templateMetadata = null;

  // TODO(b/19406885): Remove.
  private final String commandText;

  private final SourceLocation openTagLocation;

  private ImmutableList<TemplateHeaderVarDefn> headerParams;

  /** Used for formatting */
  private final List<CommandTagAttribute> attributes;

  // The presence of this means that we have annotated the template with {@attribute *}.
  private final SourceLocation allowExtraAttributesLoc;

  private ImmutableSet<String> reservedAttributes;

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
      TemplateNodeBuilder<?> nodeBuilder,
      String cmdName,
      SoyFileHeaderInfo soyFileHeaderInfo,
      Visibility visibility,
      ImmutableList<TemplateHeaderVarDefn> params) {
    super(nodeBuilder.getId(), nodeBuilder.sourceLocation, nodeBuilder.openTagLocation, cmdName);
    checkNotNull(params);
    this.headerParams = params == null ? ImmutableList.of() : params;
    this.soyFileHeaderInfo = soyFileHeaderInfo;
    this.templateName = nodeBuilder.getTemplateName();
    this.partialTemplateName = nodeBuilder.getPartialTemplateName();
    this.visibility = visibility;
    this.whitespaceMode = nodeBuilder.getWhitespaceMode();
    this.contentKind = checkNotNull(nodeBuilder.getContentKind());
    this.requiredCssNamespaces = nodeBuilder.getRequiredCssNamespaces();
    this.cssBaseNamespace = nodeBuilder.getCssBaseNamespace();
    this.soyDoc = nodeBuilder.getSoyDoc();
    this.soyDocDesc = nodeBuilder.getSoyDocDesc();
    this.strictHtml = computeStrictHtmlMode(nodeBuilder.getStrictHtmlDisabled());
    this.commandText = nodeBuilder.getCmdText().trim();
    this.openTagLocation = nodeBuilder.openTagLocation;
    this.attributes = nodeBuilder.getAttributes();
    this.allowExtraAttributesLoc = nodeBuilder.allowExtraAttributesLoc;
    this.reservedAttributes = ImmutableSet.of();
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  protected TemplateNode(TemplateNode orig, CopyState copyState) {
    super(orig, copyState);
    this.headerParams = copyParams(orig.headerParams, copyState);
    this.soyFileHeaderInfo = orig.soyFileHeaderInfo.copy();
    this.templateName = orig.templateName;
    this.partialTemplateName = orig.partialTemplateName;
    this.visibility = orig.visibility;
    this.whitespaceMode = orig.whitespaceMode;
    this.contentKind = orig.contentKind;
    this.requiredCssNamespaces = orig.requiredCssNamespaces;
    this.cssBaseNamespace = orig.cssBaseNamespace;
    this.soyDoc = orig.soyDoc;
    this.soyDocDesc = orig.soyDocDesc;
    this.strictHtml = orig.strictHtml;
    this.commandText = orig.commandText;
    this.openTagLocation = orig.openTagLocation;
    this.templateMetadata = orig.templateMetadata;
    this.attributes =
        orig.attributes.stream().map(c -> c.copy(copyState)).collect(toImmutableList());
    this.allowExtraAttributesLoc = orig.allowExtraAttributesLoc;
    this.reservedAttributes = orig.reservedAttributes;
  }

  private static ImmutableList<TemplateHeaderVarDefn> copyParams(
      ImmutableList<TemplateHeaderVarDefn> orig, CopyState copyState) {
    ImmutableList.Builder<TemplateHeaderVarDefn> newParams = ImmutableList.builder();
    for (TemplateHeaderVarDefn prev : orig) {
      TemplateHeaderVarDefn next = prev.copy(copyState);
      newParams.add(next);
      copyState.updateRefs(prev, next);
    }
    return newParams.build();
  }

  /** Returns info from the containing Soy file's header declarations. */
  public SoyFileHeaderInfo getSoyFileHeaderInfo() {
    return soyFileHeaderInfo;
  }

  /** Returns the name of the containing delegate package, or null if none. */
  public String getDelPackageName() {
    return soyFileHeaderInfo.getDelPackageName();
  }

  @Override
  public List<CommandTagAttribute> getAttributes() {
    return attributes;
  }

  public boolean getAllowExtraAttributes() {
    return allowExtraAttributesLoc != null;
  }

  public SourceLocation getAllowExtraAttributesLoc() {
    return allowExtraAttributesLoc;
  }

  public ImmutableSet<String> getReservedAttributes() {
    return reservedAttributes;
  }

  public void setReservedAttributes(ImmutableSet<String> reservedAttributes) {
    this.reservedAttributes = reservedAttributes;
  }

  /** Returns a template name suitable for display in user msgs. */
  public abstract String getTemplateNameForUserMsgs();

  /** Returns this template's name. */
  public String getTemplateName() {
    return templateName;
  }

  /** Returns the source location of the template's name (e.g. ".foo" in "{template .foo}". */
  public SourceLocation getTemplateNameLocation() {
    return partialTemplateName.location();
  }

  /**
   * This exists as part of the work in DesugarStateNodesPass to downlevel @state to @let. As part
   * of that, all state nodes should be cleared.
   */
  public void clearStateVars() {
    this.headerParams =
        this.headerParams.stream()
            .filter(p -> !(p instanceof TemplateStateVar))
            .collect(toImmutableList());
  }

  /** Returns this template's partial name. */
  public String getPartialTemplateName() {
    return partialTemplateName.identifier();
  }

  /** Returns this template's partial name, with any leading dot removed. */
  public String getLocalTemplateSymbol() {
    String s = partialTemplateName.identifier();
    return s != null && s.startsWith(".") ? s.substring(1) : s;
  }

  /** Returns the visibility of this template. */
  public Visibility getVisibility() {
    return visibility;
  }

  /** The location of the {(del)template ...} */
  @Override
  public SourceLocation getOpenTagLocation() {
    return this.openTagLocation;
  }

  /** Returns the whitespace handling mode for this template. */
  public WhitespaceMode getWhitespaceMode() {
    return whitespaceMode;
  }

  private String getDeclName(TemplateHeaderVarDefn headerVar) {
    if (headerVar instanceof TemplateStateVar) {
      return "@state";
    } else if (headerVar instanceof AttrParam) {
      return "@attribute";
    } else if (headerVar.isInjected()) {
      return "@inject";
    } else {
      return "@param";
    }
  }

  private boolean computeStrictHtmlMode(boolean strictHtmlDisabled) {
    if (strictHtmlDisabled) {
      // Use the value that is explicitly set in template.
      return false;
    } else if (!contentKind.getSanitizedContentKind().isHtml()) {
      // Non-HTML templates couldn't be strictHtml.
      return false;
    } else {
      // HTML templates have strictHtml enabled by default.
      return true;
    }
  }

  /** Returns if this template is in strict html mode. */
  public boolean isStrictHtml() {
    return strictHtml;
  }

  /** Returns the content kind for strict autoescaping. */
  @Override
  public SanitizedContentKind getContentKind() {
    return contentKind.getSanitizedContentKind();
  }

  /** Returns the template's content kind (e.g. "attributes", "element", "html", etc). */
  public TemplateContentKind getTemplateContentKind() {
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

  /** Clears the SoyDoc text, description, and param descriptions. */
  public void clearSoyDocStrings() {
    soyDoc = null;
    soyDocDesc = null;
  }

  /** Returns the SoyDoc, or null. */
  @Nullable
  public String getSoyDoc() {
    return soyDoc;
  }

  /** Returns the description portion of the SoyDoc, or null. */
  @Nullable
  public String getSoyDocDesc() {
    return soyDocDesc;
  }

  /** Returns the params from template header. */
  public ImmutableList<TemplateParam> getParams() {
    ImmutableList.Builder<TemplateParam> builder = ImmutableList.builder();
    for (TemplateHeaderVarDefn header : this.getHeaderParams()) {
      if (header instanceof TemplateParam && !header.isInjected()) {
        builder.add((TemplateParam) header);
      }
    }
    return builder.build();
  }

  public void setHtmlElementMetadata(HtmlElementMetadataP metadata) {
    this.templateMetadata = metadata;
  }

  public HtmlElementMetadataP getHtmlElementMetadata() {
    return templateMetadata;
  }

  /** Returns the injected params from template header. */
  public ImmutableList<TemplateParam> getInjectedParams() {
    ImmutableList.Builder<TemplateParam> builder = ImmutableList.builder();
    for (TemplateHeaderVarDefn header : this.getHeaderParams()) {
      if (header instanceof TemplateParam && header.isInjected()) {
        builder.add((TemplateParam) header);
      }
    }
    return builder.build();
  }

  /** Returns all params from template header, both regular and injected. */
  public ImmutableList<TemplateParam> getAllParams() {
    ImmutableList.Builder<TemplateParam> builder = ImmutableList.builder();
    for (TemplateHeaderVarDefn header : this.getHeaderParams()) {
      if (header instanceof TemplateParam) {
        builder.add((TemplateParam) header);
      }
    }
    return builder.build();
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
  public ImmutableList<ExprRootNode> getExprList() {
    ImmutableList.Builder<ExprRootNode> exprs = ImmutableList.builder();
    for (TemplateHeaderVarDefn param : getParams()) {
      ExprRootNode defaultValue = param.defaultValue();
      if (defaultValue != null) {
        exprs.add(defaultValue);
      }
    }
    for (CommandTagAttribute attribute : attributes) {
      if (attribute.hasExprValue()) {
        exprs.addAll(attribute.valueAsExprList());
      }
    }
    return exprs.build();
  }

  public void addParam(TemplateParam param) {
    headerParams =
        ImmutableList.<TemplateHeaderVarDefn>builder().addAll(headerParams).add(param).build();
  }

  public ImmutableList<TemplateHeaderVarDefn> getHeaderParams() {
    return this.headerParams;
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

    appendHeaderVarDecl(getHeaderParams(), sb);

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

  /** Add the Soy template syntax that declares `headerVar` to the string builder. */
  protected void appendHeaderVarDecl(
      ImmutableList<? extends TemplateHeaderVarDefn> headerVars, StringBuilder sb) {

    for (TemplateHeaderVarDefn headerVar : headerVars) {
      sb.append("  {").append(getDeclName(headerVar));
      if (!headerVar.isRequired()) {
        sb.append("?");
      }
      sb.append(" ")
          .append(headerVar.name())
          .append(": ")
          .append(headerVar.hasType() ? headerVar.type() : headerVar.getTypeNode())
          .append("}");
      if (headerVar.desc() != null) {
        sb.append("  /** ").append(headerVar.desc()).append(" */");
      }
      sb.append("\n");
    }
  }

  /**
   * Construct a StackTraceElement that will point to the given source location of the current
   * template.
   */
  public StackTraceElement createStackTraceElement(SourceLocation srcLocation) {
    return new StackTraceElement(
        /* declaringClass= */ soyFileHeaderInfo.namespace,
        // The partial template name begins with a '.' that causes the stack trace element to
        // print "namespace..templateName" otherwise.
        /* methodName= */ partialTemplateName.identifier().substring(1),
        srcLocation.getFileName(),
        srcLocation.getBeginLine());
  }

  public VarDefn asVarDefn() {
    return new TemplateVarDefn(
        getLocalTemplateSymbol(),
        getTemplateNameLocation(),
        TemplateImportType.create(getTemplateName()));
  }

  private static class TemplateVarDefn extends AbstractVarDefn {
    public TemplateVarDefn(
        String name, @Nullable SourceLocation nameLocation, @Nullable SoyType type) {
      super(name, nameLocation, type);
    }

    @Override
    public Kind kind() {
      return Kind.TEMPLATE;
    }

    @Override
    public boolean isInjected() {
      return false;
    }
  }
}
