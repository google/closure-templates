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

package com.google.template.soy.jssrc.internal;

import com.google.common.base.CharMatcher;
import com.google.common.base.Optional;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.collect.Table;
import com.google.template.soy.exprtree.AbstractExprNodeVisitor;
import com.google.template.soy.exprtree.DataAccessNode;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprNode.ParentExprNode;
import com.google.template.soy.exprtree.FieldAccessNode;
import com.google.template.soy.exprtree.ItemAccessNode;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.aggregate.UnionType;

import java.util.Map;

/**
 * Collects info which is later used to generate {@link HelperFunctions helper functions}.
 *
 * <p>Helper functions are critical to keeping generated code small while interfacing with a variety
 * of representations of structured data.
 *
 * <p>This class builds tables for various kinds of helper functions.
 *
 * <p>For {@link HelperFunctions.FieldAccessorHelperInfo field accessors} we build a table relating
 * <table>
 *   <tr>
 *     <th>Container Type</th>
 *     <th>Field Name</th>
 *     <th>{@link HelperFunctions.FieldAccessorHelperInfo}</th>
 *   <tr><td colspan=3>&hellip;</tr>
 * </table>
 *
 * <p>For {@link HelperFunctions.ValueConverterHelperInfo value converters} we build a table relating
 * <table>
 *   <tr>
 *     <th>Field Type</th>
 *     <th>{@link HelperFunctions.ValueConverterHelperInfo}</th>
 *   </tr>
 * </table>
 *
 * <h3>Soy Types on Field Accesses</h3>
 *
 * <p>With some <a href="#unsoundness">optimistic assumptions</a>, Soy's type system lets us
 * determine which strategies to employ to read fields on protocol buffers, maps, arrays,
 * etc.
 *
 * <p>This class collects information which the {@link TranslateToJsExprVisitor code generator}
 * uses to build helper functions which read fields and which don't rely on any relationships
 * between the text of a property or method name and a field name, so will retain their
 * semantics after compilation.
 *
 * <h3 id="unsoundness">CAVEAT: Unsound Type Assumptions</h3>
 *
 * <p>To keep code small, this class makes unsound assumptions when picking strategies to read field.
 * Specifically we assume that for any field or variable reference, we will be able to enumerate the
 * nominal types to which to which it might belong.
 *
 * <p>Soy’s type system has {@code any} and {@code unknown} types.  We do not treat these as the
 * union of all possible nominal types as a sound analysis would require.
 */
final class CollectTypeHelpersExprVisitor extends AbstractExprNodeVisitor<Void> {

  /**
   * The closure namespace which is typically derived from the first template's Soy namespace
   * and which is used to generate closure paths for generated helper functions.
   */
  private final String closureNamespace;

  private final
  Table<SoyType, String, HelperFunctions.FieldAccessorHelperInfo> fieldAccessorHelperInfo =
      HashBasedTable.create();

  private final
  Map<SoyType, HelperFunctions.ValueConverterHelperInfo> valueConverterHelperInfo =
      Maps.newLinkedHashMap();


  /**
   * @param closureNamespace namespace used to generate paths for generated helper functions.
   *     This is typically derived from the first template's Soy namespace.
   */
  CollectTypeHelpersExprVisitor(String closureNamespace) {
    this.closureNamespace = closureNamespace;
  }


  /**
   * The info for the converter for the given type or absent if no conversion is necessary.
   */
  public Optional<HelperFunctions.ValueConverterHelperInfo> converterFor(SoyType valueType) {
    // Since updateTables... is idempotent we just rerun it to boil it down to a key into our table.
    Optional<SoyType> keyTypeOpt = updateTablesBasedOnValueType(valueType, false);
    if (keyTypeOpt.isPresent()) {
      return Optional.of(valueConverterHelperInfo.get(keyTypeOpt.get()));
    } else {
      return Optional.absent();
    }
  }


  /**
   * The info for the field accessor for the given type or absent if the
   * {@link HelperFunctions#defaultStrategyForField} suffices.
   */
  public Optional<HelperFunctions.FieldAccessorHelperInfo> fieldAccessorFor(
      SoyType containerType, String fieldName) {
    Optional<SoyType> keyTypeOpt = updateTablesBasedOnField(containerType, fieldName, false);
    if (keyTypeOpt.isPresent()) {
      return Optional.of(fieldAccessorHelperInfo.get(keyTypeOpt.get(), fieldName));
    } else {
      return Optional.absent();
    }
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for data references.

  @Override protected void visitVarRefNode(VarRefNode node) {
    updateTablesBasedOnValueType(node.getType(), node.isNullSafeInjected());
    // TODO: If we have a type for $data or $opt_ijData, examine it here.
  }

  @Override protected void visitDataAccessNode(DataAccessNode node) {
    throw new AssertionError();  // Should have been handled in a child-specific variant.
  }

  @Override protected void visitFieldAccessNode(FieldAccessNode node) {
    SoyType containerType = node.getBaseExprChild().getType();
    String fieldName = node.getFieldName();
    boolean isNullSafe = node.isNullSafe();

    updateTablesBasedOnField(containerType, fieldName, isNullSafe);
    updateTablesBasedOnValueType(node.getType(), isNullSafe);
  }

  @Override protected void visitItemAccessNode(ItemAccessNode node) {
    updateTablesBasedOnValueType(node.getType(), node.isNullSafe());
  }

  // -----------------------------------------------------------------------------------------------
  // Fallback implementation.

  @Override protected void visitExprNode(ExprNode node) {
    if (node instanceof ParentExprNode) {
      visitChildren((ParentExprNode) node);
    }
  }


  // -----------------------------------------------------------------------------------------------
  // Internal machinery for maintaining function helper tables.

  private abstract class HelperTableMaintainer<HelperInfoT extends HelperFunctions.BaseHelperInfo> {
    /**
     * Examines the type and determines whether it or any variants (sub-types within a
     * union) need to appear in the type table.
     *
     * <p>Adds entries to the helper function tables as needed.
     *
     * @return valueType a minimal sub-type of type that includes all concrete types that
     *   need to appear in the table, or absent if there is no such type.
     */
    Optional<SoyType> updateTable(SoyType type, boolean appearsInUnion) {
      SoyType minimalType;
      if (type instanceof UnionType) {
        UnionType unionType = (UnionType) type;
        // For unions, we collect the member types that convert, so that we can construct a
        // minimal union consisting of types we need to try to convert before falling back
        // to the identity conversion.
        ImmutableList.Builder<SoyType> helperMemberTypes = ImmutableList.builder();
        for (SoyType memberType : unionType.getMembers()) {
          Optional<SoyType> helperMemberTypeOpt = updateTable(memberType, true);
          if (helperMemberTypeOpt.isPresent()) {
            helperMemberTypes.add(helperMemberTypeOpt.get());
          }
        }
        // UnionType.of de-dupes, flattens, and sorts for us.
        minimalType = UnionType.of(helperMemberTypes.build());
        if (minimalType instanceof UnionType
            && ((UnionType) minimalType).getMembers().isEmpty()) {
          return Optional.absent();
        }
      } else {
        if (shouldAppearInTable(type)) {
          return Optional.absent();
        }
        minimalType = type;
      }

      // Make sure there's an entry for minimalType.
      HelperInfoT info;
      Optional<HelperInfoT> infoOpt = lookupHelperInfo(minimalType);
      if (infoOpt.isPresent()) {
        info = infoOpt.get();
      } else {
        String closurePath;
        Optional<String> closurePathOpt = externalClosurePathFor(minimalType);
        HelperFunctions.CodeLocation loc;
        if (closurePathOpt.isPresent()) {
          closurePath = closurePathOpt.get();
          loc = HelperFunctions.CodeLocation.EXTERNAL;
        } else {
          closurePath = allocateHelperName(helperNamePrefix(), minimalType);
          loc = HelperFunctions.CodeLocation.GENERATED;
        }
        info = storeHelperInfo(closurePath, loc, minimalType);
      }
      // Record the fact that it appears in a union.
      if (appearsInUnion) {
        info.appearsInUnion = true;
      }

      return Optional.of(minimalType);
    }

    /**
     * True if type is a concrete type that needs an entry in the table.
     */
    abstract boolean shouldAppearInTable(SoyType type);

    /**
     * An existing row in the table or absent if none exists.
     */
    abstract Optional<HelperInfoT> lookupHelperInfo(SoyType minimalType);

    /**
     * The closure path of an externally-defined library function for
     * the given minimal Soy type or absent if none exists.
     */
    abstract Optional<String> externalClosurePathFor(SoyType minimalType);

    /** Allocates a name for a generated helper function. */
    abstract String helperNamePrefix();

    /** Adds an entry to the helper table. */
    abstract HelperInfoT storeHelperInfo(
        String closurePath, HelperFunctions.CodeLocation loc, SoyType minimalType);
  }


  /**
   * Examines the value type and determines whether it or any variants (sub-types within a
   * union) need conversion at render-time.
   * <p>
   * Adds entries to the helper function tables as needed.
   *
   * @return valueType a minimal sub-type of valueType that includes all concrete types that
   *   need conversion, or absent if no conversion needs to be done.
   */
  private Optional<SoyType> updateTablesBasedOnValueType(
      SoyType valueType, boolean appearsInUnion) {
    return new HelperTableMaintainer<HelperFunctions.ValueConverterHelperInfo>() {
      @Override boolean shouldAppearInTable(SoyType type) {
        return HelperFunctions.converterForType(type).isPresent();
      }

      @Override
      Optional<HelperFunctions.ValueConverterHelperInfo> lookupHelperInfo(SoyType minimalType) {
        return Optional.fromNullable(valueConverterHelperInfo.get(minimalType));
      }

      @Override Optional<String> externalClosurePathFor(SoyType minimalType) {
        return HelperFunctions.converterForType(minimalType);
      }

      @Override String helperNamePrefix() {
        return "ConvertValue";
      }

      @Override HelperFunctions.ValueConverterHelperInfo storeHelperInfo(
        String closurePath, HelperFunctions.CodeLocation loc, SoyType minimalType) {
        HelperFunctions.ValueConverterHelperInfo info =
            new HelperFunctions.ValueConverterHelperInfo(closurePath, loc, minimalType);
        valueConverterHelperInfo.put(minimalType, info);
        return info;
      }

    }.updateTable(valueType, appearsInUnion);
  }


  /**
   * Examines the containerType in the context of a known field and determines if helpers
   * are needed to read the field.
   * <p>
   * Adds entries to the helper function tables as needed.
   *
   * @return the minimal sub-type of containerType for which the lookup strategy
   *   {@code container[fieldName]} does not work.
   */
  private Optional<SoyType> updateTablesBasedOnField(
      SoyType containerType, final String fieldName, boolean appearsInUnion) {
    return new HelperTableMaintainer<HelperFunctions.FieldAccessorHelperInfo>() {
      @Override boolean shouldAppearInTable(SoyType type) {
        HelperFunctions.FieldAccessStrategy strategy =
            HelperFunctions.strategyForFieldLookup(type, fieldName);
        HelperFunctions.FieldAccessStrategy defaultStrategy =
            HelperFunctions.defaultStrategyForField(fieldName);
        return !strategy.equals(defaultStrategy);
      }

      @Override
      Optional<HelperFunctions.FieldAccessorHelperInfo> lookupHelperInfo(SoyType minimalType) {
        return Optional.fromNullable(fieldAccessorHelperInfo.get(minimalType, fieldName));
      }

      @Override Optional<String> externalClosurePathFor(SoyType minimalType) {
        HelperFunctions.FieldAccessStrategy strategy =
            HelperFunctions.strategyForFieldLookup(minimalType, fieldName);
        switch (strategy.op()) {
          case BRACKET: case DOT: case METHOD:
            return Optional.absent();
          case LIBRARY_FN:
            return Optional.of(strategy.fieldKey());
        }
        throw new AssertionError("unrecognized " + strategy.op());
      }

      @Override String helperNamePrefix() {
        return "Read_" + fieldName;
      }

      @Override HelperFunctions.FieldAccessorHelperInfo storeHelperInfo(
        String closurePath, HelperFunctions.CodeLocation loc, SoyType minimalType) {
        HelperFunctions.FieldAccessorHelperInfo info =
            new HelperFunctions.FieldAccessorHelperInfo(closurePath, loc, minimalType, fieldName);
        fieldAccessorHelperInfo.put(minimalType, fieldName, info);
        return info;
      }

    }.updateTable(containerType, appearsInUnion);
  }


  // -----------------------------------------------------------------------------------------------
  // Internal machinery for choosing names for helper functions that don't collide.

  /** Used to generate non-colliding helper function names. */
  private int nameCounter;

  private String allocateHelperName(String prefix, SoyType type) {
    if (nameCounter < 0) { throw new AssertionError("underflow"); }
    String suffix = "_" + nameCounter;
    ++nameCounter;
    return closureNamespace + "." + prefix + toJsIdentifierPart(type.toString()) + suffix;
  }


  /**
   * Returns a JS identifier part based on a string.
   * <p>
   * This is used to produce readable names based on union types.
   * For example to generate a value converter for {@code foo | bar}
   * we might produce {@code ConvertValue_foo_bar_12} where this
   * method produces the substring {@code foo_bar} and the suffix is a
   * counter that guarantees uniqueness.
   */
  private static String toJsIdentifierPart(String s) {
    StringBuilder sb = new StringBuilder(s);
    for (int i = 0, n = sb.length(); i < n; ++i) {
      // The set of Java letters and digits is a subset of the EcmaScript IdentifierPart
      // per https://es5.github.io/#x7.6
      if (!CharMatcher.JAVA_LETTER_OR_DIGIT.matches(sb.charAt(i))) {
        sb.setCharAt(i, '_');
      }
    }
    return sb.toString();
  }
}
