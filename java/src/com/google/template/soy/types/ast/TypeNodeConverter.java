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

package com.google.template.soy.types.ast;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.errorprone.annotations.DoNotCall;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.error.SoyErrorKind.StyleAllowance;
import com.google.template.soy.error.SoyErrors;
import com.google.template.soy.types.ErrorType;
import com.google.template.soy.types.RecordType;
import com.google.template.soy.types.SanitizedType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyTypeRegistry;
import java.util.List;
import java.util.Map;

/** Resolves {@link TypeNode}s into {@link SoyType}s. */
public final class TypeNodeConverter
    implements TypeNodeVisitor<SoyType>, Function<TypeNode, SoyType> {

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

  private static final SoyErrorKind SAFE_PROTO_TYPE =
      SoyErrorKind.of("Please use Soy''s native ''{0}'' type instead of the ''{1}'' type.");

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
          },
          "ve",
          new GenericTypeInfo(1) {
            @Override
            SoyType create(List<SoyType> types, SoyTypeRegistry registry) {
              return registry.getOrCreateVeType(types.get(0).toString());
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
  }

  private final ErrorReporter errorReporter;
  private final SoyTypeRegistry typeRegistry;

  public TypeNodeConverter(ErrorReporter errorReporter, SoyTypeRegistry typeRegistry) {
    this.errorReporter = errorReporter;
    this.typeRegistry = typeRegistry;
  }

  /**
   * Converts a TypeNode into a SoyType.
   *
   * <p>If any errors are encountered they are reported to the error reporter.
   */
  public SoyType getOrCreateType(TypeNode node) {
    return node.accept(this);
  }

  @Override
  public SoyType visit(NamedTypeNode node) {
    String name = node.name();
    SanitizedType safeProtoType = SoyTypeRegistry.SAFE_PROTO_TO_SANITIZED_TYPE.get(name);
    if (safeProtoType != null) {
      String safeProtoNativeType = safeProtoType.getContentKind().asAttributeValue();
      errorReporter.report(node.sourceLocation(), SAFE_PROTO_TYPE, safeProtoNativeType, name);
    }
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
    node.setResolvedType(type);
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

    SoyType type = genericType.create(Lists.transform(args, this), typeRegistry);
    node.setResolvedType(type);
    return type;
  }

  @Override
  public SoyType visit(UnionTypeNode node) {
    // Copy the result of the transform because transform is lazy. The union evaluation code short
    // circuits if it sees a ? type so for types like ?|list<?> the union evaluation would get
    // short circuited and the lazy transform would never visit list<?>. By copying the transform
    // result (which the transform documentation recommends to avoid lazy evaluation), we ensure
    // that all type nodes are visited.
    SoyType type =
        typeRegistry.getOrCreateUnionType(
            node.candidates().stream().map(this).collect(toImmutableList()));
    node.setResolvedType(type);
    return type;
  }

  @Override
  public SoyType visit(RecordTypeNode node) {
    // LinkedHashMap insertion order iteration on values() is important here.
    Map<String, RecordType.Member> map = Maps.newLinkedHashMap();
    for (RecordTypeNode.Property property : node.properties()) {
      RecordType.Member oldType =
          map.put(
              property.name(), RecordType.memberOf(property.name(), property.type().accept(this)));
      if (oldType != null) {
        errorReporter.report(property.nameLocation(), DUPLICATE_RECORD_FIELD, property.name());
        // restore old mapping and keep going
        map.put(property.name(), oldType);
      }
    }
    SoyType type = typeRegistry.getOrCreateRecordType(map.values());
    node.setResolvedType(type);
    return type;
  }

  @DoNotCall
  @Override
  public SoyType apply(TypeNode node) {
    return node.accept(this);
  }
}
