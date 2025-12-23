# Dealing with nullable types

In Soy it is common to have optional
[parameters](../reference/templates.md#param) and other
[nullable types](../reference/types#null). This is useful since the compiler
will prevent you from accidentally dereferencing potentially nullish values and
it provides additional information to callers. However, for these same reasons
they can be difficult to work with. This page shows a few strategies for dealing
with `null` and `undefined` in your templates.

[TOC]

## Static type narrowing in conditional blocks

**Note**: Conditionals in Soy follow the rules of Javascript truthiness. See
https://developer.mozilla.org/en-US/docs/Glossary/Truthy.

When a template contains an optional parameter (`@param?`), if the compiler can
prove the parameter cannot be `undefined` it will change the expression type to
be non-optional. For example:

```soy
import {Person} from 'foo/bar.proto';

{template main}
  /** Optional parameter of type (Person|undefined). */
  {@param? person: Person}
  {if $person}
    // Within this if-block, $person can never be null, so its type
    // is now ‘Person’, not ‘(Person|undefined)’
    {$person.getName()}
  {else}
    // Compile-time error: $person can only be undefined at this point.
    {$person.getName()}
  {/if}
{/template}
```

This type narrowing feature is triggered by the various control flow mechanisms:

*   [if statements](../reference/control-flow#if)
*   [and/or operators](../reference/expressions#logical-operators)
*   [nullish coalescing operator](../reference/expressions#nullish-coalescing-operator)
*   [ternary operator](../reference/expressions#ternary)

When the predicate of the conditional is a comparison with `null` or `undefined`
the compiler is able to narrow the type on each side of the branch.

For example consider these expressions, `$foo ? A : B`, `$foo != null ? A : B`.
In each example, the variable `$foo` is compared with `null` either implicitly
or explicitly, so within the `A` branch we know that all references to `$foo`
are guaranteed to be non-null and so the type is modified to reflect that.
Furthermore within the `B` branch we know that `$foo` is `null` or at least is
`falsy`.

## `checkNotNull` function

The [`checkNotNull`](../reference/functions#checkNotNull) function will either
return its parameter or throw an unspecified exception if the provided value is
nullish. Additionally the type checker understands this behavior and so this can
be used as a cast operator to turn nullable types into non-nullable types.
