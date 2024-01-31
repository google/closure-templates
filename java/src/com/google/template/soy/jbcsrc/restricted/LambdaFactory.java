/*
 * Copyright 2023 Google Inc.
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

package com.google.template.soy.jbcsrc.restricted;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static java.util.Arrays.asList;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.jbcsrc.restricted.Expression.Feature;
import com.google.template.soy.jbcsrc.restricted.Expression.Features;
import com.google.template.soy.jbcsrc.shared.ExtraConstantBootstraps;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Type;

/**
 * A helper for generating lambda callsites. Allows for some extra compile time checking and
 * encapsulation.
 */
public final class LambdaFactory {

  private static final Handle METAFACTORY_HANDLE =
      MethodRef.createPure(
              LambdaMetafactory.class,
              "metafactory",
              MethodHandles.Lookup.class,
              String.class,
              MethodType.class,
              MethodType.class,
              MethodHandle.class,
              MethodType.class)
          .asHandle();
  private static final Handle CONDY_METAFACTORY =
      MethodRef.createPure(
              ExtraConstantBootstraps.class,
              "constantMetafactory",
              MethodHandles.Lookup.class,
              String.class,
              Class.class,
              MethodType.class,
              MethodHandle.class,
              MethodType.class)
          .asHandle();

  private static final Handle CONDY_METAFACTORY_WTIH_ARGS =
      MethodRef.createPure(
              ExtraConstantBootstraps.class,
              "constantMetafactoryWithArgs",
              MethodHandles.Lookup.class,
              String.class,
              Class.class,
              MethodType.class,
              MethodHandle.class,
              MethodType.class,
              MethodType.class,
              Object[].class)
          .asHandle();

  /**
   * Create a {@code LambdaFactory} that can create instances of an interface that implements {@code
   * interfaceMethod} by delegating to {@code implMethod}.
   */
  public static LambdaFactory create(MethodRef interfaceMethod, MethodRef implMethod) {
    checkState(interfaceMethod.isInterfaceMethod(), "Only interfaces are supported");
    return new LambdaFactory(interfaceMethod.owner(), interfaceMethod, implMethod);
  }

  private final TypeInfo interfaceType;
  private final String interfaceMethodName;
  private final Type interfaceMethodType;
  private final MethodRef implMethod;
  private final String callSiteDescriptor;
  private final ImmutableList<Type> boundParams;

  private LambdaFactory(TypeInfo interfaceType, MethodRef interfaceMethod, MethodRef implMethod) {
    this.interfaceType = interfaceType;
    this.interfaceMethodName = interfaceMethod.method().getName();
    this.implMethod = implMethod;
    // Skip the first param as it is the receiver type.  MethodRef models 'receivers' as an argument
    // but the jdk models them separately so from the perspective of the lambda-metafactory it isn't
    // part of the interface method signature.
    var interfaceMethodParams =
        interfaceMethod.argTypes().subList(1, interfaceMethod.argTypes().size());
    this.interfaceMethodType =
        Type.getMethodType(
            interfaceMethod.returnType(), interfaceMethodParams.toArray(new Type[0]));
    // Lambdas are essentially an inner classes
    // class Impl extends Interface {
    // Impl(...boundParams) {...}
    // method(..freeParams) { return target(..boundParams, ...freeParams);}
    // }
    // So all 'free' params must be in trailing argument position of the target method and exactly
    // match the interface method parameter types.
    // The JDK does allow for there to be some mismatch, as long as the mismatches are resolvable
    // with simple type coercion rules (aka if the java cast operator could handle it, so could the
    // lambda factory).  However, for now we require an exact match.
    var trailingImplParams =
        implMethod
            .argTypes()
            .subList(
                implMethod.argTypes().size() - interfaceMethodParams.size(),
                implMethod.argTypes().size());
    checkArgument(
        interfaceMethodParams.equals(trailingImplParams),
        "trailing parameters of %s must match the free parameters of %s",
        implMethod,
        interfaceMethod);
    this.boundParams =
        implMethod
            .argTypes()
            .subList(0, implMethod.argTypes().size() - interfaceMethodParams.size());
    this.callSiteDescriptor =
        Type.getMethodDescriptor(interfaceType.type(), boundParams.toArray(new Type[0]));
  }

  public Expression invoke(Expression... args) {
    return invoke(asList(args));
  }

  public Expression invoke(Iterable<Expression> args) {
    Expression.checkTypes(boundParams, args);
    var features = Features.of(Feature.NON_JAVA_NULLABLE, Feature.NON_SOY_NULLISH);
    if (Expression.areAllCheap(args)) {
      features = features.plus(Feature.CHEAP);
    }
    if (Expression.areAllConstant(args)) {
      // When there are no bound parameters we can link this with condy instead of indy.
      // According to Brian Goetz this should be faster to link even if the impls are identical
      // See https://mail.openjdk.org/pipermail/amber-dev/2023-October/008327.html
      var bytecodeArgs =
          ImmutableList.<Object>builder()
              .add(interfaceMethodType)
              .add(implMethod.asHandle())
              .add(interfaceMethodType);
      if (!boundParams.isEmpty()) {
        bytecodeArgs.add(Type.getType(callSiteDescriptor));
        for (Expression arg : args) {
          bytecodeArgs.add(arg.constantBytecodeValue());
        }
      }
      return BytecodeUtils.constant(
          interfaceType.type(),
          new ConstantDynamic(
              interfaceMethodName,
              interfaceType.type().getDescriptor(),
              boundParams.isEmpty() ? CONDY_METAFACTORY : CONDY_METAFACTORY_WTIH_ARGS,
              bytecodeArgs.build().toArray()),
          features);
    }
    return new Expression(interfaceType.type(), features) {
      @Override
      protected void doGen(CodeBuilder cb) {
        for (Expression arg : args) {
          arg.gen(cb);
        }
        cb.visitInvokeDynamicInsn(
            interfaceMethodName,
            callSiteDescriptor,
            METAFACTORY_HANDLE,
            interfaceMethodType,
            implMethod.asHandle(),
            interfaceMethodType);
      }
    };
  }
}
