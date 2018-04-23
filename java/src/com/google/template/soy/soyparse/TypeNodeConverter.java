/*
 * Copyright 2018 Google Inc.
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

package com.google.template.soy.soyparse;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.ErrorReporter.Checkpoint;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.error.SoyErrorKind.StyleAllowance;
import com.google.template.soy.error.SoyErrors;
import com.google.template.soy.types.ErrorType;
import com.google.template.soy.types.MapType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyTypeRegistry;
import com.google.template.soy.types.UnknownType;
import com.google.template.soy.types.ast.GenericTypeNode;
import com.google.template.soy.types.ast.NamedTypeNode;
import com.google.template.soy.types.ast.RecordTypeNode;
import com.google.template.soy.types.ast.TypeNode;
import com.google.template.soy.types.ast.TypeNodeVisitor;
import com.google.template.soy.types.ast.UnionTypeNode;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/** Resolves {@link TypeNode}s into {@link SoyType}s. */
final class TypeNodeConverter {

  private static final SoyErrorKind UNKNOWN_TYPE =
      SoyErrorKind.of("Unknown type ''{0}''.{1}", StyleAllowance.NO_PUNCTUATION);

  private static final SoyErrorKind DUPLICATE_RECORD_FIELD =
      SoyErrorKind.of("Duplicate field ''{0}'' in record declaration.");

  private static final SoyErrorKind UNEXPECTED_TYPE_PARAM =
      SoyErrorKind.of(
          "Unexpected type parameter: ''{0}'' only has {1}", StyleAllowance.NO_PUNCTUATION);

  private static final SoyErrorKind EXPECTED_TYPE_PARAM =
      SoyErrorKind.of("Expected a type parameter: ''{0}'' has {1}", StyleAllowance.NO_PUNCTUATION);

  private static final SoyErrorKind NOT_A_GENERIC_TYPE =
      SoyErrorKind.of("''{0}'' is not a generic type, expected ''list'' or ''map''.");

  private static final SoyErrorKind MISSING_GENERIC_TYPE_PARAMETERS =
      SoyErrorKind.of("''{0}'' is a generic type, expected {1}.");

  // LINT.IfChange
  private static final SoyErrorKind BAD_MAP_KEY_TYPE =
      SoyErrorKind.of(
          "''{0}'' is not allowed as a map key type. Allowed map key types: "
              + "bool, int, float, number, string, proto enum.");

  private static final ImmutableMap<String, GenericTypeInfo> GENERIC_TYPES =
      ImmutableMap.of(
          "list",
          new GenericTypeInfo(1) {
            @Override
            SoyType create(List<SoyType> types, SoyTypeRegistry registry) {
              return registry.getOrCreateListType(types.get(0));
            }
          },
          "legacy_object_map",
          new GenericTypeInfo(2) {
            @Override
            SoyType create(List<SoyType> types, SoyTypeRegistry registry) {
              return registry.getOrCreateLegacyObjectMapType(types.get(0), types.get(1));
            }
          },
          "map",
          new GenericTypeInfo(2) {
            @Override
            SoyType create(List<SoyType> types, SoyTypeRegistry registry) {
              return registry.getOrCreateMapType(types.get(0), types.get(1));
            }

            @Override
            void checkPermissibleGenericTypes(
                List<SoyType> types, List<TypeNode> typeNodes, ErrorReporter errorReporter) {
              SoyType keyType = types.get(0);
              if (!MapType.isAllowedKeyType(keyType)) {
                errorReporter.report(typeNodes.get(0).sourceLocation(), BAD_MAP_KEY_TYPE, keyType);
              }
            }
          });

  /** Simple representation of a generic type specification. */
  private abstract static class GenericTypeInfo {
    final int numParams;

    GenericTypeInfo(int numParams) {
      this.numParams = numParams;
    }

    final String formatNumTypeParams() {
      return numParams + " type parameter" + (numParams > 1 ? "s" : "");
    }

    /**
     * Creates the given type. There are guaranteed to be exactly {@link #numParams} in the list.
     */
    abstract SoyType create(List<SoyType> types, SoyTypeRegistry registry);

    /**
     * Subclasses can override to implement custom restrictions on their generic type parameters.
     *
     * @param types The generic types.
     * @param typeNodes TypeNodes corresponding to each of the generic types (for reporting source
     *     locations in error messages)
     * @param errorReporter For reporting an error condition.
     */
    void checkPermissibleGenericTypes(
        List<SoyType> types, List<TypeNode> typeNodes, ErrorReporter errorReporter) {}
  }

  private final ErrorReporter errorReporter;
  private final SoyTypeRegistry typeRegistry;

  TypeNodeConverter(ErrorReporter errorReporter, SoyTypeRegistry typeRegistry) {
    this.errorReporter = errorReporter;
    this.typeRegistry = typeRegistry;
  }

  /**
   * Converts a TypeNode into a SoyType.
   *
   * <p>If any errors are encountered they are reported to the error reporter.
   */
  SoyType getOrCreateType(@Nullable TypeNode node) {
    if (node == null) {
      return UnknownType.getInstance();
    }
    return node.accept(new ConverterVisitor());
  }

  private final class ConverterVisitor
      implements TypeNodeVisitor<SoyType>, Function<TypeNode, SoyType> {
    @Override
    public SoyType visit(NamedTypeNode node) {
      String name = node.name();
      SoyType type = typeRegistry.getType(name);
      if (type == null) {
        GenericTypeInfo genericType = GENERIC_TYPES.get(name);
        if (genericType != null) {
          errorReporter.report(
              node.sourceLocation(),
              MISSING_GENERIC_TYPE_PARAMETERS,
              name,
              genericType.formatNumTypeParams());
        } else {
          errorReporter.report(
              node.sourceLocation(),
              UNKNOWN_TYPE,
              name,
              SoyErrors.getDidYouMeanMessage(typeRegistry.getAllSortedTypeNames(), name));
        }
        type = ErrorType.getInstance();
      }
      return type;
    }

    @Override
    public SoyType visit(GenericTypeNode node) {
      ImmutableList<TypeNode> args = node.arguments();
      String name = node.name();
      GenericTypeInfo genericType = GENERIC_TYPES.get(name);
      if (genericType == null) {
        errorReporter.report(node.sourceLocation(), NOT_A_GENERIC_TYPE, name);
        return ErrorType.getInstance();
      }
      if (args.size() < genericType.numParams) {
        errorReporter.report(
            // blame the '>'
            node.sourceLocation().getEndLocation(),
            EXPECTED_TYPE_PARAM,
            name,
            genericType.formatNumTypeParams());
        return ErrorType.getInstance();
      } else if (args.size() > genericType.numParams) {
        errorReporter.report(
            // blame the first unexpected argument
            args.get(genericType.numParams).sourceLocation(),
            UNEXPECTED_TYPE_PARAM,
            name,
            genericType.formatNumTypeParams());
        return ErrorType.getInstance();
      }

      List<SoyType> genericTypes = Lists.transform(args, this);
      Checkpoint checkpoint = errorReporter.checkpoint();
      genericType.checkPermissibleGenericTypes(genericTypes, args, errorReporter);
      return errorReporter.errorsSince(checkpoint)
          ? ErrorType.getInstance()
          : genericType.create(genericTypes, typeRegistry);
    }

    @Override
    public SoyType visit(UnionTypeNode node) {
      return typeRegistry.getOrCreateUnionType(Collections2.transform(node.candidates(), this));
    }

    @Override
    public SoyType visit(RecordTypeNode node) {
      Map<String, SoyType> map = Maps.newLinkedHashMap();
      for (RecordTypeNode.Property property : node.properties()) {
        SoyType oldType = map.put(property.name(), property.type().accept(this));
        if (oldType != null) {
          errorReporter.report(property.nameLocation(), DUPLICATE_RECORD_FIELD, property.name());
          // restore old mapping and keep going
          map.put(property.name(), oldType);
        }
      }
      return typeRegistry.getOrCreateRecordType(map);
    }

    @Override
    public SoyType apply(TypeNode node) {
      return node.accept(this);
    }
  }
}
