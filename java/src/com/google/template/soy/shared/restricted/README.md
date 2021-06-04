# Soy methods

Currently Soy methods are for internal use only. `@SoyMethodSignature` is
visibility restricted.

## How do I create a plugin method?

Creating a plugin method is very similar to creating a function. The main
difference is that a method applies to a particular type, which is the receiver
of the method call. Polymorphism is supported, so a method named `hello()` on
type `string` is distinct from the method `hello()` on type `float`.

To create a method use the `@SoyMethodSignature` annotation instead of (or in
addition to) the `@SoyFunctionSignature`. The annotated class must also be an
implementation of `SoySourceFunction`.

```java
@SoyMethodSignature(
    name = "reverse",
    baseType = "string",
    value = @Signature(returnType="string"))
class ReverseStringMethod implements SoySourceFunction {}
```

The `value` property of `@SoyMethodSignature` is identical to that of
`@SoyFunctionSignature`. Methods also support overloading by specifying multiple
signature values here. However, unlike functions, methods may also be overloaded
across multiple `SoySourceFunction` implementations.

The Java, JavaScript, and Python implementations of the plugin method share the
exact same APIs as that of functions. The only difference is that the object
receiving the method call is passed as the first function argument, followed by
the method arguments if any. Using this fact you can have the same
`SoySourceFunction` class provide both a function and a method, for example,

```java
@SoyFunctionSignature(
    name = "strReverse",
    value = @Signature(argTypes="string", returnType="string"))
@SoyMethodSignature(
    name = "reverse",
    baseType = "string",
    value = @Signature(returnType="string"))
class ReverseStringMethod implements SoySourceFunction {}
```

Methods are registered in the exact same way as functions, by passing them to
the `--pluginFunctions` flag. Every class passed to this flag must define either
a function signature, a method signature, or both.
