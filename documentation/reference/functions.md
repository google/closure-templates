# Methods and Functions

Soy methods and functions are called from within Soy
[expressions](expressions.md).

The table below lists basic Soy functions and methods that are available by
default. For Soy functions related to bidirectional text, see
[Bidi Support](../dev/localization#bidi_functions). For information on writing
custom external functions, see
[Creating an External Function](../dev/externs.md).

[TOC]

## Basic Functions

### `checkNotNull(value)` {#checkNotNull}

Throws a runtime exception if the given value is `null` and returns the value
otherwise.

This function is integrated into the type system, so if `value` is typed as a
nullable value, the return value of `checkNotNull` will no longer be nullable.
This can be useful when passing values to templates that expect non nullable
values.

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

### `floor(number)` {#floor}

The floor of the number.

### `ceiling(number)` {#ceiling}

The ceiling of the number.

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

### `randomInt(rangeArg)` {#randomInt}

A random integer in the range `[0, rangeArg - 1]` (where `rangeArg` must be a
positive integer).

### `sqrt(number)` {#sqrt}

Returns the square root of the number.

### `pow(number, number)` {#pow}

Returns the value of the first argument raised to the power of the second
argument.

## List Methods

<span id="list-any_length"></span>

### `list.length()` {#length}

Returns the length of a list.

Also callable as deprecated global function: `length(list)`

<span id="list-any_concat"></span>

### `list.concat(list)` {#concatLists}

Joins two or more lists together.

<span id="list-any_contains"></span>

### `list.contains(value)` {#listContains}

Checks if the given value is inside the list. This method implements JavaScript
semantics, comparing elements with `==`. Therefore it only works on lists of
primitive values.

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

<span id="list-string|int_join"></span>

### `list.join(separator)` {#join}

Joins a list of strings or numbers with a string separator.

Also callable as deprecated global function: `join(list, separator)`

<span id="list-number_sort"></span>

### `list<number>.sort()` {#sort}

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

## Map Methods

<span id="map-any,any_keys"></span>

### `map.keys()` {#mapKeys}

The keys of a [map](types.md#map) as a list. There is no guarantee on order.

### `map.values()` {#map-any,any_values}

The values of a [map](types.md#map) as a list. There is no guarantee on order.

### `map.entries()` {#map-any,any_entries}

The entries of a [map](types.md#map) as a list of records with fields `key` and
`value`. There is no guarantee on order.

### `map.length()` {#map-any,any_length}

The number of keys in a [map](types.md#map).

### `map.concat(map)` {#map-any,any_concat}

Combines two [map](types.md#map)s into one. If there's a key collision the value
from the method parameter wins.

## String Methods

NOTE: it is generally tricky to manipulate user authored text with these
functions, these are intended for manipulating textual 'data', not something
that should be displayed to a human. These functions are not generally unicode
aware and may do bad things if used naively. For example, consider calling
`.substring()` on text containing emoji, without being extremely careful you are
likely to break the emoji and subvert user intention.

<span id="string_contains"></span>

### `str.contains(subStr)` {#strContains}

Checks whether a string contains a particular substring.

Also callable as deprecated global function: `strContains(str, subStr)`

### `str.endsWith(subStr[, length])` {#string_endsWith}

Checks whether a string ends with a particular substring. If length is provided,
it is used as the length of str. Defaults to str.length.

<span id="string_indexOf"></span>

### `str.indexOf(subStr[, position])` {#strIndexOf}

Returns the character index of the first occurrence of `substr` within `str`, or
`-1`. Case-sensitive, 0-based index. Given a value for position, it returns the
first index greater than or equal to position.

Also callable as deprecated global function: `strIndexOf(str, subStr)`

WARNING: The index is based on characters (aka java `char` or utf-16
characters), not Unicode codepoints or more useful concepts like graphemes. It
is almost never valid to use this to break text meant for users into parts since
it will be very easy to break the string (e.g. split an emoji in half).

<span id="string_length"></span>

### `str.length()` {#strLen}

Returns the length of a string in characters.

Also callable as deprecated global function: `strLen(str)`

WARNING: The length is based on characters (aka java `char` or utf-16
characters), not Unicode codepoints or more useful concepts like graphemes. It
is almost never valid to use this to break text meant for users into parts since
it will be very easy to break the string (e.g. split an emoji in half).

### `str.replaceAll(t, s)` {#string_replaceAll}

Returns a copy of `str` with all occurrences of string `t` replaced with `s`.

### `str.split(sep)` {#string_split}

Returns a list of tokens generated by splitting a string on a separator. If
`sep` is the empty string then returns a list with each character of the string.

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

Also callable as deprecated global function: `strSub(str, start[, end])`

<span id="string_toAsciiLowerCase"></span>

### `str.toAsciiLowerCase()` {#strToAsciiLowerCase}

Returns the lowercase representation of the given string.

NOTE: This function doesn't consider locales when tranforming the string and it
only transforms ASCII characters `A-Z`. Do not use it to lowercase string that
are localized and/or UNICODE.

Also callable as deprecated global function: `strToAsciiLowerCase(str)`

<span id="string_toAsciiUpperCase"></span>

### `str.toAsciiUpperCase()` {#strToAsciiUpperCase}

Returns the uppercase representation of the given string.

NOTE: This function doesn't consider locales when tranforming the string and it
only transforms ASCII characters `a-z`. Do not use it to uppercase string that
are localized and/or UNICODE.

Also callable as deprecated global function: `strToAsciiUpperCase(str)`

### `strSmsUriToUri(string)` {#strSmsUriToUri}

Returns a sanitized uri given an SMS uri string.

The RFC for sms: https://tools.ietf.org/html/rfc5724

### `str.trim()` {#string_trim}

Returns a copy of a string with leading and trailing whitespace removed.

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

Returns the minified and obfuscated version of a string. The argument can either
be a `string` literal or a dotted identifier. The implementation of this
function is configurable and depends on the backend. In JavaScript, this turns
into a call to the `xid` function which uses the `@idgenerator` mechanism of the
Closure Compiler to perform compile time obfuscation. In Java, this uses a
`SoyIdRenamingMap` which can be configured directly with the renderering APIs
(e.g. `SoySauce.Renderer.setXidRenamingMap` or
`SoyTofu.Renderer.setIdRenamingMap`).

When a dotted identifier is passed, it is as if you had passed an equivalent
string literal, the benefit is that using this syntax you can take advantage of
the [`alias`](file-declarations.md#alias) syntax to abbreviate the identifier.

For example, assuming that the file has `{alias foo.bar}`, then
`xid('foo.bar')`, `xid(foo.bar)`, and `xid(bar)` will all evaluate to the same
value.

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
  {@param fn: (a:string) => html}
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
  {@param fn: (a:string) => html}
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
    "native" so that we show native characters in languages like arabic (this
    argument is ignored for templates running in JavaScript).

    NOTE: see http://userguide.icu-project.org/locale for more "numbers"
    keywords

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
        zeros. "4.1234" would be formatted as "4.123", but "4.100" would be
        formatted as "4.1".

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
