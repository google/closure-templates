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
{template .foo}
  {@param p: ?}
  {if $p > 2}
    {call .bar}{param p : $p == 3 ? 'a' : 'b' /}{/call}
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

### map {#map}

Map literals are delimited by `map()` and contain a comma-delimited sequence of
key-value pairs separated by `:` characters. For example,

*   `map()`: the empty map
*   `map(1: 'one', 2: 'two')`

These expressions create [map](types.md#map) values. For more details about the
difference between maps and legacy object maps see the [map](types.md#map)
documentation.


### record {#record}

Record literals are delimited by `record()` and contain a comma-delimited
sequence of key-value pairs separated by `:` characters. Each key must be an
identifier. For example,

*   `record()`: the empty record
*   `record(aaa: 'blah', bbb: 123, ccc: $foo)`


## Variables

### Parameters and locals

Parameters and locals are introduced by:

*   [param declarations](templates#param)
*   [inject declarations](templates#inject)
*   [let declarations](let)
*   [for loops](control-flow#for)

To reference a variable, use a dollar sign `$` followed by the variable name.
For example: `$foo`

### globals

A global is a reference that looks like a simple dotted identifier sequence.

`foo.bar.Baz`

Globals can be configured with the compiler via the `--compileTimeGlobalsFile`
flag, proto enum values are also represented as global references.

TIP: You can use the [`{alias ...}`](file-declarations.md#alias) directive to
abbreviate globals.

It is an error in the compiler to reference a global that doesn't have a
definition at compile time, however, if you are only compiling for JavaScript
then this is allowed for backwards compatibility reasons.

## Operators

### Precedence

Here are the supported operators, listed in decreasing order of precedence
(highest precedence at the top):

1.  `( <expr> )` `.` `?.` `[]`, `?[]`
1.    `-` (unary)   `not`
1.    `*`   `/`   `%`
1.    `+`   `-` (binary)
1.    `<`   `>`   `<=`   `>=`
1.    `==`   `!=`
1.    `and`
1.    `or`
1.    `?:` (binary)   `? :` (ternary)

The Soy programming language respects the order of evaluation indicated
explicitly by parentheses and implicitly by operator precedence.

### Parenthesis `(...)`

Parenthesis have no affect other than to manipulate the order of evaluation.

### Data access operators `.` `?.` {#data-access}

The dot operator and question-dot operator are for accessing fields of a
`record` or a `proto`.

The question-dot operator is for null safe access. If the value of the preceding
operand is `null` then the access will return `null` rather than failing. This
is useful for traversing optional proto fields.

For example,

*   `$foo.bar` accesses the `bar` field of the variable `$foo`
*   `$foo?.bar` accesses the `bar` field of `$foo` only if `$foo` is non-`null`

### Indexed access operators `[]` `?[]` {#indexing-operators}

Indexed access operators, used for accessing elements of `maps` and `lists`.

The question-bracket operator is for null safe access. If the value of the
preceding operand is `null` then the access will return `null` rather than
failing.

For example,

*   `$foo[$bar]` accesses the `$bar` index of the map `$foo`
*   `$foo?[$bar]` accesses the `$bar` field of `$foo` only if `$foo` is
    non-`null`

NOTE: if the index being accessed doesn't exist, `null` will be returned. There
is no 'index out of bounds' error for lists.

### Minus operator `-`

Unary minus. Used to mathematically negate a value.

For example,

*   `-$foo`
*   `-(3 + $bar)`

### Not operator `not`

The `not` operator, performs logical negation (i.e. `not true == false` and `not
false == true`)

For example,

*   `not $bar`
*   `not isLast($foo)`

### Times operator `*`

Numeric multiplication.

For example,

*   `$foo * 2`

### Division operator `/`

Numeric division.

NOTE: this always performs floating point division. For integer division
consider using the [`floor`](functions#floor),
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

### Relative comparison operators `<`, `>`, `<=`, `>=`

Relative comparison operators, used for comparing numeric values.

For example,

*   `$foo < $bar`

### Equality operators `==`, `!=`

Equality operators. Compares two values for equality. If one side is a `string`
and the other side is not, then it will be coerced to a `string` for the
comparison.

For example,

*   `$foo == null`
*   `2 == $bar`

### Logical operators `and`, `or` {#logical-operators}

Logical boolean operators.

The boolean operators are short-circuiting. When a non-boolean value is used in
a boolean context, it is coerced to a boolean.

NOTE: Each primitive type has exactly one falsy value: `null` is falsy, `false`
is falsy for booleans, `0` is falsy for integers, `0.0` is falsy for floats, and
`''` (empty string) is falsy for strings. All other primitive values are truthy.
Maps and lists are always truthy even if they're empty.

For example,

*   `$foo > 2 and $foo < 5`
*   `$foo <= 2 or $foo >= 5`

Using a constant expression for the `or` operator produces a compiler warning.
Using a boolean constant renders the `or` expression meaningless; using a
constant of another type means the expression does not evaluate to a boolean
type. Further, the falsy rules are different in Java and JavaScript (`''` in
Java is truthy; but in JavaScript it is falsy). Using non-boolean constants
might produce slightly different results when rendering in Java versus
JavaScript.

Rather than using the short-circuit property of the `or` operator, you should
use `?:`, the [null coalescing operator](#null-coalescing-operator). This more
clearly expresses your intent.

For example, these expressions will produce a warning:

```soy {.bad}
{param myLabel: $myProto?.label or '' /}
{param isEnabled: $isButtonVisible or false /}
{param isEnabled: $optBoolVar or false /}
```

Simplify or use `?:` for all new Soy code.

```soy {.good}
{param myLabel: $myProto?.label ?: '' /}
{param isEnabled: $isButtonVisible /}
{param isEnabled: $optBoolVar ?: false /}
```

### Null coalescing operator `?:` {#null-coalescing-operator}

The null coalescing operator (also known as the 'elvis operator'). Returns the
left side if it is non-`null`, and the right side otherwise. This is often
useful for supplying default values.

This operator is short circuiting, if the left side is non-`null` the right side
will not be evaluated.

For example,

*   `$foo ?: 0`

### Ternary operator `? :` {#ternary-operator}

Ternary conditional operator ? : uses the boolean value of one expression to
decide which of two other expressions should be evaluated.

For example,

*   `$foo ? 1 : 2`

NOTE: The checks done by the binary operator `?:` and the ternary operator `? :`
are different. Specifically, `$a ?: $b` is not equivalent to `$a ? $a : $b`.
Rather, the former expression is equivalent to `isNonnull($a) ? $a : $b`.

## Function calls

Function calls consist of an identifier with a number of positional parameters:

`<IDENT>(<EXPR>,...)`

See the [dev guide](../dev/plugins.md) for how to register a custom function and
the [functions reference](functions.md) for a list of all functions that are
available by default.

For example:

*   `isNonnull($foo)`
*   `max($foo, $bar)`

## Proto initialization

Proto construction consists of a fully qualified proto name followed by a
sequence of key value pairs where the keys correspond to fields in the proto.

`<PROTO-NAME>(<FIELD>: <VALUE>,...)`

For example:

*   `foo.bar.Baz(quux: 3)`

TIP: You can use the [`{alias ...}`](file-declarations.md#alias) directive to
abbreviate proto names used in initialization expressions.
