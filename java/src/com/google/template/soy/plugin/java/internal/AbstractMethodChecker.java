/*
 * Copyright 2021 Google Inc.
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
package com.google.template.soy.plugin.java.internal;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.plugin.java.MethodChecker;
import com.google.template.soy.plugin.java.ReadMethodData;
import com.google.template.soy.plugin.java.internal.CompiledJarsPluginSignatureReader.ClassSignatures;
import com.google.template.soy.plugin.java.internal.CompiledJarsPluginSignatureReader.MethodSignatures;
import com.google.template.soy.plugin.java.internal.CompiledJarsPluginSignatureReader.PartialSignature;
import java.util.List;

/** Interface for validating a Java method. */
public abstract class AbstractMethodChecker implements MethodChecker {

  @Override
  public final Response findMethod(
      String className, String methodName, String returnType, List<String> arguments) {
    ClassSignatures signatures = getSignatures(className);
    if (signatures.equals(ClassSignatures.EMPTY)) {
      return Response.error(Code.NO_SUCH_CLASS);
    }
    MethodSignatures methodsForSig =
        signatures.forPartial(PartialSignature.create(methodName, ImmutableList.copyOf(arguments)));
    if (methodsForSig.equals(MethodSignatures.EMPTY)) {
      ImmutableList<PartialSignature> methodsMatchingName =
          signatures.allPartials().stream()
              .filter(p -> p.methodName().equals(methodName))
              .collect(toImmutableList());
      if (methodsMatchingName.isEmpty()) {
        return Response.error(
            Code.NO_SUCH_METHOD_NAME,
            signatures.allPartials().stream()
                .map(PartialSignature::methodName)
                .collect(toImmutableList()));
      } else {
        ImmutableList<String> suggestedSigs =
            methodsMatchingName.stream().map(PartialSignature::toString).collect(toImmutableList());
        return Response.error(Code.NO_SUCH_METHOD_SIG, suggestedSigs);
      }
    }
    ReadMethodData method = methodsForSig.forReturnType(returnType);
    if (method != null) {
      if (!method.isPublic()) {
        return Response.error(Code.NOT_PUBLIC);
      }
      return Response.success(method);
    }
    return Response.error(Code.NO_SUCH_RETURN_TYPE, methodsForSig.returnTypes());
  }

  protected abstract ClassSignatures getSignatures(String className);
}
