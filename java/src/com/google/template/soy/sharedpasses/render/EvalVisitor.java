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

package com.google.template.soy.sharedpasses.render;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.template.soy.shared.internal.SharedRuntime.bitwiseAnd;
import static com.google.template.soy.shared.internal.SharedRuntime.bitwiseOr;
import static com.google.template.soy.shared.internal.SharedRuntime.bitwiseXor;
import static com.google.template.soy.shared.internal.SharedRuntime.checkMapFromListConstructorCondition;
import static com.google.template.soy.shared.internal.SharedRuntime.constructMapFromList;
import static com.google.template.soy.shared.internal.SharedRuntime.dividedBy;
import static com.google.template.soy.shared.internal.SharedRuntime.equal;
import static com.google.template.soy.shared.internal.SharedRuntime.lessThan;
import static com.google.template.soy.shared.internal.SharedRuntime.lessThanOrEqual;
import static com.google.template.soy.shared.internal.SharedRuntime.minus;
import static com.google.template.soy.shared.internal.SharedRuntime.mod;
import static com.google.template.soy.shared.internal.SharedRuntime.negative;
import static com.google.template.soy.shared.internal.SharedRuntime.plus;
import static com.google.template.soy.shared.internal.SharedRuntime.shiftLeft;
import static com.google.template.soy.shared.internal.SharedRuntime.shiftRight;
import static com.google.template.soy.shared.internal.SharedRuntime.soyServerKey;
import static com.google.template.soy.shared.internal.SharedRuntime.times;
import static com.google.template.soy.shared.internal.SharedRuntime.tripleEqual;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Iterables;
import com.google.errorprone.annotations.ForOverride;
import com.google.template.soy.base.SourceFilePath;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.data.Dir;
import com.google.template.soy.data.SoyDataException;
import com.google.template.soy.data.SoyLegacyObjectMap;
import com.google.template.soy.data.SoyList;
import com.google.template.soy.data.SoyMap;
import com.google.template.soy.data.SoyProtoValue;
import com.google.template.soy.data.SoyRecord;
import com.google.template.soy.data.SoyRecords;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueConverter;
import com.google.template.soy.data.SoyValueProvider;
import com.google.template.soy.data.SoyVisualElement;
import com.google.template.soy.data.SoyVisualElementData;
import com.google.template.soy.data.TemplateValue;
import com.google.template.soy.data.internal.DictImpl;
import com.google.template.soy.data.internal.ListImpl;
import com.google.template.soy.data.internal.RuntimeMapTypeTracker;
import com.google.template.soy.data.internal.SoyMapImpl;
import com.google.template.soy.data.restricted.BooleanData;
import com.google.template.soy.data.restricted.FloatData;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.NullData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.data.restricted.UndefinedData;
import com.google.template.soy.exprtree.AbstractReturningExprNodeVisitor;
import com.google.template.soy.exprtree.BooleanNode;
import com.google.template.soy.exprtree.DataAccessNode;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprNode.AccessChainComponentNode;
import com.google.template.soy.exprtree.ExprNode.Kind;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.FieldAccessNode;
import com.google.template.soy.exprtree.FloatNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.FunctionNode.ExternRef;
import com.google.template.soy.exprtree.IntegerNode;
import com.google.template.soy.exprtree.ItemAccessNode;
import com.google.template.soy.exprtree.ListComprehensionNode;
import com.google.template.soy.exprtree.ListComprehensionNode.ComprehensionVarDefn;
import com.google.template.soy.exprtree.ListLiteralNode;
import com.google.template.soy.exprtree.MapLiteralFromListNode;
import com.google.template.soy.exprtree.MapLiteralNode;
import com.google.template.soy.exprtree.MethodCallNode;
import com.google.template.soy.exprtree.NullNode;
import com.google.template.soy.exprtree.NullSafeAccessNode;
import com.google.template.soy.exprtree.OperatorNodes.AndOpNode;
import com.google.template.soy.exprtree.OperatorNodes.AssertNonNullOpNode;
import com.google.template.soy.exprtree.OperatorNodes.BitwiseAndOpNode;
import com.google.template.soy.exprtree.OperatorNodes.BitwiseOrOpNode;
import com.google.template.soy.exprtree.OperatorNodes.BitwiseXorOpNode;
import com.google.template.soy.exprtree.OperatorNodes.ConditionalOpNode;
import com.google.template.soy.exprtree.OperatorNodes.DivideByOpNode;
import com.google.template.soy.exprtree.OperatorNodes.EqualOpNode;
import com.google.template.soy.exprtree.OperatorNodes.GreaterThanOpNode;
import com.google.template.soy.exprtree.OperatorNodes.GreaterThanOrEqualOpNode;
import com.google.template.soy.exprtree.OperatorNodes.LessThanOpNode;
import com.google.template.soy.exprtree.OperatorNodes.LessThanOrEqualOpNode;
import com.google.template.soy.exprtree.OperatorNodes.MinusOpNode;
import com.google.template.soy.exprtree.OperatorNodes.ModOpNode;
import com.google.template.soy.exprtree.OperatorNodes.NegativeOpNode;
import com.google.template.soy.exprtree.OperatorNodes.NotEqualOpNode;
import com.google.template.soy.exprtree.OperatorNodes.NotOpNode;
import com.google.template.soy.exprtree.OperatorNodes.NullCoalescingOpNode;
import com.google.template.soy.exprtree.OperatorNodes.OrOpNode;
import com.google.template.soy.exprtree.OperatorNodes.PlusOpNode;
import com.google.template.soy.exprtree.OperatorNodes.ShiftLeftOpNode;
import com.google.template.soy.exprtree.OperatorNodes.ShiftRightOpNode;
import com.google.template.soy.exprtree.OperatorNodes.TimesOpNode;
import com.google.template.soy.exprtree.OperatorNodes.TripleEqualOpNode;
import com.google.template.soy.exprtree.OperatorNodes.TripleNotEqualOpNode;
import com.google.template.soy.exprtree.ProtoEnumValueNode;
import com.google.template.soy.exprtree.RecordLiteralNode;
import com.google.template.soy.exprtree.StringNode;
import com.google.template.soy.exprtree.TemplateLiteralNode;
import com.google.template.soy.exprtree.VarDefn;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.logging.LoggingFunction;
import com.google.template.soy.logging.SoyLogger;
import com.google.template.soy.msgs.SoyMsgBundle;
import com.google.template.soy.plugin.internal.JavaPluginExecContext;
import com.google.template.soy.plugin.java.PluginInstances;
import com.google.template.soy.plugin.java.RenderCssHelper;
import com.google.template.soy.plugin.java.restricted.JavaValueFactory;
import com.google.template.soy.plugin.java.restricted.MethodSignature;
import com.google.template.soy.plugin.java.restricted.SoyJavaSourceFunction;
import com.google.template.soy.shared.SoyCssRenamingMap;
import com.google.template.soy.shared.SoyIdRenamingMap;
import com.google.template.soy.shared.internal.BuiltinFunction;
import com.google.template.soy.shared.internal.BuiltinMethod;
import com.google.template.soy.shared.internal.DelTemplateSelector;
import com.google.template.soy.shared.restricted.SoyJavaFunction;
import com.google.template.soy.shared.restricted.SoyMethod;
import com.google.template.soy.shared.restricted.SoySourceFunctionMethod;
import com.google.template.soy.soytree.ExternNode;
import com.google.template.soy.soytree.JavaImplNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.defn.TemplateParam;
import com.google.template.soy.types.MapType;
import com.google.template.soy.types.SoyProtoType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyTypes;
import com.google.template.soy.types.UnionType;
import com.ibm.icu.util.ULocale;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Predicate;
import javax.annotation.Nullable;

/**
 * Visitor for evaluating the expression rooted at a given ExprNode.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * <p>{@link #exec} may be called on any expression. The result of evaluating the expression (in the
 * context of the {@code data} and {@code env} passed into the constructor) is returned as a {@code
 * SoyValue} object.
 */
public class EvalVisitor extends AbstractReturningExprNodeVisitor<SoyValue> {

  static final SoyVisualElement UNDEFINED_VE =
      SoyVisualElement.create(SoyLogger.UNDEFINED_VE_ID, SoyLogger.UNDEFINED_VE_NAME);

  static final SoyVisualElementData UNDEFINED_VE_DATA =
      SoyVisualElementData.create(UNDEFINED_VE, /* data= */ null);

  /** Defines how we deal with and produce UndefinedData instanes. */
  public enum UndefinedDataHandlingMode {
    /**
     * In 'bugged' mode we will produce instances of undefined data when dereferencing null instead
     * of throwing an exception.
     */
    BUGGED,
    /** Normal mode just means not doing the bugged behavior. */
    NORMAL
  }

  /** Interface for a factory that creates an EvalVisitor. */
  public interface EvalVisitorFactory {

    /**
     * Creates an EvalVisitor.
     *
     * @param env The current environment.
     * @param cssRenamingMap The CSS renaming map, or null if not applicable.
     * @param xidRenamingMap The XID renaming map, or null if not applicable.
     * @param pluginInstances The instances used for evaluating functions that call instance
     *     methods.
     * @return The newly created EvalVisitor instance.
     */
    EvalVisitor create(
        Environment env,
        @Nullable SoyCssRenamingMap cssRenamingMap,
        @Nullable SoyIdRenamingMap xidRenamingMap,
        @Nullable SoyMsgBundle msgBundle,
        boolean debugSoyTemplateInfo,
        PluginInstances pluginInstances,
        ImmutableTable<SourceFilePath, String, ImmutableList<ExternNode>> externs,
        DelTemplateSelector<TemplateNode> deltemplates,
        Predicate<String> activeModSelector);
  }

  /** The current environment. */
  private final Environment env;

  @Nullable private final SoyMsgBundle msgBundle;

  /** The current CSS renaming map. */
  private final SoyCssRenamingMap cssRenamingMap;

  /** The current XID renaming map. */
  private final SoyIdRenamingMap xidRenamingMap;

  /** If we should render additional HTML comments for runtime insepction. */
  private final boolean debugSoyTemplateInfo;

  /** The context for running plugins. */
  private final TofuPluginContext context;

  /**
   * The instances for functions that implement {@link SoyJavaSourceFunction} and call {@link
   * JavaValueFactory#callInstanceMethod}.
   */
  private final PluginInstances pluginInstances;

  /** How to manage old data handling bugs. */
  private final UndefinedDataHandlingMode undefinedDataHandlingMode;

  private final ImmutableTable<SourceFilePath, String, ImmutableList<ExternNode>> externs;
  private final DelTemplateSelector<TemplateNode> deltemplates;
  private final Predicate<String> activeModSelector;

  /**
   * @param env The current environment.
   * @param pluginInstances The instances used for evaluating functions that call instance methods.
   */
  protected EvalVisitor(
      Environment env,
      @Nullable SoyCssRenamingMap cssRenamingMap,
      @Nullable SoyIdRenamingMap xidRenamingMap,
      @Nullable SoyMsgBundle msgBundle,
      boolean debugSoyTemplateInfo,
      PluginInstances pluginInstances,
      UndefinedDataHandlingMode undefinedDataHandlingMode,
      ImmutableTable<SourceFilePath, String, ImmutableList<ExternNode>> externs,
      DelTemplateSelector<TemplateNode> deltemplates,
      Predicate<String> activeModSelector) {
    this.env = checkNotNull(env);
    this.msgBundle = msgBundle;
    this.cssRenamingMap = (cssRenamingMap == null) ? SoyCssRenamingMap.EMPTY : cssRenamingMap;
    this.xidRenamingMap = (xidRenamingMap == null) ? SoyCssRenamingMap.EMPTY : xidRenamingMap;
    this.debugSoyTemplateInfo = debugSoyTemplateInfo;
    this.context = new TofuPluginContext(msgBundle);
    this.pluginInstances = checkNotNull(pluginInstances);
    this.undefinedDataHandlingMode = checkNotNull(undefinedDataHandlingMode);
    this.externs = externs;
    this.deltemplates = deltemplates;
    this.activeModSelector = activeModSelector;
  }

  // -----------------------------------------------------------------------------------------------
  // Implementation for a dummy root node.

  @Override
  protected SoyValue visitExprRootNode(ExprRootNode node) {
    return visit(node.getRoot());
  }

  // -----------------------------------------------------------------------------------------------
  // Implementations for primitives.

  @Override
  protected SoyValue visitNullNode(NullNode node) {
    return NullData.INSTANCE;
  }

  @Override
  protected SoyValue visitBooleanNode(BooleanNode node) {
    return convertResult(node.getValue());
  }

  @Override
  protected SoyValue visitIntegerNode(IntegerNode node) {
    return convertResult(node.getValue());
  }

  @Override
  protected SoyValue visitFloatNode(FloatNode node) {
    return convertResult(node.getValue());
  }

  @Override
  protected SoyValue visitStringNode(StringNode node) {
    return convertResult(node.getValue());
  }

  @Override
  protected SoyValue visitProtoEnumValueNode(ProtoEnumValueNode node) {
    return convertResult(node.getValue());
  }

  // -----------------------------------------------------------------------------------------------
  // Implementations for collections.

  @Override
  protected SoyValue visitListLiteralNode(ListLiteralNode node) {
    List<SoyValue> values = this.visitChildren(node);
    return ListImpl.forProviderList(values);
  }

  @Override
  protected SoyValue visitListComprehensionNode(ListComprehensionNode node) {
    ExprNode listExpr = node.getListExpr();
    SoyValue listValue = visit(listExpr);
    if (!(listValue instanceof SoyList)) {
      throw RenderException.create(String.format("List expression is not a list: %s", listValue));
    }
    ExprNode mapExpr = node.getListItemTransformExpr();
    ExprNode filterExpr = node.getFilterExpr();
    ComprehensionVarDefn itemName = node.getListIterVar();
    ImmutableList.Builder<SoyValueProvider> mappedValues = ImmutableList.builder();
    List<? extends SoyValueProvider> list = ((SoyList) listValue).asJavaList();
    for (int i = 0; i < list.size(); i++) {
      env.bind(itemName, list.get(i));
      if (node.getIndexVar() != null) {
        env.bind(node.getIndexVar(), SoyValueConverter.INSTANCE.convert(i));
      }
      if (filterExpr != null) {
        if (!visit(filterExpr).coerceToBoolean()) {
          continue;
        }
      }
      SoyValue mappedValue = visit(mapExpr);
      mappedValues.add(mappedValue);
    }
    return ListImpl.forProviderList(mappedValues.build());
  }

  @Override
  protected SoyValue visitRecordLiteralNode(RecordLiteralNode node) {
    int numItems = node.numChildren();

    Map<String, SoyValue> map = new LinkedHashMap<>();
    for (int i = 0; i < numItems; i++) {
      map.put(node.getKey(i).identifier(), visit(node.getChild(i)));
    }
    return DictImpl.forProviderMap(map, RuntimeMapTypeTracker.Type.LEGACY_OBJECT_MAP_OR_RECORD);
  }

  @Override
  protected SoyValue visitMapLiteralNode(MapLiteralNode node) {
    int numItems = node.numChildren() / 2;

    Map<SoyValue, SoyValue> map = new HashMap<>();
    for (int i = 0; i < numItems; i++) {
      SoyValue key = visit(node.getChild(2 * i));
      SoyValue value = visit(node.getChild(2 * i + 1));
      if (isNullOrUndefinedBase(key)) {
        throw RenderException.create(String.format("null key in entry: null=%s", value));
      }
      map.put(key, value);
    }
    return SoyMapImpl.forProviderMap(map);
  }

  @Override
  protected SoyValue visitMapLiteralFromListNode(MapLiteralFromListNode node) {
    ExprNode listExpr = node.getListExpr();
    SoyValue listValue = visit(listExpr);
    try {
      checkMapFromListConstructorCondition(
          listValue instanceof SoyList, listValue, OptionalInt.empty());

      List<? extends SoyValueProvider> list = ((SoyList) listValue).asJavaList();
      return constructMapFromList(list);
    } catch (IllegalArgumentException e) {
      throw RenderException.create(
          e.getMessage() + " at " + node.getListExpr().getSourceLocation(), e);
    }
  }

  // -----------------------------------------------------------------------------------------------
  // Implementations for data references.

  @Override
  protected SoyValue visitVarRefNode(VarRefNode node) {
    if (node.getDefnDecl().kind() == VarDefn.Kind.STATE) {
      throw new AssertionError(); // should have been desugared
    } else {
      SoyValue value = env.getVar(node.getDefnDecl());
      if (node.getDefnDecl().kind() == VarDefn.Kind.PARAM
          && ((TemplateParam) node.getDefnDecl()).hasDefault()
          && (UndefinedData.INSTANCE == value)) {
        // Use the default value if it has one and the parameter is undefined.
        value = visit(((TemplateParam) node.getDefnDecl()).defaultValue());
      }
      return value;
    }
  }

  @Override
  protected SoyValue visitDataAccessNode(DataAccessNode node) {
    // All null safe accesses should've already been converted to NullSafeAccessNodes.
    checkArgument(!node.isNullSafe());
    SoyValue base = visit(node.getBaseExprChild());
    return visitDataAccessNode(node, base, /* nullSafe= */ false);
  }

  private SoyValue visitDataAccessNode(DataAccessNode node, SoyValue base, boolean nullSafe) {
    SoyValue result;
    switch (node.getKind()) {
      case FIELD_ACCESS_NODE:
        result = visitFieldAccessNode((FieldAccessNode) node, base, nullSafe);
        break;
      case ITEM_ACCESS_NODE:
        result = visitItemAccessNode((ItemAccessNode) node, base, nullSafe);
        break;
      case METHOD_CALL_NODE:
        result = visitMethodCallNode((MethodCallNode) node, base);
        break;
      default:
        throw new AssertionError(node.getKind());
    }
    return result;
  }

  @Override
  protected SoyValue visitNullSafeAccessNode(NullSafeAccessNode nullSafeAccessNode) {
    SoyValue value = visit(nullSafeAccessNode.getBase());
    ExprNode dataAccess = nullSafeAccessNode.getDataAccess();
    while (!isNullOrUndefinedBase(value) && dataAccess.getKind() == Kind.NULL_SAFE_ACCESS_NODE) {
      NullSafeAccessNode node = (NullSafeAccessNode) dataAccess;
      value = accumulateDataAccess((DataAccessNode) node.getBase(), value);
      dataAccess = node.getDataAccess();
    }
    if (isNullOrUndefinedBase(value)) {
      return NullData.INSTANCE;
    }
    return accumulateDataAccessTail((AccessChainComponentNode) dataAccess, value);
  }

  private SoyValue accumulateDataAccess(DataAccessNode dataAccessNode, SoyValue base) {
    boolean accessChain = false;
    if (dataAccessNode.getBaseExprChild() instanceof DataAccessNode) {
      base = accumulateDataAccess((DataAccessNode) dataAccessNode.getBaseExprChild(), base);
      accessChain = true;
    }
    return visitDataAccessNode(dataAccessNode, base, !accessChain);
  }

  private SoyValue accumulateDataAccessTail(
      AccessChainComponentNode dataAccessNode, SoyValue base) {
    if (dataAccessNode.getKind() == ExprNode.Kind.ASSERT_NON_NULL_OP_NODE) {
      AssertNonNullOpNode assertNonNull = (AssertNonNullOpNode) dataAccessNode;
      dataAccessNode = (AccessChainComponentNode) assertNonNull.getChild(0);
    }
    return accumulateDataAccess((DataAccessNode) dataAccessNode, base);
  }

  private SoyValue visitFieldAccessNode(
      FieldAccessNode fieldAccess, SoyValue base, boolean nullSafe) {
    // All null safe accesses should've already been converted to NullSafeAccessNodes.
    checkArgument(!fieldAccess.isNullSafe());

    SoySourceFunctionMethod method = fieldAccess.getSoyMethod();
    if (method != null) {
      return computeFunctionHelper(
          ImmutableList.of(base), JavaPluginExecContext.forFieldAccessNode(fieldAccess, method));
    }

    // attempting field access on non-SoyRecord
    if (!(base instanceof SoyRecord) && !(base instanceof SoyProtoValue)) {
      if (nullSafe) {
        if (!isNullOrUndefinedBase(base)) {
          throw RenderException.create(
              String.format(
                  "While evaluating \"%s\", encountered non-record just before accessing \"%s\".",
                  fieldAccess.toSourceString(), fieldAccess.getSourceStringSuffix()));
        }
      }

      // This behavior is not ideal, but needed for compatibility with existing code.
      // TODO: If feasible, find and fix existing instances, then throw RenderException here.
      if (undefinedDataHandlingMode == UndefinedDataHandlingMode.BUGGED) {
        return UndefinedData.INSTANCE;
      }
      if (isNullOrUndefinedBase(base)) {
        throw RenderException.create(
            String.format("Attempted to access field \"%s\" of null.", fieldAccess.getFieldName()));
      }
      throw RenderException.create(
          String.format(
              "Attempted to access field \"%s\" of non-record type: %s.",
              fieldAccess.getFieldName(), base.getClass().getName()));
    }

    // If the static type is a proto, access it using proto semantics
    if (isProtoOrUnionOfProtos(fieldAccess.getBaseExprChild().getType())) {
      // getProtoFieldOrNull can handle all fields and is only nullish for fields with presence.
      return ((SoyProtoValue) base).getProtoFieldOrNull(fieldAccess.getFieldName());
    }
    maybeMarkBadProtoAccess(fieldAccess, base);
    // base is a valid SoyRecord: get value
    SoyValue value = ((SoyRecord) base).getField(fieldAccess.getFieldName());

    // Note that this code treats value of null and value of NullData differently. Only the latter
    // will trigger this check, which is partly why places like
    // SoyProtoValue.getFieldProviderInternal() and AbstractDict.getField() return null instead
    // of NullData.
    // TODO(user): Consider cleaning up the null / NullData inconsistencies.
    if (value != null
        && !TofuTypeChecks.isInstance(
            fieldAccess.getType(), value, fieldAccess.getSourceLocation())) {
      throw RenderException.create(
          String.format(
              "Expected value of type '%s', but actual type was '%s'.",
              fieldAccess.getType(), value.getClass().getSimpleName()));
    }

    return (value != null)
        ? value
        : (undefinedDataHandlingMode == UndefinedDataHandlingMode.BUGGED
            ? UndefinedData.INSTANCE
            : NullData.INSTANCE);
  }

  private static boolean isProtoOrUnionOfProtos(SoyType type) {
    if (type.getKind() == SoyType.Kind.PROTO) {
      return true;
    }
    if (type.getKind() == SoyType.Kind.UNION) {
      for (SoyType memberType : ((UnionType) type).getMembers()) {
        if (memberType.getKind() != SoyType.Kind.PROTO
            && memberType.getKind() != SoyType.Kind.NULL) {
          return false;
        }
      }
      return true;
    }
    return false;
  }

  private SoyValue visitItemAccessNode(ItemAccessNode itemAccess, SoyValue base, boolean nullSafe) {
    // All null safe accesses should've already been converted to NullSafeAccessNodes.
    checkArgument(!itemAccess.isNullSafe());
    // attempting item access on non-SoyMap
    if (!(base instanceof SoyLegacyObjectMap || base instanceof SoyMap)) {
      if (nullSafe) {
        if (!isNullOrUndefinedBase(base)) {
          throw RenderException.create(
              String.format(
                  "While evaluating \"%s\", encountered non-map/list just before accessing \"%s\".",
                  itemAccess.toSourceString(), itemAccess.getSourceStringSuffix()));
        }
      }

      // This behavior is not ideal, but needed for compatibility with existing code.
      // TODO: If feasible, find and fix existing instances, then throw RenderException here.
      if (undefinedDataHandlingMode == UndefinedDataHandlingMode.BUGGED) {
        return UndefinedData.INSTANCE;
      }
      if (isNullOrUndefinedBase(base)) {
        throw RenderException.create(
            String.format(
                "Attempted to access item \"%s\" of null.", itemAccess.getSourceStringSuffix()));
      }
      throw RenderException.create(
          String.format(
              "While evaluating \"%s\", encountered non-map/list just before accessing \"%s\".",
              itemAccess.toSourceString(), itemAccess.getSourceStringSuffix()));
    }

    // base is a valid SoyMap or SoyLegacyObjectMap: get value
    maybeMarkBadProtoAccess(itemAccess, base);
    SoyValue key = visit(itemAccess.getKeyExprChild());

    SoyType baseType = SoyTypes.removeNull(itemAccess.getBaseExprChild().getType());

    // We need to know whether to invoke the SoyMap or SoyLegacyObjectMap method.
    // An instanceof check on the runtime value of base is insufficient, since
    // DictImpl implements both interfaces. Instead, look at the declared type of the base
    // expression.
    boolean shouldUseNewMap = MapType.ANY_MAP.isAssignableFromStrict(baseType);
    SoyValue value =
        shouldUseNewMap ? ((SoyMap) base).get(key) : ((SoyLegacyObjectMap) base).getItem(key);

    if (value != null
        && !TofuTypeChecks.isInstance(
            itemAccess.getType(), value, itemAccess.getSourceLocation())) {
      throw RenderException.create(
          String.format(
              "Expected value of type '%s', but actual type was '%s'.",
              itemAccess.getType(), value.getClass().getSimpleName()));
    }

    if (value != null) {
      return value;
    } else if (shouldUseNewMap || undefinedDataHandlingMode != UndefinedDataHandlingMode.BUGGED) {
      // UndefinedData is a misfeature. The new map type should return null for failed lookups.
      return NullData.INSTANCE;
    } else {
      return UndefinedData.INSTANCE;
    }
  }

  /**
   * If the value is a proto, then set the current access location since we are about to access it
   * incorrectly.
   */
  private static void maybeMarkBadProtoAccess(ExprNode expr, SoyValue value) {
    if (value instanceof SoyProtoValue) {
      ((SoyProtoValue) value).setAccessLocationKey(expr.getSourceLocation());
    }
  }

  private SoyValue visitMethodCallNode(MethodCallNode methodNode, SoyValue base) {
    // All null safe accesses should've already been converted to NullSafeAccessNodes.
    checkArgument(!methodNode.isNullSafe());
    // TODO(b/147372851): Handle case when the implementation of the method cannot be determined
    // from the base type during compile time and the node has multiple SoySourceFunctions.
    checkArgument(methodNode.isMethodResolved());

    // Never allow a null method receiver.
    base = assertNotNull(base, methodNode.getBaseExprChild());

    SoyMethod method = methodNode.getSoyMethod();
    if (method instanceof BuiltinMethod) {
      BuiltinMethod builtinMethod = (BuiltinMethod) method;
      switch (builtinMethod) {
        case GET_EXTENSION:
          return ((SoyProtoValue) base)
              .getProtoField(BuiltinMethod.getProtoExtensionIdFromMethodCall(methodNode));
        case HAS_EXTENSION:
          return BooleanData.forValue(
              ((SoyProtoValue) base)
                  .hasProtoField(BuiltinMethod.getProtoExtensionIdFromMethodCall(methodNode)));
        case GET_READONLY_EXTENSION:
          return ((SoyProtoValue) base)
              .getReadonlyProtoField(BuiltinMethod.getProtoExtensionIdFromMethodCall(methodNode));
        case HAS_PROTO_FIELD:
          return BooleanData.forValue(
              ((SoyProtoValue) base)
                  .hasProtoField(BuiltinMethod.getProtoFieldNameFromMethodCall(methodNode)));
        case GET_PROTO_FIELD:
          return ((SoyProtoValue) base)
              .getProtoField(BuiltinMethod.getProtoFieldNameFromMethodCall(methodNode));
        case GET_READONLY_PROTO_FIELD:
          return ((SoyProtoValue) base)
              .getReadonlyProtoField(BuiltinMethod.getProtoFieldNameFromMethodCall(methodNode));
        case GET_PROTO_FIELD_OR_UNDEFINED:
          return ((SoyProtoValue) base)
              .getProtoFieldOrNull(BuiltinMethod.getProtoFieldNameFromMethodCall(methodNode));
        case BIND:
          TemplateValue template = (TemplateValue) base;
          SoyRecord params = (SoyRecord) visit(methodNode.getParams().get(0));
          return TemplateValue.createWithBoundParameters(
              template.getTemplateName(),
              template.getBoundParameters().isPresent()
                  ? SoyRecords.merge(template.getBoundParameters().get(), params)
                  : params);
      }
    } else if (method instanceof SoySourceFunctionMethod) {
      SoySourceFunctionMethod sourceMethod = (SoySourceFunctionMethod) method;
      List<SoyValue> args = new ArrayList<>(methodNode.numParams() + 1);
      args.add(base);
      methodNode.getParams().forEach(n -> args.add(visit(n)));
      return computeFunctionHelper(
          args, JavaPluginExecContext.forMethodCallNode(methodNode, sourceMethod));
    }
    throw new AssertionError(method.getClass());
  }

  // Returns true if the base SoyValue of a data access chain is null or undefined.
  private static boolean isNullOrUndefinedBase(SoyValue base) {
    return base == null || base instanceof NullData || base instanceof UndefinedData;
  }

  // -----------------------------------------------------------------------------------------------
  // Implementations for operators.

  @Override
  protected SoyValue visitNegativeOpNode(NegativeOpNode node) {
    return negative(visit(node.getChild(0)));
  }

  @Override
  protected SoyValue visitNotOpNode(NotOpNode node) {

    SoyValue operand = visit(node.getChild(0));
    return convertResult(!operand.coerceToBoolean());
  }

  @Override
  protected SoyValue visitTimesOpNode(TimesOpNode node) {
    return times(visit(node.getChild(0)), visit(node.getChild(1)));
  }

  @Override
  protected SoyValue visitDivideByOpNode(DivideByOpNode node) {
    return FloatData.forValue(dividedBy(visit(node.getChild(0)), visit(node.getChild(1))));
  }

  @Override
  protected SoyValue visitModOpNode(ModOpNode node) {
    SoyValue operand0 = visit(node.getChild(0));
    SoyValue operand1 = visit(node.getChild(1));
    return mod(operand0, operand1);
  }

  @Override
  protected SoyValue visitPlusOpNode(PlusOpNode node) {
    return plus(visit(node.getChild(0)), visit(node.getChild(1)));
  }

  @Override
  protected SoyValue visitMinusOpNode(MinusOpNode node) {
    return minus(visit(node.getChild(0)), visit(node.getChild(1)));
  }

  @Override
  protected SoyValue visitShiftLeftOpNode(ShiftLeftOpNode node) {
    return shiftLeft(visit(node.getChild(0)), visit(node.getChild(1)));
  }

  @Override
  protected SoyValue visitShiftRightOpNode(ShiftRightOpNode node) {
    return shiftRight(visit(node.getChild(0)), visit(node.getChild(1)));
  }

  @Override
  protected SoyValue visitBitwiseOrOpNode(BitwiseOrOpNode node) {
    return bitwiseOr(visit(node.getChild(0)), visit(node.getChild(1)));
  }

  @Override
  protected SoyValue visitBitwiseXorOpNode(BitwiseXorOpNode node) {
    return bitwiseXor(visit(node.getChild(0)), visit(node.getChild(1)));
  }

  @Override
  protected SoyValue visitBitwiseAndOpNode(BitwiseAndOpNode node) {
    return bitwiseAnd(visit(node.getChild(0)), visit(node.getChild(1)));
  }

  @Override
  protected SoyValue visitLessThanOpNode(LessThanOpNode node) {
    return BooleanData.forValue(lessThan(visit(node.getChild(0)), visit(node.getChild(1))));
  }

  @Override
  protected SoyValue visitGreaterThanOpNode(GreaterThanOpNode node) {
    // note the argument reversal
    return BooleanData.forValue(lessThan(visit(node.getChild(1)), visit(node.getChild(0))));
  }

  @Override
  protected SoyValue visitLessThanOrEqualOpNode(LessThanOrEqualOpNode node) {
    return BooleanData.forValue(lessThanOrEqual(visit(node.getChild(0)), visit(node.getChild(1))));
  }

  @Override
  protected SoyValue visitGreaterThanOrEqualOpNode(GreaterThanOrEqualOpNode node) {
    // note the argument reversal
    return BooleanData.forValue(lessThanOrEqual(visit(node.getChild(1)), visit(node.getChild(0))));
  }

  @Override
  protected SoyValue visitEqualOpNode(EqualOpNode node) {

    return convertResult(equal(visit(node.getChild(0)), visit(node.getChild(1))));
  }

  @Override
  protected SoyValue visitNotEqualOpNode(NotEqualOpNode node) {
    return convertResult(!equal(visit(node.getChild(0)), visit(node.getChild(1))));
  }

  @Override
  protected SoyValue visitTripleEqualOpNode(TripleEqualOpNode node) {
    return convertResult(tripleEqual(visit(node.getChild(0)), visit(node.getChild(1))));
  }

  @Override
  protected SoyValue visitTripleNotEqualOpNode(TripleNotEqualOpNode node) {
    return convertResult(!tripleEqual(visit(node.getChild(0)), visit(node.getChild(1))));
  }

  @Override
  protected SoyValue visitAndOpNode(AndOpNode node) {

    // Note: Short-circuit evaluation.
    SoyValue operand0 = visit(node.getChild(0));
    if (!operand0.coerceToBoolean()) {
      return convertResult(false);
    } else {
      SoyValue operand1 = visit(node.getChild(1));
      return convertResult(operand1.coerceToBoolean());
    }
  }

  @Override
  protected SoyValue visitOrOpNode(OrOpNode node) {

    // Note: Short-circuit evaluation.
    SoyValue operand0 = visit(node.getChild(0));
    if (operand0.coerceToBoolean()) {
      return convertResult(true);
    } else {
      SoyValue operand1 = visit(node.getChild(1));
      return convertResult(operand1.coerceToBoolean());
    }
  }

  @Override
  protected SoyValue visitConditionalOpNode(ConditionalOpNode node) {

    // Note: We only evaluate the part that we need.
    SoyValue operand0 = visit(node.getChild(0));
    if (operand0.coerceToBoolean()) {
      return visit(node.getChild(1));
    } else {
      return visit(node.getChild(2));
    }
  }

  @Override
  protected SoyValue visitNullCoalescingOpNode(NullCoalescingOpNode node) {
    SoyValue operand0 = visit(node.getChild(0));
    // identical to the implementation of != null
    if (operand0 instanceof NullData || operand0 instanceof UndefinedData) {
      return visit(node.getChild(1));
    }
    return operand0;
  }

  @Override
  protected SoyValue visitAssertNonNullOpNode(AssertNonNullOpNode node) {
    return visit(Iterables.getOnlyElement(node.getChildren()));
  }

  // -----------------------------------------------------------------------------------------------
  // Implementations for functions.

  @Override
  protected SoyValue visitFunctionNode(FunctionNode node) {
    Object soyFunction = node.getSoyFunction();
    // Handle nonplugin functions.
    if (soyFunction instanceof BuiltinFunction) {
      BuiltinFunction nonpluginFn = (BuiltinFunction) soyFunction;
      switch (nonpluginFn) {
        case IS_PARAM_SET:
          return visitIsSetFunction(node);
        case CHECK_NOT_NULL:
          return assertNotNull(node.getChild(0));
        case CSS:
          return visitCssFunction(node);
        case XID:
          return visitXidFunction(node);
        case SOY_SERVER_KEY:
          return visitSoyServerKeyFunction(node);
        case IS_PRIMARY_MSG_IN_USE:
          return visitIsPrimaryMsgInUseFunction(node);
        case PROTO_INIT:
          return visitProtoInitFunction(node);
        case UNKNOWN_JS_GLOBAL:
        case LEGACY_DYNAMIC_TAG:
          throw new UnsupportedOperationException(
              "the "
                  + nonpluginFn.getName()
                  + " function can't be used in templates compiled to Java");
        case TO_FLOAT:
          return visitToFloatFunction(node);
        case DEBUG_SOY_TEMPLATE_INFO:
          return BooleanData.forValue(debugSoyTemplateInfo);
        case VE_DATA:
          return UNDEFINED_VE_DATA;
        case VE_DEF:
          return UNDEFINED_VE;
        case MSG_WITH_ID:
        case ID_HOLDER:
        case UNIQUE_ATTRIBUTE:
        case REMAINDER:
          // should have been removed earlier in the compiler
          throw new AssertionError();
      }
      throw new AssertionError();
    } else if (soyFunction instanceof SoyJavaFunction) {
      List<SoyValue> args = this.visitChildren(node);
      SoyJavaFunction fn = (SoyJavaFunction) soyFunction;
      // Note: Arity has already been checked by CheckFunctionCallsVisitor.
      return computeFunctionHelper(fn, args, node);
    } else if (soyFunction instanceof SoyJavaSourceFunction) {
      List<SoyValue> args = this.visitChildren(node);
      SoyJavaSourceFunction fn = (SoyJavaSourceFunction) soyFunction;
      // Note: Arity has already been checked by CheckFunctionCallsVisitor.
      return computeFunctionHelper(args, JavaPluginExecContext.forFunctionNode(node, fn));
    } else if (soyFunction instanceof LoggingFunction) {
      return StringData.forValue(((LoggingFunction) soyFunction).getPlaceholder());
    } else if (soyFunction instanceof ExternRef) {
      return visitExternRef(node, (ExternRef) soyFunction);
    } else {
      throw RenderException.createF(
          "Failed to find Soy function with name '%s' (function call \"%s\").",
          node.getStaticFunctionName(), node.toSourceString());
    }
  }

  private SoyValue visitExternRef(FunctionNode node, ExternRef soyFunction) {
    ImmutableList<ExternNode> externNodes = externs.get(soyFunction.path(), soyFunction.name());
    if (externNodes == null) {
      externNodes = ImmutableList.of();
    }
    Optional<ExternNode> matching =
        externNodes.stream().filter(e -> e.getType().equals(soyFunction.signature())).findFirst();
    if (!matching.isPresent()) {
      throw RenderException.createF(
          "No extern named '%s' matching signature %s.",
          soyFunction.name(), soyFunction.signature());
    }
    Optional<JavaImplNode> impl = matching.get().getJavaImpl();
    if (!impl.isPresent()) {
      throw RenderException.createF("No java implementation for extern '%s'.", soyFunction.name());
    }
    JavaImplNode java = impl.get();
    int numJavaParams = java.params().size();
    MethodSignature method;
    try {
      Class<?> rt = MethodSignature.forName(java.returnType());
      Class<?>[] args = new Class<?>[numJavaParams];
      for (int i = 0; i < numJavaParams; i++) {
        args[i] = MethodSignature.forName(java.params().get(i));
      }
      method =
          java.isInterface()
              ? MethodSignature.createInterfaceMethod(java.className(), java.methodName(), rt, args)
              : MethodSignature.create(java.className(), java.methodName(), rt, args);
    } catch (ClassNotFoundException e) {
      throw RenderException.create("Required Java runtime class not found.", e);
    }

    List<ExprNode> params = node.getParams();
    TofuJavaValue[] javaValues = new TofuJavaValue[numJavaParams];
    for (int i = 0; i < params.size(); i++) {
      ExprNode param = params.get(i);
      javaValues[i] = TofuJavaValue.forSoyValue(visit(param), param.getSourceLocation());
    }
    // Add implicit params.
    for (int i = params.size(); i < numJavaParams; i++) {
      Class<?> implicitType = method.arguments().get(i);
      if (implicitType == Dir.class) {
        javaValues[i] = TofuJavaValue.forRaw(context.getBidiGlobalDir().toDir());
      } else if (implicitType == ULocale.class) {
        javaValues[i] = context.getULocale();
      } else if (implicitType == RenderCssHelper.class) {
        javaValues[i] = TofuJavaValue.forRaw(getRenderCssHelper());
      } else {
        throw new IllegalArgumentException(implicitType.getName());
      }
    }

    TofuValueFactory factory =
        new TofuValueFactory(
            node.getSourceLocation(),
            java.className(), // Use java class as instance key.
            pluginInstances,
            soyFunction.signature());
    TofuJavaValue value =
        java.isStatic()
            ? factory.callStaticMethod(method, node.getType(), javaValues)
            : factory.callInstanceMethod(method, node.getType(), javaValues);
    return value.soyValue();
  }

  private RenderCssHelper getRenderCssHelper() {
    return (delTemplate, variant) -> {
      TemplateNode data = deltemplates.selectTemplate(delTemplate, variant, activeModSelector);
      return data != null ? data.getTemplateName() : null;
    };
  }

  protected SoyValue visitProtoInitFunction(FunctionNode node) {
    // The downcast is safe because if it was anything else, compilation would have already failed.
    SoyProtoType soyProto = (SoyProtoType) node.getType();
    ImmutableList<Identifier> paramNames = node.getParamNames();
    SoyProtoValue.Builder builder = new SoyProtoValue.Builder(soyProto.getDescriptor());
    for (int i = 0; i < node.numChildren(); i++) {
      SoyValue visit = visit(node.getChild(i));
      // null means don't assign
      if (visit instanceof NullData || visit instanceof UndefinedData) {
        continue;
      }
      builder.setField(paramNames.get(i).identifier(), visit);
    }
    return builder.build();
  }

  private SoyValue assertNotNull(ExprNode child) {
    return assertNotNull(visit(child), child);
  }

  private static SoyValue assertNotNull(SoyValue value, ExprNode node) {
    if (value instanceof NullData || value instanceof UndefinedData) {
      throw new SoyDataException(node.toSourceString() + " is null");
    }
    return value;
  }

  /**
   * Protected helper for {@code computeFunction}.
   *
   * @param fn The function object.
   * @param args The arguments to the function.
   * @param fnNode The function node. Only used for error reporting.
   * @return The result of the function called on the given arguments.
   */
  @ForOverride
  protected SoyValue computeFunctionHelper(
      SoyJavaFunction fn, List<SoyValue> args, FunctionNode fnNode) {
    try {
      return fn.computeForJava(args);
    } catch (Exception e) {
      throw RenderException.create(
          "While computing function \"" + fnNode.toSourceString() + "\": " + e.getMessage(), e);
    }
  }

  /**
   * Protected helper for {@code computeFunction}.
   *
   * @param args The arguments to the function.
   * @param fnNode The function node. Only used for error reporting.
   * @return The result of the function called on the given arguments.
   */
  @ForOverride
  protected SoyValue computeFunctionHelper(List<SoyValue> args, JavaPluginExecContext fnNode) {
    try {
      return new TofuValueFactory(fnNode, pluginInstances)
          .computeForJava(fnNode.getSourceFunction(), args, context);
    } catch (Exception e) {
      throw RenderException.create(
          "While computing function \"" + fnNode.toSourceString() + "\": " + e.getMessage(), e);
    }
  }

  private SoyValue visitIsSetFunction(FunctionNode node) {
    return BooleanData.forValue(env.hasVar(((VarRefNode) node.getChild(0)).getDefnDecl()));
  }

  private SoyValue visitCssFunction(FunctionNode node) {
    List<SoyValue> children = visitChildren(node);
    String selector = Iterables.getLast(children).stringValue();

    String renamedSelector = cssRenamingMap.get(selector);
    if (renamedSelector == null) {
      renamedSelector = selector;
    }

    if (node.numChildren() == 1) {
      return StringData.forValue(renamedSelector);
    } else {
      String fullSelector = children.get(0).stringValue() + "-" + renamedSelector;
      return StringData.forValue(fullSelector);
    }
  }

  private SoyValue visitXidFunction(FunctionNode node) {
    String xid = visit(node.getChild(0)).stringValue();
    String renamed = xidRenamingMap.get(xid);
    return (renamed != null) ? StringData.forValue(renamed) : StringData.forValue(xid + "_");
  }

  private SoyValue visitSoyServerKeyFunction(FunctionNode node) {
    SoyValue value = visit(node.getChild(0));
    // map tofu null to soysauce null since that is what this function expects.
    return StringData.forValue(
        soyServerKey(value instanceof NullData || value instanceof UndefinedData ? null : value));
  }

  private SoyValue visitIsPrimaryMsgInUseFunction(FunctionNode node) {
    if (msgBundle == null) {
      return BooleanData.TRUE;
    }
    // if the primary message id is available or the fallback message is not available, then we
    // are using the primary message.
    long primaryMsgId = ((IntegerNode) node.getChild(1)).getValue();
    if (!msgBundle.getMsgParts(primaryMsgId).isEmpty()) {
      return BooleanData.TRUE;
    }
    long fallbackMsgId = ((IntegerNode) node.getChild(2)).getValue();
    return BooleanData.forValue(msgBundle.getMsgParts(fallbackMsgId).isEmpty());
  }

  private SoyValue visitToFloatFunction(FunctionNode node) {
    IntegerData v = (IntegerData) visit(node.getChild(0));
    return FloatData.forValue((double) v.longValue());
  }

  @Override
  protected SoyValue visitTemplateLiteralNode(TemplateLiteralNode node) {
    return TemplateValue.create(node.getResolvedName());
  }

  // -----------------------------------------------------------------------------------------------
  // Private helpers.

  /**
   * Private helper to convert a boolean result.
   *
   * @param b The boolean to convert.
   */
  private SoyValue convertResult(boolean b) {
    return BooleanData.forValue(b);
  }

  /**
   * Private helper to convert an integer result.
   *
   * @param i The integer to convert.
   */
  private SoyValue convertResult(long i) {
    return IntegerData.forValue(i);
  }

  /**
   * Private helper to convert a float result.
   *
   * @param f The float to convert.
   */
  private SoyValue convertResult(double f) {
    return FloatData.forValue(f);
  }

  /**
   * Private helper to convert a string result.
   *
   * @param s The string to convert.
   */
  private SoyValue convertResult(String s) {
    return StringData.forValue(s);
  }
}
