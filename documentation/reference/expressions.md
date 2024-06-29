# Expressions

Expressions are written within templates to reference template data, variables,
or compute intermediate values. Soy uses a language-neutral expression syntax.
This section describes the expression grammar, including how to reference data,
write literals for all the primitive types, and use basic operators.

[TOC]

## Where are expressions evaluated?

Expressions show up in lots of different places in Soy. The most common place is
in [print commands](print.md), but they are also used in
[if commands](control-flow.md#if), [let declarations](let.md) and many other
places. Here are a few examples:

```soy
{template foo}
  {@param p: ?}
  {if $p > 2}
    {call bar}{param p : $p == 3 ? 'a' : 'b' /}{/call}
  {/if}
{/template}
```

In the above example, we can see a parameter `p` being used in several
expressions for several different Soy commands.

## Literals

### bool

Boolean literals have two values: `false` and `true`

### int

Integer literals can be specified using either hexadecimal or decimal notation.

Examples:

*   `0xNN`: hexadecimal number
    *   `0xFF`, `Ox00FF00FF`
*   `NNN`: decimal number
    *   `1`, `45`

Soy integers are limited to what is representable in JavaScript. This means that
in practice, values above 2^53 are not guaranteed to be precisely represented.
See
https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Number/MAX_SAFE_INTEGER
for more information.

### float

Floating point literals can be written using either decimal or scientific
format.

For example,

*   `NNN.NN`: decimal format
    *   `22.1`, `1.0`
*   `NN.NeNN`: scientific notation
    *   `6.03e23`

### string {#string-literal}

String literals are delimited by either single or double quote characters and
support standard escape sequences:

*   Standard escapes: `\\ ` `\'` `\"` `\n` `\r` `\t` `\b` `\f`
*   Unicode escapes: `\u####` (backslash, "u", four hex digits )
*   Hex escapes: `\x##` (backslash, "x", two hex digits )
*   Octal escapes: `\NNN` (backslash, followed by 2-3 octal digits )

Examples:

*   `''`, `""` empty string
*   `'abc'` simple literal
*   `'aa\\bb\'cc\ndd'` literal with escaped quotation marks and backslashes
*   `'\u263a'` unicode escape

Long string literals can be split across lines with the `+` operator:

```soy
"abc" +
"def"
```

or a [`let` command](let.md) using the `text` kind:

```soy
{let $longString kind="text"}
abc
def
{/let}
```

### list

list literals are delimited by square brackets and contain a comma separated
list of expressions.

For example:

*   `[]` empty list
*   `['a', 'b']` list containing two elements

You can use the `...` operator to spread one or more lists inside a list
literal.

*   `{let $l2: [1, 2, ...$l1] /}`

### map {#map}

There are two different syntaxes for creating map literals. They are both
delimited by `map()`.

1.  They can contain a comma-delimited sequence of key-value pairs separated by
    `:` characters. For example,

    *   `map()`: the empty map
    *   `map(1: 'one', 2: 'two')`

2.  They can contain a list whose elements are each a [record](#record) with
    exactly two fields named **key** and **value**. The following examples
    produce the same maps as the above ones:

    *   `{let $arr: [] /} map($arr)`
    *   `map([record(key: 1, value: 'one'), record(key: 2, value: 'two')])`

    Note: This syntax is most useful when used in conjunction with list
    comprehensions. For more details, see [this section](#map-from-list)

These expressions create [map](types.md#map) values. For more details about the
difference between maps and legacy object maps see the [map](types.md#map)
documentation.

### record {#record}

Record literals are delimited by `record()` and contain a comma-delimited
sequence of key-value pairs separated by `:` characters. Each key must be an
identifier. For example,

*   `record(aaa: 'blah', bbb: 123, ccc: $foo)`

Empty records are not allowed.

You can use the `...` operator to spread one or more records inside a record
literal.

*   `{let $r2: record(...$r1, key: 'value') /}`

## Variables

### Parameters and locals

Parameters and locals are introduced by:

*   [param declarations](templates.md#param)
*   [inject declarations](templates.md#inject)
*   [let declarations](let.md)
*   [for loops](control-flow.md#for)

To reference a variable, use a dollar sign `$` followed by the variable name.
For example: `$foo`

## Operators

### Precedence

Here are the supported operators, listed in decreasing order of precedence
(highest precedence at the top):

1.  `( <expr> )` `.` `?.` `[]`, `?[]`, `!` (non-null assertion)
2.  `-`(unary) `!` (not)
3.  `*` `/` `%`
4.  `+` `-`(binary)
5.  `>>` `<<`
6.  `<` `>` `<=` `>=` `instanceof`
7.  `==` `!=`
8.  `&`
9.  `^`
10. `|`
11. `&&` `and`
12. `||` `or` `??` `...` `as`
13. `? :`(ternary)

The Soy programming language respects the order of evaluation indicated
explicitly by parentheses and implicitly by operator precedence.

### Parenthesis `(...)`

Parenthesis have no affect other than to manipulate the order of evaluation.

### Data access operators `.` `?.` {#data-access}

The dot operator and question-dot operator are for accessing fields of a
`record` or a `proto`, or to make method calls.

The question-dot operator is for nullish safe access. If the value of the
preceding operand is `null` or `undefined` then the access will return
`undefined` rather than failing. This is useful for traversing optional proto
fields.

For example,

*   `$foo.bar` accesses the `bar` field of the variable `$foo`
*   `$foo?.bar` accesses the `bar` field of `$foo` only if `$foo` is non-nullish
*   `$foo?.bar?.baz()` accesses the `bar` field of `$foo` only if `$foo` is
    non-nullish, and invokes the `baz()` method of `$foo.bar` only if both
    `$foo` *and* `$foo.bar` are non-nullish

Note: The "short-circuiting" behavior of `?.` only applies to the sequence of
field accesses and method calls that immediately follow it. If the result of
this sequence is used as part of a larger expression, the larger expression
still will be invoked even if the sequence returns `undefined`.

Warning: This can result in dangerously different client- and server-side
rendering behavior. For example, if `$foo == null`, then the expression
`$foo?.bar > 0` is evaluated as `null > 0`. This evaluates to `false` in JS but
throws a NullPointerException in Java.

### Indexed access operators `[]` `?[]` {#indexing-operators}

Indexed access operators, used for accessing elements of `list` and
`legacy_object_map`.

The question-bracket operator is for nullish safe access. If the value of the
preceding operand is `null` or `undefined` then the access will return
`undefined` rather than failing.

For example,

*   `$foo[$bar]` accesses the `$bar` index of the `legacy_object_map` `$foo`
*   `$foo?[$bar]` accesses the `$bar` field of `$foo` only if `$foo` is
    non-nullish

NOTE: if the index being accessed doesn't exist, `undefined` will be returned.
There is no 'index out of bounds' error for lists.

Warning: The "short-circuiting" caveat described above (for `?.`) applies here
as well. For example, the expression `$foo?[$bar] > 0` is *not* safe.

### Casting and type testing

The `instanceof` operator performs a runtime check on the type of an expression.
This can be very useful when handling values with union, `any`, or `?` types.

```soy
{@param stringOrHtml: string|html}
{if $stringOrHtml instanceof string}
  // handle string
{else}
  // handle html
{/if}
```

The left hand side of the operator may be any of these values:

*   Primitive types: `number`, `string`, `bool`
*   Collection types: `list`, `map` (omitting generics)
*   Sanitized content types: `html`, `attributes`, `css`, `js`, `uri`,
    `trusted_resource_uri`
*   Protobuf types: `Message`, a specific protobuf message type

The `as` operator can be used to force the Soy compiler to treat an expression
as a particular type. This can be useful in places where the Soy compiler's type
narrowing cannot determine an expression's type, or if the developer knows
something that the compiler doesn't.

```soy {highlight="as html,1"}
{@param stringOrHtml: ?}
{if $stringOrHtml instanceof string}
  {$stringOrHtml.length}
{else}
  {lengthOfHtmlExtern($stringOrHtml as html)}
{/if}
```

**WARNING:** Using the `as` operator can result in runtime errors if the value
is not actually the corresponding type.

**WARNING:** The `as` operator is not type coercion. `(123 as
string).indexOf('2')` will fail at runtime.

### Non-null assertion operator `!` {#nonnull-assertion}

A post-fix operator to assert that the operand is non-null. This removes `null`
and `undefined` from the type of the operand.

NOTE: This does NOT insert a runtime check, so could allow `null` to sneak into
variables that are typed as non-nullable. Use the
[`checkNotNull`](functions.md#checkNotNull) function instead if you'd also like a
runtime check.

For example,

*   `$foo!`
*   `$foo!.bar!.baz!`

### Minus operator `-`

Unary minus. Used to mathematically negate a value.

For example,

*   `-$foo`
*   `-(3 + $bar)`

### Not operator `!`

The `!` operator, performs logical negation (i.e. `!true == false` and
`!false == true`)

For example,

*   `!$bar`
*   `!$foo.contains('x')`

### Times operator `*`

Numeric multiplication.

For example,

*   `$foo * 2`

### Division operator `/`

Numeric division.

NOTE: this always performs floating point division. For integer division
consider using the [`floor`](functions.md#floor),
[`ceiling`](functions.md#ceiling), or [`round`](functions.md#round) functions to
process the result of a division.

For example,

*   `$foo / 2`

### Modulus operator `%`

Modulus or remainder operator.

For example,

*   `$foo % 2 == 0` returns `true` if `$foo` is an even number

### Plus operator `+` {#plus}

The plus operator. This operator is overloaded to perform both numeric addition
and string concatenation.

*   When both operands are numeric, performs addition.
*   When one of the two operands is a string, the other value is coerced to a
    string and the values are concatenated.

NOTE: All primitive values have string representations. The string
representation of a map or list is not well-defined, so don't print a map or
list unless you're debugging. See [String Coercions](coercions.md#string) for
more information.

For example,

*   `$foo + $bar`
*   `'' + $foo` coerces `foo` to a string

### Subtraction operator `-`

The subtraction operator always performs numeric subtraction.

For example,

*   `$foo - 1`

### Bitwise operators `<<`, `>>`, `&`, `|`, `^`

These operators all require both operands to be integers.

*   `(16 >> 2) == 4` - shift right
*   `(4 << 2) == 16` - shift left
*   `(6 & 3) == 2` - bitwise AND
*   `(6 | 3) == 7` - bitwise OR
*   `(6 ^ 3) == 5` - bitwise XOR

### Relative comparison operators `<`, `>`, `<=`, `>=`

Relative comparison operators, used for comparing numeric values.

For example,

*   `$foo < $bar`

### Equality operators `==`, `!=`, `===`, `!==`

Equality operators. Compares two values for equality. Mirrors the Javascript
equality and strict equality operators.

When the double equals is used, if one side is a `string` and the other side is
not, then it will be coerced to a `string` for the comparison. When triple
equals is used, strict equality will be used.

For example,

*   `$foo == null`
*   `2 == $bar`

### Logical operators `&&`, `||` {#logical-operators}

Logical boolean operators. Thee are short-circuiting and follow JavaScript
semantics:

*   `&&` The first operand is returned if it is falsey, otherwise the second is
    returned;
*   `||` The first operand is returned if it is truthy, otherwise the second is
    returned;

When used with booleans, the result is boolean logic "and" and "or" operations.

For example,

*   `$foo > 2 && $foo < 5`
*   `$foo <= 2 || $foo >= 5`

NOTE: Each primitive type has exactly one falsy value: `null` is falsy, `false`
is falsy for booleans, `0` is falsy for integers, `0.0` is falsy for floats, and
`''` (empty string) is falsy for strings. All other primitive values are truthy.
Maps and lists are always truthy even if they're empty.

### Deprecated logical operators `and`, `or` {#logical-operators-deprecated}

WARNING: `and` and `or` are being deprecated in favor of `&&` and `||` in order
to converge with JavaScript.

When a non-boolean value is used, it is coerced to a boolean.

Using a constant expression for the `or` operator produces a compiler warning.
Using a boolean constant renders the `or` expression meaningless; using a
constant of another type means the expression does not evaluate to a boolean
type.

WARNING: While `''` is falsy in all backends, it does not compare to `true` and `false`
equivalently in all backends. It is therefore safe to use a string as the first
argument of a ternary statement but not safe to compare strings to booleans.

Rather than using the short-circuit property of the `or` operator, you should
use `??`, the [nullish coalescing operator](#nullish-coalescing-operator). This
more clearly expresses your intent.

For example, these expressions will produce a warning:

```soy {.bad}
{param myLabel: $myProto?.label or '' /}
{param isEnabled: $isButtonVisible or false /}
{param isEnabled: $optBoolVar or false /}
```

Simplify or use `??` for all new Soy code.

```soy {.good}
{param myLabel: $myProto?.label ?? '' /}
{param isEnabled: $isButtonVisible /}
{param isEnabled: $optBoolVar ?? false /}
```

### Nullish coalescing operator `??` {#nullish-coalescing-operator}

The nullish coalescing operator (also known as the 'elvis operator') returns the
left side if it is non-nullish, and the right side otherwise. This is often
useful for supplying default values.

This operator is short circuiting &mdash; if the left side is non-nullish the
right side will not be evaluated.

For example,

*   `$foo ?? 0`

### Spread operator `...` {#spread-operator}

The spread operator `...` allows you to expand the contents of a list into
another list literal, or the contents of a record into another record literal.

For example,

*   `[1, 2, ...$list1, 3, 4, ...$list2]`
*   `record(k1: 'v1', ...$rec1, k2: 'v2', ...$rec2)`

### Ternary operator `? :` {#ternary-operator}

Ternary conditional operator `? :` uses the boolean value of one expression to
decide which of two other expressions should be evaluated.

For example,

*   `$foo ? 1 : 2`

NOTE: The checks done by the binary operator `?:` and the ternary operator `? :`
are different. Specifically, `$a ?: $b` is not equivalent to `$a ? $a : $b`.
Rather, the former expression is equivalent to `$a != null ? $a : $b`.

## List comprehensions

List comprehensions can be used to transform and/or filter a list into a new
list.

For example:

`[$a + 1 for $a in $myList]`

If `$myList` were `[1, 2, 3, 4, 5]`, the expression above would evaluate to:

`[2, 3, 4, 5, 6]`

List comprehensions also accept an optional, zero-based **position index** and
an optional **filter expression**, such as:

`[$a + $i for $a, $i in $myList if $a >= 3]`

For the original `$myList` value above, this would evaluate to:

`[5, 7, 9]`

### Using list comprehensions to create maps {#map-from-list}

List comprehensions can be particularly useful for constructing and manipulating
maps. To illustrate, we'll construct three maps (`result1`, `result2` and
`result3`) in the code snippet below. Each map is constructed using a list
comprehension and evaluates to `map('a': 1, 'b': 2, 'c': 3)`.

```soy
{let $result1: map([record(key: $c, value: $i + 1) for $c, $i in ['a', 'b', 'c']]) /}

{let $oldMap: map('a': 10, 'b': 20, 'c': 30, 'd': 40, 'e': 50) /}
{let $result2: map([record(key : $x, value : $oldMap.get($x) / 10) for $x in $oldMap.keys() if $oldMap.get($x) < 35]) /}

// 'Person' is a proto with the following signature:
//
// message Person {
//   string name = 1;
//   int32 age = 2;
// }
{let $arr: [Person(name: 'a', age: 1), Person(name: 'b', age: 2), Person(name: 'c', age: 3)] /}
{let $result3: map([record(key: $x.name, value: $x.age) for $x in $arr]) /}
```

## Function calls

Function calls consist of an identifier with a number of positional parameters:

`<IDENT>(<EXPR>,...)`

See the [dev guide](../dev/externs.md) for how to define a custom external
function and the [functions reference](functions.md) for a list of all functions
that are available by default.

For example:

*   `max($foo, $bar)`

## Method calls

Method calls consist of an expression followed by the dot or question-dot
operator, and an identifier with a number of arguments for positional
parameters:

`<EXPR>.<IDENT>(<EXPR>,...)`

For example:

*   `$foo.bar($baz)` calls the `bar` method on `$foo` with an argument of `$baz`
*   `$foo?.bar(1, 2)` calls the `bar` method on `$foo` with the arguments `1`
    and `2` only if `$foo` is non-nullish

See the [methods reference](functions.md) for a list of all methods that are
available.

## Proto initialization

Proto construction consists of a fully qualified proto name followed by a
sequence of key value pairs where the keys correspond to fields in the proto.

`<PROTO-NAME>(<FIELD>: <VALUE>,...)`

For example:

```soy
import {Baz} from 'foo/baz.proto';

{template bar}
  {let $b: Baz(quux: 3) /}
{/template}
```
