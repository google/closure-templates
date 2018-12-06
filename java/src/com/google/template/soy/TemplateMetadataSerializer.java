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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.base.internal.SoyFileKind;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.soyparse.SoyFileParser;
import com.google.template.soy.soytree.CallSituationP;
import com.google.template.soy.soytree.CompilationUnit;
import com.google.template.soy.soytree.ParameterP;
import com.google.template.soy.soytree.SanitizedContentKindP;
import com.google.template.soy.soytree.SourceLocationP;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileP;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.TemplateDelegateNodeBuilder;
import com.google.template.soy.soytree.TemplateKindP;
import com.google.template.soy.soytree.TemplateMetadata;
import com.google.template.soy.soytree.TemplateMetadata.CallSituation;
import com.google.template.soy.soytree.TemplateMetadata.Kind;
import com.google.template.soy.soytree.TemplateMetadata.Parameter;
import com.google.template.soy.soytree.TemplateMetadataP;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.TemplateRegistry;
import com.google.template.soy.soytree.Visibility;
import com.google.template.soy.soytree.VisibilityP;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyTypeRegistry;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/** Utilities to transform TemplateMetadata objects to and From CompilationUnit protos */
public final class TemplateMetadataSerializer {
  /** A simple interface to abstract type parsing to avoid a package cycle. */
  public interface TypeResolver {
    /**
     * Returns a soy type for the given string.
     *
     * @throws IllegalArgumentException with a syntax error if appropriate.
     */
    SoyType parseType(String typeString);
  }

  private static final Converter<VisibilityP, Visibility> VISIBILITY_CONVERTER =
      createEnumConverter(VisibilityP.class, Visibility.class);
  private static final Converter<TemplateKindP, TemplateMetadata.Kind> TEMPLATE_KIND_CONVERTER =
      createEnumConverter(TemplateKindP.class, TemplateMetadata.Kind.class);
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
      for (TemplateNode template : file.getChildren()) {
        TemplateMetadata meta = registry.getMetadata(template);
        fileBuilder.addTemplate(protoFromTemplate(meta, file));
      }
      builder.addFile(fileBuilder.build());
    }
    return builder.build();
  }

  public static ImmutableList<TemplateMetadata> templatesFromCompilationUnit(
      CompilationUnit compilationUnit, SoyFileKind fileKind, SoyTypeRegistry typeRegistry) {
    ImmutableList.Builder<TemplateMetadata> templates = ImmutableList.builder();
    for (SoyFileP fileProto : compilationUnit.getFileList()) {
      for (TemplateMetadataP templateProto : fileProto.getTemplateList()) {
        try {
          templates.add(metadataFromProto(fileProto, templateProto, fileKind, typeRegistry));
        } catch (IllegalArgumentException iae) {
          throw new IllegalArgumentException(
              "Unable to parse template: "
                  + templateProto.getTemplateName()
                  + " from file: "
                  + fileProto.getFilePath()
                  + ": "
                  + iae.getMessage(),
              iae);
        }
      }
    }
    return templates.build();
  }

  private static TemplateMetadataP protoFromTemplate(TemplateMetadata meta, SoyFileNode fileNode) {
    return TemplateMetadataP.newBuilder()
        .setTemplateName(
            meta.getTemplateKind() == Kind.DELTEMPLATE
                ? meta.getDelTemplateName()
                : maybeShortenTemplateName(fileNode.getNamespace(), meta.getTemplateName()))
        .setTemplateKind(TEMPLATE_KIND_CONVERTER.reverse().convert(meta.getTemplateKind()))
        .setVisibility(VISIBILITY_CONVERTER.reverse().convert(meta.getVisibility()))
        .setContentKind(
            meta.getContentKind() == null
                ? SanitizedContentKindP.NONE
                : CONTENT_KIND_CONVERTER.reverse().convert(meta.getContentKind()))
        .setSourceLocation(protoFromSourceLocation(meta.getSourceLocation()))
        .setDelTemplateVariant(Strings.nullToEmpty(meta.getDelTemplateVariant()))
        .setStrictHtml(meta.isStrictHtml())
        .addAllCallSituation(protosFromCallSitatuations(meta.getCallSituations(), fileNode))
        .addAllParameter(protosFromParameters(meta.getParameters()))
        .build();
  }

  private static TemplateMetadata metadataFromProto(
      SoyFileP fileProto,
      TemplateMetadataP templateProto,
      SoyFileKind fileKind,
      SoyTypeRegistry typeRegistry) {
    TemplateMetadata.Builder builder = TemplateMetadata.builder();
    TemplateMetadata.Kind templateKind =
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
        .setStrictHtml(templateProto.getStrictHtml())
        .setTemplateKind(templateKind)
        .setSourceLocation(sourceLocationFromProto(fileProto, templateProto.getSourceLocation()))
        .setContentKind(
            templateProto.getContentKind() == SanitizedContentKindP.NONE
                ? null
                : CONTENT_KIND_CONVERTER.convert(templateProto.getContentKind()))
        .setVisibility(VISIBILITY_CONVERTER.convert(templateProto.getVisibility()))
        .setCallSituations(callSituationsFromProto(templateProto.getCallSituationList(), fileProto))
        .setParameters(parametersFromProto(templateProto.getParameterList(), typeRegistry))
        .build();
  }

  private static ImmutableList<Parameter> parametersFromProto(
      List<ParameterP> parameterList, SoyTypeRegistry typeRegistry) {
    ImmutableList.Builder<Parameter> builder =
        ImmutableList.builderWithExpectedSize(parameterList.size());
    for (ParameterP parameter : parameterList) {
      ErrorReporter errorReporter = ErrorReporter.create(ImmutableMap.of());
      SoyType type =
          SoyFileParser.parseType(
              parameter.getType(), typeRegistry, /*filePath=*/ "---", errorReporter);
      if (type == null) {
        throw new IllegalArgumentException(
            "Unable to parse the type for parameter: "
                + parameter.getName()
                + ": "
                + parameter.getType()
                + ": "
                + errorReporter.getErrors().asList().get(0).message());
      }
      builder.add(
          Parameter.builder()
              .setName(parameter.getName())
              .setType(type)
              .setInjected(parameter.getInjected())
              .setRequired(parameter.getRequired())
              .build());
    }
    return builder.build();
  }

  private static ImmutableList<ParameterP> protosFromParameters(List<Parameter> parameterList) {
    ImmutableList.Builder<ParameterP> builder =
        ImmutableList.builderWithExpectedSize(parameterList.size());
    for (Parameter parameter : parameterList) {
      builder.add(
          ParameterP.newBuilder()
              .setName(parameter.getName())
              .setType(parameter.getType().toString())
              .setInjected(parameter.isInjected())
              .setRequired(parameter.isRequired())
              .build());
    }
    return builder.build();
  }

  private static ImmutableList<CallSituation> callSituationsFromProto(
      List<CallSituationP> callSituationList, SoyFileP fileProto) {
    ImmutableList.Builder<CallSituation> builder =
        ImmutableList.builderWithExpectedSize(callSituationList.size());
    for (CallSituationP call : callSituationList) {
      String templateName = call.getTemplateName();
      if (templateName.startsWith(".")) {
        templateName = fileProto.getNamespace() + templateName;
      }
      builder.add(
          CallSituation.builder()
              .setTemplateName(templateName)
              .setDelCall(call.getDelCall())
              .setDataAllCall(call.getDataAllCall())
              .setExplicitlyPassedParametersForDataAllCalls(
                  ImmutableSet.copyOf(call.getExplicitlyPassedParametersForDataAllCallsList()))
              .build());
    }
    return builder.build();
  }

  private static ImmutableList<CallSituationP> protosFromCallSitatuations(
      ImmutableList<CallSituation> callSituationList, SoyFileNode fileNode) {
    ImmutableList.Builder<CallSituationP> builder =
        ImmutableList.builderWithExpectedSize(callSituationList.size());
    for (CallSituation call : callSituationList) {
      builder.add(
          CallSituationP.newBuilder()
              .setTemplateName(
                  call.isDelCall()
                      ? call.getTemplateName()
                      : maybeShortenTemplateName(fileNode.getNamespace(), call.getTemplateName()))
              .setDelCall(call.isDelCall())
              .setDataAllCall(call.isDataAllCall())
              .addAllExplicitlyPassedParametersForDataAllCalls(
                  call.getExplicitlyPassedParametersForDataAllCalls())
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

  private static SourceLocation sourceLocationFromProto(
      SoyFileP fileProto, SourceLocationP sourceLocation) {
    return new SourceLocation(
        fileProto.getFilePath(),
        sourceLocation.getStartLine(),
        sourceLocation.getStartColumn(),
        sourceLocation.getEndLine(),
        sourceLocation.getEndColumn());
  }

  private static SourceLocationP protoFromSourceLocation(SourceLocation location) {
    return SourceLocationP.newBuilder()
        .setStartLine(location.getBeginLine())
        .setStartColumn(location.getBeginColumn())
        .setEndLine(location.getEndLine())
        .setEndColumn(location.getEndColumn())
        .build();
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
