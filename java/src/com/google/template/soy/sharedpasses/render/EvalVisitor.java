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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.template.soy.shared.internal.SharedRuntime.dividedBy;
import static com.google.template.soy.shared.internal.SharedRuntime.equal;
import static com.google.template.soy.shared.internal.SharedRuntime.lessThan;
import static com.google.template.soy.shared.internal.SharedRuntime.lessThanOrEqual;
import static com.google.template.soy.shared.internal.SharedRuntime.minus;
import static com.google.template.soy.shared.internal.SharedRuntime.negative;
import static com.google.template.soy.shared.internal.SharedRuntime.plus;
import static com.google.template.soy.shared.internal.SharedRuntime.times;

import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.errorprone.annotations.ForOverride;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.data.LoggingAdvisingAppendable;
import com.google.template.soy.data.SoyAbstractValue;
import com.google.template.soy.data.SoyDataException;
import com.google.template.soy.data.SoyLegacyObjectMap;
import com.google.template.soy.data.SoyMap;
import com.google.template.soy.data.SoyProtoValue;
import com.google.template.soy.data.SoyRecord;
import com.google.template.soy.data.SoyValue;
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
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.FieldAccessNode;
import com.google.template.soy.exprtree.FloatNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.GlobalNode;
import com.google.template.soy.exprtree.IntegerNode;
import com.google.template.soy.exprtree.ItemAccessNode;
import com.google.template.soy.exprtree.ListLiteralNode;
import com.google.template.soy.exprtree.MapLiteralNode;
import com.google.template.soy.exprtree.NullNode;
import com.google.template.soy.exprtree.OperatorNodes.AndOpNode;
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
import com.google.template.soy.exprtree.OperatorNodes.TimesOpNode;
import com.google.template.soy.exprtree.ProtoInitNode;
import com.google.template.soy.exprtree.RecordLiteralNode;
import com.google.template.soy.exprtree.StringNode;
import com.google.template.soy.exprtree.VarDefn;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.exprtree.VeLiteralNode;
import com.google.template.soy.logging.LoggingFunction;
import com.google.template.soy.msgs.SoyMsgBundle;
import com.google.template.soy.plugin.java.restricted.JavaValueFactory;
import com.google.template.soy.plugin.java.restricted.SoyJavaSourceFunction;
import com.google.template.soy.shared.SoyCssRenamingMap;
import com.google.template.soy.shared.SoyIdRenamingMap;
import com.google.template.soy.shared.internal.BuiltinFunction;
import com.google.template.soy.shared.restricted.SoyJavaFunction;
import com.google.template.soy.soytree.defn.LoopVar;
import com.google.template.soy.soytree.defn.TemplateStateVar;
import com.google.template.soy.types.MapType;
import com.google.template.soy.types.SoyProtoType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyType.Kind;
import com.google.template.soy.types.SoyTypes;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Visitor for evaluating the expression rooted at a given ExprNode.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * <p>{@link #exec} may be called on any expression. The result of evaluating the expression (in the
 * context of the {@code data} and {@code env} passed into the constructor) is returned as a {@code
 * SoyValue} object.
 *
 */
public class EvalVisitor extends AbstractReturningExprNodeVisitor<SoyValue> {

  /** Interface for a factory that creates an EvalVisitor. */
  public interface EvalVisitorFactory {

    /**
     * Creates an EvalVisitor.
     *
     * @param env The current environment.
     * @param ijData The current injected data.
     * @param cssRenamingMap The CSS renaming map, or null if not applicable.
     * @param xidRenamingMap The XID renaming map, or null if not applicable.
     * @param pluginInstances The instances used for evaluating functions that call instance
     *     methods.
     * @return The newly created EvalVisitor instance.
     */
    EvalVisitor create(
        Environment env,
        @Nullable SoyRecord ijData,
        @Nullable SoyCssRenamingMap cssRenamingMap,
        @Nullable SoyIdRenamingMap xidRenamingMap,
        @Nullable SoyMsgBundle msgBundle,
        boolean debugSoyTemplateInfo,
        ImmutableMap<String, Supplier<Object>> pluginInstances);
  }

  /** The current environment. */
  private final Environment env;

  /** The current injected data. */
  @Nullable private final SoyRecord ijData;

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
  private final ImmutableMap<String, Supplier<Object>> pluginInstances;

  /**
   * @param ijData The current injected data.
   * @param env The current environment.
   * @param pluginInstances The instances used for evaluating functions that call instance methods.
   */
  protected EvalVisitor(
      Environment env,
      @Nullable SoyRecord ijData,
      @Nullable SoyCssRenamingMap cssRenamingMap,
      @Nullable SoyIdRenamingMap xidRenamingMap,
      @Nullable SoyMsgBundle msgBundle,
      boolean debugSoyTemplateInfo,
      ImmutableMap<String, Supplier<Object>> pluginInstances) {
    this.env = checkNotNull(env);
    this.ijData = ijData;
    this.msgBundle = msgBundle;
    this.cssRenamingMap = (cssRenamingMap == null) ? SoyCssRenamingMap.EMPTY : cssRenamingMap;
    this.xidRenamingMap = (xidRenamingMap == null) ? SoyCssRenamingMap.EMPTY : xidRenamingMap;
    this.debugSoyTemplateInfo = debugSoyTemplateInfo;
    this.context = new TofuPluginContext(msgBundle);
    this.pluginInstances = checkNotNull(pluginInstances);
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
  protected SoyValue visitGlobalNode(GlobalNode node) {
    return visit(node.getValue());
  }

  // -----------------------------------------------------------------------------------------------
  // Implementations for collections.

  @Override
  protected SoyValue visitListLiteralNode(ListLiteralNode node) {
    List<SoyValue> values = this.visitChildren(node);
    return ListImpl.forProviderList(values);
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

  // -----------------------------------------------------------------------------------------------
  // Implementations for data references.

  @Override
  protected SoyValue visitVarRefNode(VarRefNode node) {
    return visitNullSafeNode(node);
  }

  @Override
  protected SoyValue visitDataAccessNode(DataAccessNode node) {
    return visitNullSafeNode(node);
  }

  /**
   * Helper function which ensures that {@link NullSafetySentinel} instances don't escape from this
   * visitor.
   *
   * @param node The node to evaluate.
   * @return The result of evaluating the node.
   */
  private SoyValue visitNullSafeNode(ExprNode node) {
    SoyValue value = visitNullSafeNodeRecurse(node);
    // Transform null sentinel into a normal null value.
    if (value == NullSafetySentinel.INSTANCE) {
      return NullData.INSTANCE;
    }
    return value;
  }

  /**
   * Helper function which recursively evaluates data references. This bypasses the normal visitor
   * mechanism as follows: As soon as the EvalVisitor sees a node which is a data reference, it
   * calls this function which evaluates that data reference and any descendant data references,
   * returning either the result of the evaluation, or a special sentinel value which indicates that
   * a null-safety check failed. Internally this sentinel value is used to short-circuit evaluations
   * that would otherwise fail because of the null value.
   *
   * <p>If any descendant node is not a data reference, then this uses the normal visitor mechanism
   * to evaluate that node.
   *
   * <p>The reason for bypassing the normal visitor mechanism is that we want to detect the
   * transition between data-reference nodes and non-data-reference nodes. So for example, if a
   * FieldAccessNode has a parent node which is a data reference, we want to propagate the sentinel
   * value upward, whereas if the parent is not a data reference, then we want to convert the
   * sentinel value into a regular null value.
   *
   * @param node The node to evaluate.
   * @return The result of evaluating the node.
   */
  private SoyValue visitNullSafeNodeRecurse(ExprNode node) {
    switch (node.getKind()) {
      case VAR_REF_NODE:
        return visitNullSafeVarRefNode((VarRefNode) node);

      case FIELD_ACCESS_NODE:
        return visitNullSafeFieldAccessNode((FieldAccessNode) node);

      case ITEM_ACCESS_NODE:
        return visitNullSafeItemAccessNode((ItemAccessNode) node);

      default:
        return visit(node);
    }
  }

  private SoyValue visitNullSafeVarRefNode(VarRefNode varRef) {
    SoyValue result = null;
    if (varRef.isDollarSignIjParameter()) {
      // TODO(lukes): it would be nice to move this logic into Environment or even eliminate the
      // ijData == null case.  It seems like this case is mostly for prerendering, though im not
      // sure.
      if (ijData != null) {
        result = ijData.getField(varRef.getName());
      } else {
        throw RenderException.create(
            "Injected data not provided, yet referenced (" + varRef.toSourceString() + ").");
      }
    } else if (varRef.getDefnDecl().kind() == VarDefn.Kind.STATE) {
      TemplateStateVar state = (TemplateStateVar) varRef.getDefnDecl();
      return visit(state.defaultValue());
    } else {
      return env.getVar(varRef.getDefnDecl());
    }

    return (result != null) ? result : UndefinedData.INSTANCE;
  }

  private SoyValue visitNullSafeFieldAccessNode(FieldAccessNode fieldAccess) {
    SoyValue base = visitNullSafeNodeRecurse(fieldAccess.getBaseExprChild());

    // attempting field access on non-SoyRecord
    if (!(base instanceof SoyRecord) && !(base instanceof SoyProtoValue)) {
      if (base == NullSafetySentinel.INSTANCE) {
        // Bail out if base expression failed a null-safety check.
        return NullSafetySentinel.INSTANCE;
      }

      if (fieldAccess.isNullSafe()) {
        if (isNullOrUndefinedBase(base)) {
          // Return the sentinel value that indicates that a null-safety check failed.
          return NullSafetySentinel.INSTANCE;
        } else {
          throw RenderException.create(
              String.format(
                  "While evaluating \"%s\", encountered non-record just before accessing \"%s\".",
                  fieldAccess.toSourceString(), fieldAccess.getSourceStringSuffix()));
        }
      }

      // This behavior is not ideal, but needed for compatibility with existing code.
      // TODO: If feasible, find and fix existing instances, then throw RenderException here.
      return UndefinedData.INSTANCE;
    }

    // If the static type is a proto, access it using proto semantics
    // the base type is possibly nullable, so remove null before testing for being a proto
    if (SoyTypes.tryRemoveNull(fieldAccess.getBaseExprChild().getType()).getKind() == Kind.PROTO) {
      return ((SoyProtoValue) base).getProtoField(fieldAccess.getFieldName());
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

    return (value != null) ? value : UndefinedData.INSTANCE;
  }

  private SoyValue visitNullSafeItemAccessNode(ItemAccessNode itemAccess) {
    SoyValue base = visitNullSafeNodeRecurse(itemAccess.getBaseExprChild());

    // attempting item access on non-SoyMap
    if (!(base instanceof SoyLegacyObjectMap || base instanceof SoyMap)) {
      if (base == NullSafetySentinel.INSTANCE) {
        // Bail out if base expression failed a null-safety check.
        return NullSafetySentinel.INSTANCE;
      }

      if (itemAccess.isNullSafe()) {
        if (isNullOrUndefinedBase(base)) {
          // Return the sentinel value that indicates that a null-safety check failed.
          return NullSafetySentinel.INSTANCE;
        } else {
          throw RenderException.create(
              String.format(
                  "While evaluating \"%s\", encountered non-map/list just before accessing \"%s\".",
                  itemAccess.toSourceString(), itemAccess.getSourceStringSuffix()));
        }
      }

      // This behavior is not ideal, but needed for compatibility with existing code.
      // TODO: If feasible, find and fix existing instances, then throw RenderException here.
      return UndefinedData.INSTANCE;
    }

    // base is a valid SoyMap or SoyLegacyObjectMap: get value
    maybeMarkBadProtoAccess(itemAccess, base);
    SoyValue key = visit(itemAccess.getKeyExprChild());

    SoyType baseType = SoyTypes.tryRemoveNull(itemAccess.getBaseExprChild().getType());

    // We need to know whether to invoke the SoyMap or SoyLegacyObjectMap method.
    // An instanceof check on the runtime value of base is insufficient, since
    // DictImpl implements both interfaces. Instead, look at the declared type of the base
    // expression.
    boolean shouldUseNewMap = MapType.ANY_MAP.isAssignableFrom(baseType);
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
    } else if (shouldUseNewMap) {
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

  // Returns true if the base SoyValue of a data access chain is null or undefined.
  private static boolean isNullOrUndefinedBase(SoyValue base) {
    return base == null
        || base instanceof NullData
        || base instanceof UndefinedData
        || base == NullSafetySentinel.INSTANCE;
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
    return convertResult(operand0.longValue() % operand1.longValue());
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
    // identical to the implementation of isNonnull
    if (operand0 instanceof NullData || operand0 instanceof UndefinedData) {
      return visit(node.getChild(1));
    }
    return operand0;
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
        case IS_FIRST:
          return visitIsFirstFunction(node);
        case IS_LAST:
          return visitIsLastFunction(node);
        case INDEX:
          return visitIndexFunction(node);
        case CHECK_NOT_NULL:
          return visitCheckNotNullFunction(node.getChild(0));
        case CSS:
          return visitCssFunction(node);
        case XID:
          return visitXidFunction(node);
        case IS_PRIMARY_MSG_IN_USE:
          return visitIsPrimaryMsgInUseFunction(node);
        case V1_EXPRESSION:
          throw new UnsupportedOperationException(
              "the v1Expression function can't be used in templates compiled to Java");
        case TO_FLOAT:
          return visitToFloatFunction(node);
        case DEBUG_SOY_TEMPLATE_INFO:
          return BooleanData.forValue(debugSoyTemplateInfo);
        case VE_DATA:
          return NullData.INSTANCE;
        case MSG_WITH_ID:
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
      return computeFunctionHelper(fn, args, node);
    } else if (soyFunction instanceof LoggingFunction) {
      return StringData.forValue(((LoggingFunction) soyFunction).getPlaceholder());
    } else {
      throw RenderException.create(
          "Failed to find Soy function with name '"
              + node.getFunctionName()
              + "'"
              + " (function call \""
              + node.toSourceString()
              + "\").");
    }
  }

  @Override
  protected SoyValue visitProtoInitNode(ProtoInitNode node) {
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

  private SoyValue visitCheckNotNullFunction(ExprNode child) {
    SoyValue childValue = visit(child);
    if (childValue instanceof NullData || childValue instanceof UndefinedData) {
      throw new SoyDataException(child.toSourceString() + " is null");
    }
    return childValue;
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
   * @param fn The function object.
   * @param args The arguments to the function.
   * @param fnNode The function node. Only used for error reporting.
   * @return The result of the function called on the given arguments.
   */
  @ForOverride
  protected SoyValue computeFunctionHelper(
      SoyJavaSourceFunction fn, List<SoyValue> args, final FunctionNode fnNode) {
    try {
      return new TofuValueFactory(fnNode, pluginInstances).computeForJava(fn, args, context);
    } catch (Exception e) {
      throw RenderException.create(
          "While computing function \"" + fnNode.toSourceString() + "\": " + e.getMessage(), e);
    }
  }

  private SoyValue visitIsFirstFunction(FunctionNode node) {

    int localVarIndex;
    try {
      VarRefNode dataRef = (VarRefNode) node.getChild(0);
      localVarIndex = env.getIndex((LoopVar) dataRef.getDefnDecl());
    } catch (Exception e) {
      throw RenderException.create(
          "Failed to evaluate function call " + node.toSourceString() + ".", e);
    }
    return convertResult(localVarIndex == 0);
  }

  private SoyValue visitIsLastFunction(FunctionNode node) {

    boolean isLast;
    try {
      VarRefNode dataRef = (VarRefNode) node.getChild(0);
      isLast = env.isLast((LoopVar) dataRef.getDefnDecl());
    } catch (Exception e) {
      throw RenderException.create(
          "Failed to evaluate function call " + node.toSourceString() + ".", e);
    }
    return convertResult(isLast);
  }

  private SoyValue visitIndexFunction(FunctionNode node) {

    int localVarIndex;
    try {
      VarRefNode dataRef = (VarRefNode) node.getChild(0);
      localVarIndex = env.getIndex((LoopVar) dataRef.getDefnDecl());
    } catch (Exception e) {
      throw RenderException.create(
          "Failed to evaluate function call " + node.toSourceString() + ".", e);
    }
    return convertResult(localVarIndex);
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
  protected SoyValue visitVeLiteralNode(VeLiteralNode node) {
    return NullData.INSTANCE;
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

  /**
   * Class that represents a sentinel value indicating that a null-safety check failed. This value
   * should never "leak" outside this class, in other words, no code outside of this class should
   * ever see a value of this type.
   */
  private static final class NullSafetySentinel extends SoyAbstractValue {

    /** Static singleton instance of SafeNullData. */
    public static final NullSafetySentinel INSTANCE = new NullSafetySentinel();

    private NullSafetySentinel() {}

    @Override
    public boolean equals(Object other) {
      return other == this;
    }

    @Override
    public int hashCode() {
      return System.identityHashCode(this);
    }

    @Override
    public boolean coerceToBoolean() {
      return false;
    }

    @Override
    public String coerceToString() {
      return "null";
    }

    @Override
    public void render(LoggingAdvisingAppendable appendable) throws IOException {
      appendable.append(coerceToString());
    }
  }
}
