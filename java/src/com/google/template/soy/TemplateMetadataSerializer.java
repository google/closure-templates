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
package com.google.template.soy;

import static com.google.common.base.Strings.emptyToNull;

import com.google.common.base.Converter;
import com.google.common.base.Strings;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.base.internal.SoyFileKind;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.soytree.CompilationUnit;
import com.google.template.soy.soytree.DataAllCallSituationP;
import com.google.template.soy.soytree.ParameterP;
import com.google.template.soy.soytree.SanitizedContentKindP;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileP;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyTypeP;
import com.google.template.soy.soytree.TemplateDelegateNodeBuilder;
import com.google.template.soy.soytree.TemplateKindP;
import com.google.template.soy.soytree.TemplateMetadata;
import com.google.template.soy.soytree.TemplateMetadata.DataAllCallSituation;
import com.google.template.soy.soytree.TemplateMetadata.Parameter;
import com.google.template.soy.soytree.TemplateMetadataP;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.TemplateRegistry;
import com.google.template.soy.soytree.Visibility;
import com.google.template.soy.soytree.VisibilityP;
import com.google.template.soy.types.AnyType;
import com.google.template.soy.types.BoolType;
import com.google.template.soy.types.ErrorType;
import com.google.template.soy.types.FloatType;
import com.google.template.soy.types.IntType;
import com.google.template.soy.types.MessageType;
import com.google.template.soy.types.NullType;
import com.google.template.soy.types.RecordType;
import com.google.template.soy.types.SanitizedType.AttributesType;
import com.google.template.soy.types.SanitizedType.HtmlType;
import com.google.template.soy.types.SanitizedType.JsType;
import com.google.template.soy.types.SanitizedType.StyleType;
import com.google.template.soy.types.SanitizedType.TrustedResourceUriType;
import com.google.template.soy.types.SanitizedType.UriType;
import com.google.template.soy.types.SoyProtoEnumType;
import com.google.template.soy.types.SoyProtoType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyTypeRegistry;
import com.google.template.soy.types.StringType;
import com.google.template.soy.types.TemplateType;
import com.google.template.soy.types.UnknownType;
import com.google.template.soy.types.VeDataType;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/** Utilities to transform TemplateMetadata objects to and From CompilationUnit protos */
public final class TemplateMetadataSerializer {
  private static final SoyErrorKind UNABLE_TO_PARSE_TEMPLATE_HEADER =
      SoyErrorKind.of("Unable to parse template header for {0} from Soy file {1}: {2}.");
  private static final SoyErrorKind UNABLE_TO_FIND_TYPE =
      SoyErrorKind.of("Unable to find {0}: {1} referenced by dependency.");
  private static final SoyErrorKind UNEXPECTED_TYPE =
      SoyErrorKind.of("Expected {0} to be a {1} but it was a {2}.");

  private static final Converter<VisibilityP, Visibility> VISIBILITY_CONVERTER =
      createEnumConverter(VisibilityP.class, Visibility.class);
  private static final Converter<TemplateKindP, TemplateType.TemplateKind> TEMPLATE_KIND_CONVERTER =
      createEnumConverter(TemplateKindP.class, TemplateType.TemplateKind.class);
  private static final Converter<SanitizedContentKindP, SanitizedContentKind>
      CONTENT_KIND_CONVERTER =
          createEnumConverter(SanitizedContentKindP.class, SanitizedContentKind.class);

  private TemplateMetadataSerializer() {}

  public static CompilationUnit compilationUnitFromFileSet(
      SoyFileSetNode fileSet, TemplateRegistry registry) {
    CompilationUnit.Builder builder = CompilationUnit.newBuilder();
    for (SoyFileNode file : fileSet.getChildren()) {
      SoyFileP.Builder fileBuilder =
          SoyFileP.newBuilder()
              .setNamespace(file.getNamespace())
              .setDelpackage(Strings.nullToEmpty(file.getDelPackageName()))
              .setFilePath(file.getFilePath());
      for (TemplateNode template : file.getTemplates()) {
        TemplateMetadata meta = registry.getMetadata(template);
        fileBuilder.addTemplate(protoFromTemplate(meta, file));
      }
      builder.addFile(fileBuilder.build());
    }
    return builder.build();
  }

  public static ImmutableList<TemplateMetadata> templatesFromSoyFileP(
      SoyFileP fileProto,
      SoyFileKind fileKind,
      SoyTypeRegistry typeRegistry,
      String headerFilePath,
      ErrorReporter errorReporter) {
    ImmutableList.Builder<TemplateMetadata> templates = ImmutableList.builder();
      for (TemplateMetadataP templateProto : fileProto.getTemplateList()) {
        try {
        templates.add(
            metadataFromProto(
                fileProto, templateProto, fileKind, typeRegistry, headerFilePath, errorReporter));
        } catch (IllegalArgumentException iae) {
        errorReporter.report(
            new SourceLocation(headerFilePath),
            UNABLE_TO_PARSE_TEMPLATE_HEADER,
            templateProto.getTemplateName(),
            fileProto.getFilePath(),
            iae.getMessage());
      }
    }
    return templates.build();
  }

  private static TemplateMetadataP protoFromTemplate(TemplateMetadata meta, SoyFileNode fileNode) {
    TemplateMetadataP.Builder builder =
        TemplateMetadataP.newBuilder()
            .setTemplateName(
                meta.getTemplateKind() == TemplateType.TemplateKind.DELTEMPLATE
                    ? meta.getDelTemplateName()
                    : maybeShortenTemplateName(fileNode.getNamespace(), meta.getTemplateName()))
            .setTemplateKind(TEMPLATE_KIND_CONVERTER.reverse().convert(meta.getTemplateKind()))
            .setVisibility(VISIBILITY_CONVERTER.reverse().convert(meta.getVisibility()))
            .setContentKind(
                meta.getContentKind() == null
                    ? SanitizedContentKindP.NONE
                    : CONTENT_KIND_CONVERTER.reverse().convert(meta.getContentKind()))
            .setDelTemplateVariant(Strings.nullToEmpty(meta.getDelTemplateVariant()))
            .setStrictHtml(meta.isStrictHtml())
            .addAllDataAllCallSituation(
                protosFromCallSitatuations(meta.getDataAllCallSituations(), fileNode))
            .addAllParameter(protosFromParameters(meta.getParameters()));
    // This may be null because some flows such as conformance tests do not run the SoyElementPass.
    if (meta.getHtmlElement() != null && meta.getSoyElement() != null) {
      builder = builder.setHtmlElement(meta.getHtmlElement()).setSoyElement(meta.getSoyElement());
    }
    return builder.build();
  }

  private static TemplateMetadata metadataFromProto(
      SoyFileP fileProto,
      TemplateMetadataP templateProto,
      SoyFileKind fileKind,
      SoyTypeRegistry typeRegistry,
      String filePath,
      ErrorReporter errorReporter) {
    TemplateMetadata.Builder builder = TemplateMetadata.builder();
    TemplateType.TemplateKind templateKind =
        TEMPLATE_KIND_CONVERTER.convert(templateProto.getTemplateKind());
    @Nullable String delPackageName = emptyToNull(fileProto.getDelpackage());
    String templateName;
    switch (templateKind) {
      case ELEMENT:
      case BASIC:
        templateName = fileProto.getNamespace() + templateProto.getTemplateName();
        break;
      case DELTEMPLATE:
        String variant = templateProto.getDelTemplateVariant();
        String delTemplateName = templateProto.getTemplateName();
        templateName =
            fileProto.getNamespace()
                + TemplateDelegateNodeBuilder.partialDeltemplateTemplateName(
                    delTemplateName, delPackageName, variant);
        builder.setDelTemplateVariant(variant).setDelTemplateName(delTemplateName);
        break;
      default:
        throw new AssertionError();
    }
    return builder
        .setTemplateName(templateName)
        .setSoyFileKind(fileKind)
        .setDelPackageName(delPackageName)
        .setHtmlElement(templateProto.getHtmlElement())
        .setSoyElement(templateProto.getSoyElement())
        .setStrictHtml(templateProto.getStrictHtml())
        .setTemplateKind(templateKind)
        .setSourceLocation(new SourceLocation(fileProto.getFilePath()))
        .setContentKind(
            templateProto.getContentKind() == SanitizedContentKindP.NONE
                ? null
                : CONTENT_KIND_CONVERTER.convert(templateProto.getContentKind()))
        .setVisibility(VISIBILITY_CONVERTER.convert(templateProto.getVisibility()))
        .setDataAllCallSituations(
            callSituationsFromProto(templateProto.getDataAllCallSituationList(), fileProto))
        .setParameters(
            parametersFromProto(
                templateProto.getParameterList(), typeRegistry, filePath, errorReporter))
        .build();
  }

  private static ImmutableList<Parameter> parametersFromProto(
      List<ParameterP> parameterList,
      SoyTypeRegistry typeRegistry,
      String filePath,
      ErrorReporter errorReporter) {
    ImmutableList.Builder<Parameter> builder =
        ImmutableList.builderWithExpectedSize(parameterList.size());
    for (ParameterP parameter : parameterList) {
      builder.add(
          Parameter.builder()
              .setName(parameter.getName())
              .setRequired(parameter.getRequired())
              .setTypeLazily(
                  new SoyTypeSupplier(parameter.getType(), typeRegistry, filePath, errorReporter))
              .build());
    }
    return builder.build();
  }

  /**
   * Lazily parses a type.
   *
   * <p>We do this lazily because for many compiles we never access these types. For many
   * dependencies there are templates that are never called by the source templates, so there is no
   * point in fully resolving its types.
   */
  private static final class SoyTypeSupplier implements Supplier<SoyType> {
    final SoyTypeP typeProto;
    final SoyTypeRegistry typeRegistry;
    final String filePath;
    final ErrorReporter errorReporter;

    SoyTypeSupplier(
        SoyTypeP type, SoyTypeRegistry typeRegistry, String filePath, ErrorReporter errorReporter) {
      this.typeProto = type;
      this.typeRegistry = typeRegistry;
      this.filePath = filePath;
      this.errorReporter = errorReporter;
    }

    @Override
    public SoyType get() {
      return fromProto(typeProto, typeRegistry, filePath, errorReporter);
    }
  }

  private static SoyType fromProto(
      SoyTypeP proto, SoyTypeRegistry typeRegistry, String filePath, ErrorReporter errorReporter) {
    switch (proto.getTypeKindCase()) {
      case PRIMITIVE:
        switch (proto.getPrimitive()) {
          case ANY:
            return AnyType.getInstance();
          case UNKNOWN:
            return UnknownType.getInstance();
          case INT:
            return IntType.getInstance();
          case NULL:
            return NullType.getInstance();
          case BOOL:
            return BoolType.getInstance();
          case FLOAT:
            return FloatType.getInstance();
          case STRING:
            return StringType.getInstance();
          case HTML:
            return HtmlType.getInstance();
          case ATTRIBUTES:
            return AttributesType.getInstance();
          case JS:
            return JsType.getInstance();
          case CSS:
            return StyleType.getInstance();
          case URI:
            return UriType.getInstance();
          case TRUSTED_RESOURCE_URI:
            return TrustedResourceUriType.getInstance();
          case VE_DATA:
            return VeDataType.getInstance();
          case UNRECOGNIZED:
          case UNKNOWN_PRIMITIVE_TYPE:
            // fall-through
        }
        throw new AssertionError("Unknown primitive: " + proto.getPrimitive());
      case LIST_ELEMENT:
        return typeRegistry.getOrCreateListType(
            fromProto(proto.getListElement(), typeRegistry, filePath, errorReporter));

      case LEGACY_OBJECT_MAP:
        return typeRegistry.getOrCreateLegacyObjectMapType(
            fromProto(proto.getLegacyObjectMap().getKey(), typeRegistry, filePath, errorReporter),
            fromProto(
                proto.getLegacyObjectMap().getValue(), typeRegistry, filePath, errorReporter));
      case MAP:
        return typeRegistry.getOrCreateMapType(
            fromProto(proto.getMap().getKey(), typeRegistry, filePath, errorReporter),
            fromProto(proto.getMap().getValue(), typeRegistry, filePath, errorReporter));
      case MESSAGE:
        return MessageType.getInstance();
      case PROTO:
        {
          SoyType type = typeRegistry.getProtoRegistry().getProtoType(proto.getProto());
          if (type == null) {
            errorReporter.report(
                new SourceLocation(filePath), UNABLE_TO_FIND_TYPE, "proto", proto.getProto());
            return ErrorType.getInstance();
          }
          // allow unknown to support message extraction which configures the DEFAULT_UNKNOWN type
          // registry
          if (type instanceof SoyProtoType || type == UnknownType.getInstance()) {
            return type;
          }
          errorReporter.report(
              new SourceLocation(filePath),
              UNEXPECTED_TYPE,
              proto.getProto(),
              "proto",
              type.getKind());
          return ErrorType.getInstance();
        }
      case PROTO_ENUM:
        {
          SoyType type = typeRegistry.getProtoRegistry().getProtoType(proto.getProtoEnum());
          if (type == null) {
            errorReporter.report(
                new SourceLocation(filePath),
                UNABLE_TO_FIND_TYPE,
                "proto enum",
                proto.getProtoEnum());
            return ErrorType.getInstance();
          }
          // allow unknown to support message extraction which configures the DEFAULT_UNKNOWN type
          // registry
          if (type instanceof SoyProtoEnumType || type == UnknownType.getInstance()) {
            return type;
          }
          errorReporter.report(
              new SourceLocation(filePath),
              UNEXPECTED_TYPE,
              proto.getProtoEnum(),
              "proto enum",
              type.getKind());
          return ErrorType.getInstance();
        }
      case RECORD:
        {
          List<RecordType.Member> members = new ArrayList<>();
          // TODO: this relies on proto map insertion order, which is not guaranteed by the spec.
          for (Map.Entry<String, SoyTypeP> entry : proto.getRecord().getFieldMap().entrySet()) {
            members.add(
                RecordType.memberOf(
                    entry.getKey(),
                    fromProto(entry.getValue(), typeRegistry, filePath, errorReporter)));
          }
          return typeRegistry.getOrCreateRecordType(members);
        }
      case TEMPLATE:
        {
          List<TemplateType.Parameter> parameters = new ArrayList<>();
          // TODO: this relies on proto map insertion order, which is not guaranteed by the spec.
          for (Map.Entry<String, SoyTypeP> entry :
              proto.getTemplate().getParameterMap().entrySet()) {
            parameters.add(
                TemplateType.Parameter.create(
                    entry.getKey(),
                    fromProto(entry.getValue(), typeRegistry, filePath, errorReporter),
                    true /* isRequired */));
          }
          return typeRegistry.internTemplateType(
              TemplateType.declaredTypeOf(
                  parameters,
                  fromProto(
                      SoyTypeP.newBuilder()
                          .setPrimitive(proto.getTemplate().getReturnType())
                          .build(),
                      typeRegistry,
                      filePath,
                      errorReporter)));
        }
      case UNION:
        {
          List<SoyType> members = new ArrayList<>(proto.getUnion().getMemberCount());
          for (SoyTypeP member : proto.getUnion().getMemberList()) {
            members.add(fromProto(member, typeRegistry, filePath, errorReporter));
          }
          return typeRegistry.getOrCreateUnionType(members);
        }
      case VE:
        return typeRegistry.getOrCreateVeType(proto.getVe());
      case TYPEKIND_NOT_SET:
        // fall-through
    }
    throw new AssertionError("unhandled typeKind: " + proto.getTypeKindCase());
  }

  private static ImmutableList<ParameterP> protosFromParameters(List<Parameter> parameterList) {
    ImmutableList.Builder<ParameterP> builder =
        ImmutableList.builderWithExpectedSize(parameterList.size());
    for (Parameter parameter : parameterList) {
      builder.add(
          ParameterP.newBuilder()
              .setName(parameter.getName())
              .setType(parameter.getType().toProto())
              .setRequired(parameter.isRequired())
              .build());
    }
    return builder.build();
  }

  private static ImmutableList<DataAllCallSituation> callSituationsFromProto(
      List<DataAllCallSituationP> callSituationList, SoyFileP fileProto) {
    ImmutableList.Builder<DataAllCallSituation> builder =
        ImmutableList.builderWithExpectedSize(callSituationList.size());
    for (DataAllCallSituationP call : callSituationList) {
      String templateName = call.getTemplateName();
      if (templateName.startsWith(".")) {
        templateName = fileProto.getNamespace() + templateName;
      }
      builder.add(
          DataAllCallSituation.builder()
              .setTemplateName(templateName)
              .setDelCall(call.getDelCall())
              .setExplicitlyPassedParameters(
                  ImmutableSet.copyOf(call.getExplicitlyPassedParametersList()))
              .build());
    }
    return builder.build();
  }

  private static ImmutableList<DataAllCallSituationP> protosFromCallSitatuations(
      ImmutableList<DataAllCallSituation> callSituationList, SoyFileNode fileNode) {
    ImmutableList.Builder<DataAllCallSituationP> builder =
        ImmutableList.builderWithExpectedSize(callSituationList.size());
    for (DataAllCallSituation call : callSituationList) {
      builder.add(
          DataAllCallSituationP.newBuilder()
              .setTemplateName(
                  call.isDelCall()
                      ? call.getTemplateName()
                      : maybeShortenTemplateName(fileNode.getNamespace(), call.getTemplateName()))
              .setDelCall(call.isDelCall())
              .addAllExplicitlyPassedParameters(call.getExplicitlyPassedParameters())
              .build());
    }
    return builder.build();
  }

  private static String maybeShortenTemplateName(String namespace, String templateName) {
    if (templateName.startsWith(namespace)
        && templateName.indexOf('.', namespace.length() + 2) == -1) {
      return templateName.substring(namespace.length());
    }
    return templateName;
  }

  private static <T1 extends Enum<T1>, T2 extends Enum<T2>> Converter<T1, T2> createEnumConverter(
      final Class<T1> t1, final Class<T2> t2) {
    Map<String, T1> t1NameMap = new HashMap<>();
    for (T1 instance : t1.getEnumConstants()) {
      t1NameMap.put(instance.name(), instance);
    }
    final EnumMap<T1, T2> forwardMap = new EnumMap<>(t1);
    final EnumMap<T2, T1> backwardMap = new EnumMap<>(t2);
    for (T2 t2Instance : t2.getEnumConstants()) {
      T1 t1Instance = t1NameMap.remove(t2Instance.name());
      if (t1Instance != null) {
        forwardMap.put(t1Instance, t2Instance);
        backwardMap.put(t2Instance, t1Instance);
      }
    }
    return new Converter<T1, T2>() {
      @Override
      protected T2 doForward(T1 a) {
        T2 r = forwardMap.get(a);
        if (r == null) {
          throw new IllegalArgumentException("Failed to map: " + a + " to an instance of " + t2);
        }
        return r;
      }

      @Override
      protected T1 doBackward(T2 b) {
        T1 r = backwardMap.get(b);
        if (r == null) {
          throw new IllegalArgumentException("Failed to map: " + b + " to an instance of " + t1);
        }
        return r;
      }
    };
  }
}
