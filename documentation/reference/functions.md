<!-- disableFinding(LINK_RELATIVE_G3DOC) -->

# Methods, Fields, and Functions

Soy methods, fields, and functions are called from within Soy
[expressions](expressions.md).

The table below lists basic Soy methods, fields, and functions that are
available by default. For Soy functions related to bidirectional text, see
[Bidi Support](../dev/localization#bidi_functions). For information on writing
custom external functions, see
[Creating an External Function](../dev/externs.md).

[TOC]

## Basic Functions

### `Boolean(value)` {#Boolean}

Explicitly
[coerces](http://go/soy/reference/coercions?polyglot=call-command#boolean-coercions)
the argument to a boolean.

Warning: `Boolean()` does not work correctly for gbigint 0 values since it may
be represented as the string `"0"` in goog.DEBUG or if `bigint` is not
available. Please use `gbigintToBoolean()` instead.

### `hasContent(value)` {#hasContent}

Value must be one of the sanitized content types (`html`, `attributes`, `uri`,
`trusted_resource_uri`, `css`, `js`) or `string`. Returns `true` if it is
non-null and has content which is not empty string.

### `isTruthyNonEmpty(value)` {#isTruthyNonEmpty}

For strings and sanitized types, equivalent to `hasContent(value)`. Any other
type is allowed, in which case standard
[boolean coercion](http://go/soy/reference/coercions?polyglot=call-command#boolean-coercions)
is used.

### `checkNotNull(value)` {#checkNotNull}

Throws a runtime exception if the given value is `null` or `undefined` and
returns the value otherwise.

This function is integrated into the type system, so if `value` is typed as a
nullable value, the return value of `checkNotNull` will no longer be nullable.
This can be useful when passing values to templates that expect non nullable
values.

### `Number_isNaN(value)` {#Number_isNaN}

Returns `true` if `value` is a number and its number value is `NaN`.

### `Number_isInteger(value)` {#Number_isInteger}

Returns `true` if `value` is a number that can be represented as a whole
integer.

### `keys(legacyObjectMap)` {#keys}

The keys of a [legacy object map](types.md#legacy_object_map) as a list. There
is no guarantee on order.

### `mapToLegacyObjectMap(map)` {#mapToLegacyObjectMap}

Converts a [map](types.md#map) to an equivalent
[legacy_object_map](types.md#legacy_object_map).

Because legacy object maps do not support non-string keys, all of the keys are
coerced to strings in the returned legacy object map.

### `legacyObjectMapToMap(legacyObjectMap)` {#legacyObjectMapToMap}

Converts a [legacy object map](types.md#legacy_object_map) to an equivalent
[map](types.md#map).

### `range([start,] end[, step])` {#range}

Use this to create lists containing arithmetic progressions. It is most often
used in [indexed for loops](control-flow.md#for-indexed). If the `step` argument
is omitted, it defaults to 1. If the `start` argument is omitted, it defaults to
0. The full form returns a list of plain integers `[start, start + step, start +
2 * step, ...]`.

This function behaves identically to the Python `range` builtin function, or the
Closure `goog.array.range` function.

## Math Functions

### `round(number[, numDigitsAfterDecimalPoint])` {#round}

Rounds the given number to an integer.

If `numDigitsAfterDecimalPoint` is positive, round to that number of decimal
places; if `numDigitsAfterDecimalPoint` is negative, round to an integer with
that many 0s at the end.

BEST PRACTICE: Don't use this function to format numbers for display. Prefer
things like [`formatNum`](#formatNum) which are i18n friendly.

### `abs(number)` {#Math_abs}

The absolute value of the number.

### `floor(number)` {#floor}

The floor of the number.

### `ceiling(number)` {#ceiling}

The ceiling of the number.

### `cos(number)` {#Math_cos}

The cosine of a number in radians.

### `log(number)` {#Math_log}

The log of the number with base `e`.

### `min(number, number)` {#min}

The min of the two numbers.

### `max(number, number)` {#max}

The max of the two numbers.

### `parseInt(str[, base])` {#parseInt}

Parses the string argument as a signed base 10 integer of the specified base.
Returns `null` if the string cannot be parsed. Base defaults to 10 and must be
between 2 and 36, if specified.

### `parseFloat(str)` {#parseFloat}

Parses the string argument as a floating point number. Returns `null` if the
string cannot be parsed.

### `Number_parseInt(str[, radix])` {#Number_parseInt}

Like `parseInt()` but matching the ECMAScript spec for `Number.parseInt()`.

### `Number_parseFloat(str)` {#Number_parseFloat}

Like `parseFloat()` but matching the ECMAScript spec for `Number.parseFloat()`.

### `randomInt(rangeArg)` {#randomInt}

A random integer in the range `[0, rangeArg - 1]` (where `rangeArg` must be a
positive integer).

### `sign(number)` {#Math_sign}

Returns the sign of the number as 1 or -1. 0 returns 0. -0 returns -0.

### `sin(number)` {#Math_sin}

The sine of a number in radians.

### `sqrt(number)` {#sqrt}

Returns the square root of the number.

### `tan(number)` {#Math_tan}

The tangent of a number in radians.

### `pow(number, number)` {#pow}

Returns the value of the first argument raised to the power of the second
argument.

### `isFinite(number)` {#isFinite}

Returns whether the number is finite (i.e., not `NaN` or `Infinity`).

## Math Constants

### `Math_PI`

Ratio of a circle's circumference to its diameter; approximately 3.14159.

### `Math_E`

Euler's number and the base of natural logarithms; approximately 2.718.

### `Number_NaN`

Floating point "not-a-number".

### `Number_POSITIVE_INFINITY`

Floating point positive infinity.

### `Number_NEGATIVE_INFINITY`

Floating point negative infinity.

## List Fields

<span id="list-any_length"></span>

### `list.length` {#length}

Returns the length of a list.

Also callable as deprecated global function `length(list)`.

## List Methods

<span id="list-any_concat"></span>

### `list.concat(list)` {#concatLists}

Joins two or more lists together.

<span id="list-any_includes"></span>

### `list.includes(value)` {#listIncludes}

Checks if the given value is inside the list. This method uses the equivalent of
JS/TS script equality.

<span id="list-any_flat"></span>

### `list.flat([depth])` {#listFlat}

Flattens a nested list. The behavior matches JavaScript's `Array.prototype.flat`
method.

<span id="list-any_indexOf"></span>

### `list.indexOf(value[, startIndex])` {#listIndexOf}

Return the first index of the value in list, or -1. Given a value for
startIndex, it returns the first index greater than or equal to startIndex. This
method implements JavaScript semantics, comparing elements with `==`. Therefore
it only works on lists of primitive values.

### `list.slice([from, to])` {#list-any_slice}

Returns a sublist of a list from index `from` inclusive to index `to` exclusive.
If `from` is not specified, it returns a list with the same elements in it.
Negative indices are supported and match the
[JavaScript spec](https://developer.mozilla.org/en/docs/Web/JavaScript/Reference/Global_Objects/Array/slice).

### `list.reverse()` {#list-any_reverse}

Reverses a shallow copy of the list and returns it. The original list passed is
not modified.

### `list.uniq()` {#list-any_uniq}

Removes duplicates from a shallow copy of the list and returns it. The original
list passed is not modified.

NOTE: We do not test for deep equality in the implementation. Hence, this
function does not remove duplicate records or protobufs. Only primitive types
(null, undefined, bool, int, float, number, string) are successfully
deduplicated.

<span id="list-string|float|int|gbigint_join"></span>

### `list.join(separator)` {#join}

Joins a list of strings or numbers with a string separator.

Also callable as deprecated global function: `join(list, separator)`

<span id="list-float|int_sort"></span>
<span id="list-gbigint_sort"></span>

### `list<any>.toSorted(comparator)` {#list-any_toSorted}

Returns a copy of the list sorted by the supplied comparator.

### `list<number>.sort()` and `list<gbigint>.sort()` {#sort}

Sorts the list in numerical order.

This method is only defined on lists of non-nullable number types. If the method
is not found, please check the type of your list.

### `list<string>.asciiSort()` {#list-string_asciiSort}

Sorts the list in alphabetical order according to the ASCII specification. Do
not use for user visible strings, instead use `localeSort()`.

This method is only defined on lists of non-nullable strings. If the method is
not found, please check the type of your list.

WARNING: The sort is based on the Unicode values of the string. This order may
not correspond to how users of your app would sort strings, and therefore you
should not use this method to sort any user-visible string. Only use this method
for non-user visible strings, e.g. to normalize the order of a list of
identifiers. See go/unicode-codelab-cc#sorting-in-alphabetical-order for more
information on i18n-safe string sorting.

### `list<string>.localeSort([options])` {#list-string_localeSort}

Sorts the list in alphabetical order according to the user's locale.

The method allows an optional parameter of type record that affects sort order.
The record may contain any of the following properties:

*   caseFirst: `"upper"`, `"lower"`
*   numeric: `true`, `false`
*   sensitivity: `"base"`, `"accent"`, `"case"`, `"variant"`

See
[Intl.Collator](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Intl/Collator/Collator)
for a description of these options.

This method is only defined on lists of non-nullable strings. If the method is
not found, please check the type of your list.

## Mutable List Methods {#mutable_list}

These methods are only available inside the
[`autoimpl` command](../dev/externs.md#autoimpl).

### `Array.fill(item, start, end)` {#mutable_list-any_fill}

### `Array.push(item)` {#mutable_list-any_push}

### `Array.pop()` {#mutable_list-any_pop}

### `list.mutableReverse()` {#mutable_list-any_mutableReverse}

### `Array.unshift(item)` {#mutable_list-any_unshift}

### `Array.shift()` {#mutable_list-any_shift}

### `Array.splice(index, length, item)` {#mutable_list-any_splice}

## Set Fields

### `set.size` {#set-any_size}

Returns the number of items in the set.

## Set Methods

### `Set(items)` {#Set}

The set constructor. Accepts a single iterable parameter.

### `set.has(item)` {#set-any_has}

Returns whether an item exists in a set.

## Map Fields

### `map.size` {#map-any,any_size}

Returns the number of keys in the map.

## Map Methods

<span id="map-any,any_keys"></span>

### `map.has(key)` {#map-any,any_has}

Returns a boolean whether the key is contained in the map.

### `map.get(key)` {#map-any,any_get}

Returns a single value from a map; equivalent to bracket access.

### `map.keys()` {#mapKeys}

The keys of a [map](types.md#map) as a list. The ordering is stable and will
match the map constructor.

### `map.values()` {#map-any,any_values}

The values of a [map](types.md#map) as a list. The ordering is stable and will
match the map constructor.

### `map.entries()` {#map-any,any_entries}

The entries of a [map](types.md#map) as a list of records with fields `key` and
`value`. The ordering is stable and will match the map constructor.

### `map.concat(map)` {#map-any,any_concat}

Combines two [map](types.md#map)s into one. If there's a key collision the value
from the method parameter wins.

## Mutable Map Methods (Available inside the {autoimpl} command)

### `map.delete(key)` {#mutable_map-any,any_delete}

Removes the value for a given key, in place. Returns `true` if an entry was
removed, `false` if the key was not found in the map.

### `map.set(key, value)` {#mutable_map-any,any_set}

Adds the new key-value pair to the map, in place. Replaces the value for an
existing key, if present. Returns the Map object, for chaining.

## Number Methods

### `number.toFixed` {#number_toFixed}

## String Fields

<span id="string_length"></span>

### `str.length` {#strLen}

Returns the length of a string in characters.

WARNING: The length is based on characters (aka java `char` or utf-16
characters), not Unicode codepoints or more useful concepts like graphemes. It
is almost never valid to use this to break text meant for users into parts since
it will be very easy to break the string (e.g. split an emoji in half).

## String Methods

NOTE: it is generally tricky to manipulate user authored text with these
functions, these are intended for manipulating textual 'data', not something
that should be displayed to a human. These functions are not generally unicode
aware and may do bad things if used naively. For example, consider calling
`.substring()` on text containing emoji, without being extremely careful you are
likely to break the emoji and subvert user intention.

<span id="string_includes"></span>

### `str.includes(subStr)` {#strIncludes}

Checks whether a string contains a particular substring.

### `str.endsWith(subStr[, length])` {#string_endsWith}

Checks whether a string ends with a particular substring. If length is provided,
it is used as the length of str. Defaults to str.length.

<span id="string_indexOf"></span>

### `str.indexOf(subStr[, position])` {#strIndexOf}

Returns the character index of the first occurrence of `substr` within `str`, or
`-1`. Case-sensitive, 0-based index. Given a value for position, it returns the
first index greater than or equal to position.

WARNING: The index is based on characters (aka java `char` or utf-16
characters), not Unicode codepoints or more useful concepts like graphemes. It
is almost never valid to use this to break text meant for users into parts since
it will be very easy to break the string (e.g. split an emoji in half).

### `str.replaceAll(t, s)` {#string_replaceAll}

Returns a copy of `str` with all occurrences of string `t` replaced with `s`.

### `str.split(sep[, limit])` {#string_split}

Returns a list of tokens generated by splitting a string on a separator. If
`sep` is the empty string then returns a list with each character of the string.
If limit is specified, the list is truncated to the limit size.

### `str.startsWith(subStr[, start])` {#string_startsWith}

Checks whether a string starts with a particular substring. If start is
provided, it is used as the beginning index of the str. Defaults to 0.

<span id="string_substring"></span>

### `str.substring(start[, end])` {#strSub}

Returns the substring of `str` beginning at index `start`. If `end` is provided
returns the substring of `str` beginning at index `start`, and ending at `end -
1`.

WARNING: The index is based on characters (aka java `char` or utf-16
characters), not Unicode codepoints or more useful concepts like graphemes. It
is almost never valid to use this to break text meant for users into parts since
it will be very easy to break the string (e.g. split an emoji in half).

<span id="string_toAsciiLowerCase"></span>

### `str.toAsciiLowerCase()` {#strToAsciiLowerCase}

Returns the lowercase representation of the given string.

NOTE: This function doesn't consider locales when transforming the string and it
only transforms ASCII characters `A-Z`. Do not use it to lowercase string that
are localized and/or UNICODE.

<span id="string_toAsciiUpperCase"></span>

### `str.toAsciiUpperCase()` {#strToAsciiUpperCase}

Returns the uppercase representation of the given string.

NOTE: This function doesn't consider locales when transforming the string and it
only transforms ASCII characters `a-z`. Do not use it to uppercase string that
are localized and/or UNICODE.

### `strSmsUriToUri(string)` {#strSmsUriToUri}

Returns a sanitized uri given an SMS uri string.

The RFC for sms: https://tools.ietf.org/html/rfc5724

### `str.trim()` {#string_trim}

Returns a copy of a string with leading and trailing whitespace removed.

### `buildAttrValue(...values)` {#buildAttrValue}

Takes multiple arguments, all falsey values are first filtered out. Remaining
values are then joined into a single string with the `;` character.

### `buildClassValue(...values)` {#buildClassValue}

Like `buildAttrValue()`, but joins with the space character instead.

### `buildStyleValue(...values)` {#buildStyleValue}

Like `buildAttrValue()`, but with return type `css`. Note that when using raw
strings, each string must contain a single CSS declaration to avoid a runtime
error:

```soy {.good}
buildStyleValue('color:red', 'padding:10px', $moreStyles)
```

```soy {.bad}
buildStyleValue('color:red; padding:10px', $moreStyles)
```

### `buildAttr(attrName, ...values)` {#buildAttr}

Constructs an attribute by first joining the values and emitting an attributes
object with the specified attribute name. If the value is empty the attribute is
omitted entirely.

## Proto methods

### `proto.isDefault()` {#Message_isDefault}

Returns whether a protobuf message is equal to the default instance of its type.

### `proto.equals(p)` {#Message_equals}

Returns whether two protobuf messages are equal.

### `proto.getExtension(name)`

Returns the value of the extension field of the `proto`, given the name of an
imported extension field as the parameter.

#### Example usage

```proto
package soy.example;
message Person {
  extensions 1000 to max;
}
message Height {
  extend Person {
    optional Height height = 1001;
    repeated Height past_height = 1002;
  }
  optional int32 cm = 1;
}
```

The template below accesses the `Height` extension of the `Person` proto.

```soy
{template foo}
  {@param proto: soy.example.Person}
  {$proto.getExtension(soy.example.Height.height).cm}
{/template}
```

#### Repeated extension fields

Access repeated extension fields by appending `List` to the fully qualified name
of the extension.

For example, given the [above definition](#example-usage) of the repeated proto
field `past_height`, it would be accessed as follows:

```soy
{template heightHistory}
  {@param person: soy.example.Person}
  {for $height in $person.getExtension(soy.example.pastHeightList)}
    {$height.cm}
  {/for}
{/template}
```

## Other Functions

### `legacyDynamicTag($tagName)` {#legacyDynamicTag}

The `legacyDynamicTag` function is used to create an HTML tag whose name is
determined dynamically by a print node. Wrapping the tag name expression in
`legacyDynamicTag` is required in order to disambiguate it with other Soy
syntax.

```soy
{template foo}
  {@param tagName: string}
  <{legacyDynamicTag($tagName)}>Hello!</{legacyDynamicTag($tagName)}>
{/template}
```

### `unknownJsGlobal(stringLiteral)` {#unknownJsGlobal}

The `unknownJsGlobal` function allows code compiled to the `jssrc` backend to
access JavaScript global values.

This function can only be used by the JavaScript backend, as such files that use
it are incompatible with the other backends. When used the function must take a
[string literal](expressions.md#string-literal) that contains some JS identifier
reference.

```soy
{unknownJsGlobal('some.javascript.Identifier')}
```

This will compile to something like:

```js
/** @suppress {missingRequire} */ (some.javascript.Identifier)
```

This pattern is provided for backwards compatibility with old versions of the
Soy compiler that didn't require globals definitions to be provided. Users
should consider replacing the use of this function with one of the following:

*   a custom soy plugin to represent the needed functionality
*   a globals definition for the referenced global
*   representing the value as a proto enum

NOTE: b/162340156 is in progress enforcing that this is used.

### `css([baseClass,] selector)` {#css}

Acts as a layer of indirection to avoid hard-coding CSS class names in template
files. In JavaScript, this becomes a call to the Closure Library function
`goog.getCssName()`. In Java, this becomes a lookup into a user-provided
`SoyCssRenamingMap`. If the optional `baseClass` arg is present, both args will
be passed to `goog.getCssName()` in JavaScript, and the two args will be
evaluated and concatenated with `-` in Java. The `selector` arg must be a string
literal.

The `css` function supports a simple autoprefixing syntax to make working with
long namespaces easier. For example,

```soy
{template foo cssbase="foo.bar.baz"}
<div class={css('%Menu')}>
...
</div>
{/template}
```

When the css selector is prefixed with a percent symbol, Soy will use the
following rules to decide what to do:

1.  If there is a `cssprefix` attribute on the `namespace`, use it for the
    prefix
1.  If there is a `cssbase` attribute on the `template`, use it for the prefix
1.  If there is a `cssbase` attribute on the `namespace`, use it for the prefix
1.  Otherwise use the first `requirecss` namespace defined on the `namespace`
    for the prefix

If there is no prefix, a compiler error is issued. Otherwise, the prefix is
converted from a dotted identifier to a lower camel cased identifier, and then
prefixed onto the selector. So the above example will act as if the user had
written `{css('fooBarBazMenu')}`.

The `css()` function returns a string. If you need to pass it as a parameter,
prefer the simple param syntax, e.g. `{param class: css('menu') /}`. In more
complex expressions, pass it as text:

```soy
{param class kind="text"}{if $disabled}{css('disabled')}{/if}{/param}
```

Avoid passing it as other content kinds.

### `xid(str), xid(id)` {#xid}

Returns the minified and obfuscated version of a string. The argument can be
either a `string` literal or a dotted identifier. Arguments that are dotted
identifiers are subject to expansion by [`alias`](file-declarations.md#alias)
commands. Otherwise the parameter types are interchangeable.

For example, assuming that the file has `{alias foo.bar as FooBar}`, then
`xid('foo.bar.baz')`, `xid(foo.bar.baz)`, and `xid(FooBar.baz)` will all
evaluate to the same value but `xid('FooBar.baz')` will be distinct.

The implementation of this function is configurable and depends on the backend.
In JavaScript, this turns into a call to the `xid` function which uses the
`@idgenerator` mechanism of the Closure Compiler to perform compile time
obfuscation. In Java, this uses a `SoyIdRenamingMap` which can be configured
directly with the renderering APIs (e.g. `SoySauce.Renderer.setXidRenamingMap`
or `SoyTofu.Renderer.setIdRenamingMap`).

The `xid()` function returns a string. If you need to pass it as a parameter,
prefer the simple param syntax, e.g. `{param jsname: xid('input') /}`. In more
complex expressions, pass it as text:

```soy
{param jsaction kind="text"}
  {if $veid}{xid('render')}:{xid('initialize')}{/if}
{/param}
```

Avoid passing it as other content kinds.

### `htmlToText(html)` {#htmlToText}

Converts HTML to plain text by removing tags, normalizing whitespace and
converting entities.

Consecutive block level tags
(`address|blockquote|dd|div|dl|dt|h[1-6]|hr|li|ol|p|pre|table|tr|ul`) are
replaced by newline, tags with a special meaning are removed including their
content (`script|style|textarea|title`), every `br` is replaced by a newline,
table cells (`td|th`) are replaced by a tab, other tags are removed. Note that
all attribute values are removed so `<a href="https://example.com">click
here</a>` is converted to `click here` which is pretty useless.

Whitespace is normalized to a single space except at the beginning of lines
where it's removed. It is preserved inside `pre`.

Entities are converted to characters. This is host language specific so the
behavior might slightly differ for rare entities e.g. between Java and
JavaScript but all common HTML entities are supported.

This function expects `kind="html"` values. If passed a string, it returns it
unmodified.

### `ve_def(name, id[, dataProtoType[, staticMetadata]])` {#ve_def}

Declares a visual element. Returns a VE object that can be passed to `ve_data()`
and `{velog}`. This can only be used within a `{const}` definition.

`dataProtoType` should be a reference to an imported proto type, or `null`.

`staticMetadata` should be a `soy.LoggableElementMetadata` proto instance.

```soy
{export const VeWithMetadata = ve_def(
    'VeWithMetadata',
    238,
    DataProto,
    LoggableElementMetadata(clickTrackType: ClickTrackType.CLICK_TRACK_REDIRECTED)) /}
```

### `ve_data(ve, data)` {#ve_data}

See the documentation for the [ve_data literal](expressions.md#ve_data).

### `template.bind(parameterRecord)` {#bind}

Binds one or more parameters to the given template-type expression. The
parameter record must be a record literal, and each member must match the name
and type of an unbound parameter in the template-type expression. Parameters
already bound to the template type may not be bound again.

<section class="polyglot">

###### Call Command {.pg-tab}

```soy
{template foo}
  {@param fn: template (a: string) => html}
{/template}

{template bar}
  {@param a: string}
  {@param i2: string}
{/template}

{template example}
  {call foo}
    {param fn: bar.bind(record(i2: 'Hello')) /}
  {/call}
{/template}
```

###### Element Composition {.pg-tab}

```soy
{template foo kind="html<div>"}
  {@param fn: template (a: string) => html}
  <div></div>
{/template}

{template bar}
  {@param a: string}
  {@param i2: string}
{/template}

{template example}
  <{foo(fn: bar.bind(record(i2: 'Hello')))} />
{/template}
```

</section>

### `ve.hasSameId(anotherVe)` {#ve-any_hasSameId}

### `uniqueAttribute(attributeName[, idHolder])` and `idHolder()` {#uniqueAttribute}

This function generates an attribute with a stable, unique value:

```soy
<div {uniqueAttribute('data-my-id')}>
```

The attribute's value will be preserved across idom rerenders, including the
initial hydration from a server-side render.

To access the attribute's value (eg, to refer to a unique ID in attributes like
`aria-labelledby`), use `idHolder()` to create an object that captures the
value:

```soy
{let $idHolder: idHolder() /}
<button {uniqueAttribute('aria-labelledby', $idHolder)}><img></button>
<label id="{$idHolder.id}">Label!</label>

// Or

<div {uniqueAttribute('id', $idHolder)}></div>
{call textField}
  {param ariaDescribedBy: $idHolder.id /}
  // Other parameters...
{/call}
```

> Warning: When rendering with idom, you can only read `$idHolder.id` after
> *rendering* the element with the corresponding `uniqueAttribute()` call (since
> it needs to read the attribute's value from the existing DOM element). In
> debug builds, reading the `$idHolder.id` before the `uniqueAttribute()` call
> is rendered will throw an error. To fix this error, swap the callsites so that
> `uniqueAttribute()` is called on the element that is printed first.

> Tip: If this restriction is impractical (eg, if the ID is always passed as a
> parameter to other templates), you can call `uniqueAttribute()` in an unused
> attribute in an earlier function instead, then pass the ID from `idHolder()`
> everywhere you need it.
>
> For example:
>
> ```soy
> {let $idHolder: idHolder() /}
> <div {uniqueAttribute('data-irrelevant-id', $idHolder)}>
>   {call someTemplate}
>     {param id: $idHolder.id /}
>   {/call}
>   {call otherTemplate}
>     {param referencedId: $idHolder.id /}
>   {/call}
> </div>
> ```

> Warning: If you call this function in a `{let}` variable, and print the
> variable multiple times, it will print unique values when rendering an idom
> template (because `{let}`s in idom compile to functions), but will print
> identical values when rendering classic Soy (including from Java). Do not
> write code like this:
>
> ```soy
> {let $myAttributes kind="attributes"}
>   {uniqueAttribute('data-foo-bar')}
> {/let}
> // Or
> {let $myAttributes: uniqueAttribute('data-foo-bar') /}
>
> <a {$myAttributes}>Hi!</a>
> <b {$myAttributes}>Hi!</b>
> ```
>
> Similarly, do not call `uniqueAttribute()` inside a `{param}` block if the
> callee template prints the parameter in multiple places (which should be
> rare).

### <span id="undefinedToNullForSsrMigration"></span>`undefinedToNullForMigration(expr)` and `undefinedToNullForSsrMigration(expr)` {#undefinedToNullForMigration}

Converts `undefined` to `null` to aid in the introduction of `undefined` to the
Soy language. The `undefinedToNullForSsrMigration` variant affects server side
rendering behavior only, for cases where introducing `undefined` is only
changing server side rendering code.

Both variants change the type of the expression, replacing `undefined` with
`null` if the expression type is a union type containing `undefined`. Note that
in the case of `undefinedToNullForSsrMigration` and client side rendering this
could result in a value of `undefined` for an expression of type `T|null`.

### `throw(string)` {#throw}

Throws an exception. It takes a single argument which is the exception message.

## gbigint Functions

64-bit int proto fields use [gbigint](http://go/gbigint) typed values that must
be explicitly converted to `number` types in order to perform operations like
numeric comparisons, math, etc.

### `toGbigint(number|string)` {#toGbigint}

Converts the value to [gbigint](http://go/gbigint). `gbigint` does not support
any mathematical operations. Additionally, its type cannot be checked with
`instanceof` or `typeof`.

When in `goog.DEBUG` and native `bigint` is available in the JS platform,
`gbigint` will be randomly represented by either `bigint` or `string`.
Therefore, it is important that values passed to APIs expecting `gbigint` first
go through `toGbigint` to have their representation synchronized with the
randomness.

When `bigint` is not available in the JS platform, `string` will always be used.

Note: `toGbigint` can only be applied to `strings` representing decimal integers
and `number` values within the JS safe integer range. `toGbigint` will throw on
all other values.

### `isGbigint(?)` {#isGbigint}

Returns true if the provided value is recognized as a `gbigint`.

When in `goog.DEBUG` and native `bigint` is available in the platform, either
odd or even `gbigint` values will be randomly selected to be represented by
`string`. Numerical `string` values that are coincidentally in the value range
for `gbigint` to be backed by `string` representations will erroneously be
considered `gbigints` by `isGbigint`. Therefore, it is important to normalize
all values intended to be treated as `gbigints` through `toGbigint`.

In other words:

```ts
// Assume in goog.DEBUG and native bigint is available. Suppose ODD numbers have
// been randomly selected by the gbigint platform to be represented by string.

const a = toGbigint('1');
const b = '1';

assert(a, isGbigint); // true
assert(b, isGbigint); // true

const c = toGbigint('2');
const d = '2';

assert(c, isGbigint); // true
assert(d, isGbigint); // false
```

### `gbigintToBoolean(gbigint)` {#gbigintToBoolean}

This mirrors the
[TS/JS platform function](http:go/bigint#converting-gbigint-values-to-boolean)
of the same name. Safely coerces a `gbigint` to a `boolean` when the underlying
browser may not natively support `bigint`.

### `gbigintToInt(gbigint)` {#gbigintToInt}

Converts the provided `gbigint` into a `int` typed value. Asserts that the input
is actually a `gbigint` and that its value is within the JavaScript safe integer
range.

### `gbigintToIntOrNull(gbigint|null|undefined)` {#gbigintToIntOrNull}

Converts the provided `gbigint` into a `int` typed value. If a nullish value is
provided, this function is guaranteed to return `null`.

Asserts the value is within the JavaScript safe integer range.

### `gbigintToIntArray(list<gbigint>)` {#gbigintToIntArray}

Returns an array created by calling `gbigintToInt` on each element of the input.

### `gbigintToStringOrNull(gbigint|null|undefined)` {#gbigintToStringOrNull}

Converts the provided `gbigint` into a `string` typed value. If a nullish value
is provided, this function is guaranteed to return `null`.

### `isSafeInt52(gbigint)` {#isSafeInt52}

This mirrors the
[TS/JS platform function](go/bigint#converting-gbigint-values-to-number) of the
same name. Returns true if the `gbigint` value is within the safe JavaScript
integer range and can be coerced to `number` type without loss of precision.

## Localization (l10n) Functions

### `remainder(length)` {#remainder}

The `remainder` function is used in the context of plural messages. See the
[reference on plurals](messages.md#offset-and-remainder) for more information.

### `formatNum(value, opt_formatType, opt_numbersKeyword, opt_minDigits, opt_maxDigits)` {#formatNum}

Formats a number using the current locale.

It takes 1 required and 4 optional arguments.

1.  The number to format.
1.  A lower-case string describing the type of format to apply, which can be one
    of 'decimal', 'currency', 'percent', 'scientific', 'compact_short', or
    'compact_long'. If this argument is not provided, the default 'decimal' will
    be used.
1.  The "numbers" keyword passed to the ICU4J's locale. For instance, it can be
    "native" so that we show native characters in languages like Arabic (this
    argument is ignored for templates running in JavaScript).

    NOTE: see https://unicode-org.github.io/icu/userguide/locale/ for more
    "numbers" keywords

1.  The minimum number of fractional digits to display after the decimal point.
    If argument 5 (maximum fractional digits) is not specificied, then maximum
    fractional digits will be set to the same value as minimum fractional
    digits. When minimum fractional digits is set to 0, any trailing zeros will
    be removed.

1.  The maximum number of fractional digits to display.

    NOTE: min and max fractional digits are not supported in the python backend.

For example:

*   `{formatNum($value)}`
*   `{formatNum($value, 'decimal')}`
*   `{formatNum($value, 'decimal', 'native')}`
*   `{formatNum($value, 'decimal', 'native', 2)}`
    *   Prints exactly two digits after the decimal point.
*   `{formatNum($value, 'decimal', 'native', 0, 3)}`
    *   Prints up to 3 digits after the decimal point, removing any trailing
        zeros. `4.1234` would be formatted as "4.123", but `4.100` would be
        formatted as "4.1".
*   `{formatNum($value, 'percent', 'native', 2)}`
    *   Prints the number as a percent. For example, `0.531` is formatted as
        "53.10%".

### `bidiDirAttr(text, opt_isHtml)` {#bidiDirAttr}

If the overall directionality of text is different from the global
directionality, then this function generates the attribute `dir=ltr` or
`dir=rtl`, which you can include in the HTML tag surrounding that piece of text.
If the overall directionality of text is the same as the global directionality,
this function returns the empty string. Set the optional second parameter to
`true` if text contains or can contain HTML tags or HTML escape sequences
(default `false`).

### `bidiEndEdge()` {#bidiEndEdge}

Generates the string `'right'` or the string `'left'`, if the global
directionality is LTR or RTL, respectively.

### `bidiGlobalDir()` {#bidiGlobalDir}

Provides a way to check the current global directionality. Returns `1` for `LTR`
or `-1` for `RTL`. The global directionality is inferred from the current
locale.

### `bidiMarkAfter(text, opt_isHtml)` {#bidiMarkAfter}

If the exit (not overall) directionality of text is different from the global
directionality, then this function generates either the `LRM` or `RLM` character
that corresponds to the global directionality. If the exit directionality of
text is the same as the global directionality, this function returns the empty
string. Set the optional second parameter to `true` if text contains or can
contain HTML tags or HTML escape sequences (default `false`). You should use
this function for an inline section of text that might be opposite
directionality from the global directionality. Also, set text to the text that
precedes this function.

### `bidiMark()` {#bidiMark}

Generates the bidi mark formatting character (LRM or RLM) that corresponds to
the global directionality. Note that if you don't want to insert this mark
unconditionally, you should use bidiMarkAfter(text) instead.

### `bidiStartEdge()` {#bidiStartEdge}

Generates the string `'left'` or the string `right`, if the global
directionality is LTR or RTL, respectively.

### `bidiTextDir(text, opt_isHtml)` {#bidiTextDir}

Checks the provided text for its overall (i.e. dominant) directionality. Returns
`1` for LTR, `-1` for RTL, or `0` for neutral (neither LTR nor RTL). Set the
optional second parameter to `true` if text contains or can contain HTML tags or
HTML escape sequences (default `false`).

### `msgWithId(var)` {#msgWithId}

The `msgWithId` function can be used to access the Id of a `{msg...}{/msg}` at
runtime. Msg ids are usually just used as an implementation detail of the
translation system, but occasionally it is useful to be able to reference these
from an application. A few usecases might be:

*   tracking which messages are actually displayed to users for auditing
    purposes (e.g. what version of a particular text was seen by a given user)
*   recording which texts were displayed as part of an A/B test.

Users can use the `msgWithId` function to assist in these scenarios. Here is a
simple example:

```soy
{let $text kind="text"}
  {msg desc="..."}
    some text
  {/msg}
{/let}
{let $auditableText : msgWithId($text)/ }
<div data-text-id="{$auditableText.id}">
  {$auditableText.msg}
</div>
```

This will render something like

`<div data-text-id="f8H0BhzHc0E=">some text</div>`

The message Id will be encoded as a 64 bit integer that is represented as a web
safe base64 string. The Id will be the same value as what is provided to the
message extractors, so it should be easy to correlate with the translation
artifacts. You can use `com.google.template.soy.msgs.SoyMsgIdConverter` to
convert the encoded IDs back to Java longs.

The `msgWithId` function returns a simple [`record`](types.md#record) consisting
of 2 fields:

*   `id` which is the encoded msg Id
*   `msg` which is the message content

The type declaration for the object is `[id:string, msg: <msg-type>]` where
`msg-type` corresponds to the type of the `let` variable for the message.
Typically this will be `string` or `html`.

IMPORTANT: In order to use this function, you *must* associate the message with
a `let` variable and it *must* be the only child of that `let` variable.
