# Functions


`Closure Templates` functions are called from within `Closure Templates`
[expressions](expressions.md).

The table below lists basic `Closure Templates` functions that are available by
default. For `Closure Templates` functions related to bidirectional text, see [Bidi
Support](../dev/localization#bidi_functions). For information on writing custom
`Closure Templates` functions, see [Plugins](../dev/plugins.md#function_plugins).

[TOC]


## `isFirst($var)` {#isFirst}

Use this with the `for` command. See the [`for` section](control-flow.md#for) of
the Commands chapter.

## `isLast($var)` {#isLast}

Use this with the `for` command. See the [`for` section](control-flow.md#for) of
the Commands chapter.

## `index($var)` {#index}

Use this with the `for` command. See the [`for` section](control-flow.md#for) of
the Commands chapter.

## `isNonnull(value)` {#isNonnull}

Returns `true` if the given value is not `null`.

## `isNull(value)` {#isNull}

Returns `true` if the given value is `null`.

## `checkNotNull(value)` {#checkNotNull}

Throws a runtime exception if the given value is `null` and returns the value
otherwise.

This function is integrated into the type system, so if `value` is typed as a
nullable value, the return value of `checkNotNull` will no longer be nullable.
This can be useful when passing values to templates that expect non nullable
values.

## `v1Expression(stringLiteral)` {#v1Expression}

The `v1Expression` function is part of the support for deprecated V1 syntax.
This function can only be used by the JavaScript backend in legacy whitelisted
files. When used the function must take a
[string literal](expressions.md#string-literal) that contains some pseudo
`Closure Templates` code. The JavaScript backend will perform some simple textual
replacements to make variable references work, but otherwise emit it as is in
the generated JavaScript.

## `remainder(length)` {#remainder}

The `remainder` function is used in the context of plural messages. See the
[reference on plurals](messages.md#offset-and-remainder) for more information.

## `length(list)` {#length}

Returns the length of a list.

## `concatLists(list, list...)` {#concatLists}

Joins two or more lists together.

## `listContains(list, value)` {#listContains}

Checks if the given value is inside the list.

## `join(list, separator)` {#join}

Joins the list of strings with a string separator.

## `keys(legacyObjectMap)` {#keys}

The keys of a [legacy object map](types.md#legacy_object_map) as a list. There
is no guarantee on order.

## `mapKeys(map)` {#mapKeys}

The keys of a [map](types.md#map) as a list. There is no guarantee on order.

## `mapToLegacyObjectMap(map)` {#mapToLegacyObjectMap}

Converts a [map](types.md#map) to an equivalent
[legacy_object_map](types.md#legacy_object_map).

Because legacy object maps do not support non-string keys, all of the keys are
coerced to strings in the returned legacy object map.

## `legacyObjectMapToMap(legacyObjectMap)` {#legacyObjectMapToMap}

Converts a [legacy object map](types.md#legacy_object_map) to an equivalent
[map](types.md#map).

## `augmentMap(baseMap, additionalMap)` {#augmentMap}

WARNING: deprecated, this is only usable with the
[legacy object map](types.md#legacy_object_map) type which is deprecated.

Builds an augmented map. The returned map contains mappings from both the base
map and the additional map. If the same key appears in both, then the value from
the additional map is visible, while the value from the base map is hidden. The
base map is used, but not modified.

## `round(number)`, `round(number, numDigitsAfterDecimalPoint)` {#round}

Rounds the given number to an integer.

If `numDigitsAfterDecimalPoint` is positive, round to that number of decimal
places; if `numDigitsAfterDecimalPoint` is negative, round to an integer with
that many 0s at the end.

## `floor(number)` {#floor}

The floor of the number.

## `ceiling(number)` {#ceiling}

The ceiling of the number.

## `min(number, number)` {#min}

The min of the two numbers.

## `max(number, number)` {#max}

The max of the two numbers.

## `parseInt(str)` {#parseInt}

Parses the string argument as a signed base 10 integer. Returns `null` if the
string cannot be parsed.

## `parseFloat(str)` {#parseFloat}

Parses the string argument as a floating point number. Returns `null` if the
string cannot be parsed.

## `randomInt(rangeArg)` {#randomInt}

A random integer in the range `[0, rangeArg - 1]` (where `rangeArg` must be a
positive integer).

## `sqrt(number)` {#sqrt}

Returns the square root of the number.

## `strContains(str, subStr)` {#strContains}

Checks whether a string contains a particular substring.

## `strIndexOf(str, subStr)` {#strIndexOf}

Returns the first occurrence of `substr` within `str`, or `-1`. Case-sensitive,
0-based index.

## `strLen(str)` {#strLen}

Returns the length of a string.

## `strSmsUriToUri(string)` {#strSmsUriToUri}

Returns a sanitized uri given an SMS uri string.

The RFC for sms: https://tools.ietf.org/html/rfc5724

## `strSub(str, start)` {#strSub}

Returns the substring of `str` beginning at index `start`.

## `strSub(str, start, end)` {#strSub}

Returns the substring of `str` beginning at index `start`, and ending at `end -
1`.

## `strToAsciiLowerCase(str)` {#strToAsciiLowerCase}

Returns the lowercase representation of the given string.

NOTE: This function doesn't consider locales when tranforming the string and it
only transforms ASCII characters `A-Z`. Do not use it to lowercase string that
are localized and/or UNICODE.

## `strToAsciiUpperCase(str)` {#strToAsciiUpperCase}

Returns the uppercase representation of the given string.

NOTE: This function doesn't consider locales when tranforming the string and it
only transforms ASCII characters `a-z`. Do not use it to uppercase string that
are localized and/or UNICODE.

## `css([baseClass,] selector)` {#css}

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
{template .foo cssbase="foo.bar.baz"}
<div class={css('%Menu')}>
...
</div>
{/template}
```

When the css selector is prefixed with a percent symbol, `Closure Templates` will
use the following rules to decide what to do:

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

## `xid(str), xid(id)` {#xid}

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

## `range([start,] end[, step])` {#range}

Use this to create lists containing arithmetic progressions. It is most often
used in [indexed for loops](control-flow.md#for-indexed). If the `step` argument
is omitted, it defaults to 1. If the `start` argument is omitted, it defaults to
0. The full form returns a list of plain integers `[start, start + step, start +
2 * step, ...]`.

This function behaves identically to the Python `range` builtin function, or the
Closure `goog.array.range` function.

## `htmlToText(html)` {#htmlToText}

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

## `bidiDirAttr(text, opt_isHtml)` {#bidiDirAttr}

If the overall directionality of text is different from the global
directionality, then this function generates the attribute `dir=ltr` or
`dir=rtl`, which you can include in the HTML tag surrounding that piece of text.
If the overall directionality of text is the same as the global directionality,
this function returns the empty string. Set the optional second parameter to
`true` if text contains or can contain HTML tags or HTML escape sequences
(default `false`).

## `bidiEndEdge()` {#bidiEndEdge}

Generates the string `'right'` or the string `'left'`, if the global
directionality is LTR or RTL, respectively.

## `bidiGlobalDir()` {#bidiGlobalDir}

Provides a way to check the current global directionality. Returns `1` for `LTR`
or `-1` for `RTL`. The global directionality is inferred from the current
locale.

## `bidiMarkAfter(text, opt_isHtml)` {#bidiMarkAfter}

If the exit (not overall) directionality of text is different from the global
directionality, then this function generates either the `LRM` or `RLM` character
that corresponds to the global directionality. If the exit directionality of
text is the same as the global directionality, this function returns the empty
string. Set the optional second parameter to `true` if text contains or can
contain HTML tags or HTML escape sequences (default `false`). You should use
this function for an inline section of text that might be opposite
directionality from the global directionality. Also, set text to the text that
precedes this function.

## `bidiMark()` {#bidiMark}

Generates the bidi mark formatting character (LRM or RLM) that corresponds to
the global directionality. Note that if you don't want to insert this mark
unconditionally, you should use bidiMarkAfter(text) instead.

## `bidiStartEdge()` {#bidiStartEdge}

Generates the string `'left'` or the string `right`, if the global
directionality is LTR or RTL, respectively.

## `bidiTextDir(text, opt_isHtml)` {#bidiTextDir}

Checks the provided text for its overall (i.e. dominant) directionality. Returns
`1` for LTR, `-1` for RTL, or `0` for neutral (neither LTR nor RTL). Set the
optional second parameter to `true` if text contains or can contain HTML tags or
HTML escape sequences (default `false`).

## `msgId(var)` {#msgId}

WARNING: deprecated, please migrate to [`msgWithId`](#msgWithId)

The `msgId` function can be used to access the Id of a `{msg...}{/msg}` at
runtime. Msg ids are usually just used as an implementation detail of the
translation system, but occasionally it is useful to be able to reference these
from an application. A few usecases might be:

*   tracking which messages are actually displayed to users for auditing
    purposes (e.g. what version of a particular text was seen by a given user)
*   recording which texts were displayed as part of an A/B test.

Users can use the `msgId` function to assist in these scenarios. Here is a
simple example:

```soy
{let $text kind="text"}
  {msg desc="..."}
    ....
  {/msg}
{/let}
<div data-text-id="{msgId($text)}">
  {$text}
</div>
```

This will render something like

`<div data-text-id="f8H0BhzHc0E=">...</div>`

The message Id will be encoded as a 64 bit integer that is encoded as a web safe
base64 string. The Id will be the same value as what is provided to the message
extractors, so it should be easy to correlate with the translation artifacts.

IMPORTANT: In order to get the id of a particular message, you must associate
the message with a `let` variable and it must be the only child of that let
variable.

## `msgWithId(var)` {#msgWithId}

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

## `ve_data(ve, data)` {#ve_data}

See the documentation for the [ve_data literal](expressions.md#ve_data).
