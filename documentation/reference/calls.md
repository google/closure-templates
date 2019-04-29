# Calls

<!--#include file="commands-blurb-include.md"-->

This chapter describes the call commands.

[TOC]

## call {#call}

The `call` command calls a template (the callee) and inserts its output into the
current template (the caller).

*   Syntax (basic form):

    ```soy
    {call <TEMPLATE_NAME> /}
    ```

*   With enclosed parameters:

    ```soy
    {call <TEMPLATE_NAME>}
      {param <KEY1>: <EXPRESSION> /}
      {param <KEY2> kind="<KIND>"}
        ...
      {/param}
    {/call}
    ```

*   With values from the caller template's data:

    ```soy
    {call <TEMPLATE_NAME> data="<DATA_TO_PASS>"/}
    ```

### Passing values

The following sections discuss these five options for passing values to a callee
template:

1.  Pass values using `param` commands.
1.  Pass values using the `data` attribute.
1.  Pass all of the caller template's `data`.
1.  Construct values to pass using `param` commands.
1.  Use a combination of these.

We'll use this callee template for the following examples:

```soy
{template .exampleCallee}
  {@param largerNum: int} /** An integer larger than 'smallerNum'. */
  {@param smallerNum: int} /** An integer smaller than 'largerNum'. */
  {$largerNum} is greater than {$smallerNum}.
{/template}
```

#### Pass values using `param` commands (recommended)

To pass values to the callee, use `param` commands inside the `call` function
with names that match the callee's parameters.

```soy
{template .exampleCaller}
  {let $largerNum: 20 /}
  {let $smallerNum: 10 /}

  {call .exampleCallee}
    {param largerNum: $largerNum /}
    {param smallerNum: $smallerNum /}
  {/call}
{/template}
```

This is the *recommended* way of calling templates in Soy as explicit data
passing makes code more readable and easier to debug.

#### Pass values using the `data` attribute

**WARNING**: This is not supported by JSWire. b/123785421

You can also pass data to the callee with the `call` command's `data` attribute.
This accepts a variable of type
[record](/third_party/java_src/soy/g3doc/reference/types.md#record). The `call`
command sets the values of any parameters in the callee whose names match fields
in the record.

For example, the following call sets the value of the `largerNum` parameter to
20 and the `smallerNum` parameter to 10. It is equivalent to the call in the
previous section.

```soy
{template .exampleCaller}
  {let $pair : record(largerNum: 20, smallerNum: 10)}
  {call .exampleCallee data="$pair" /}
{/template}
```

**WARNING**: When passing data in this way much of the call-site type checking
that Soy normally performs is *disabled*. So it can be easy to make simple
mistakes like forgetting to pass a required parameter or passing a parameter of
the wrong type.

If the record contains fields whose names do not match any parameter names,
these are ignored by the callee. Similarly, if there are any parameters whose
names do not match any field names, these are not set; whether this causes an
error depends on [whether the parameter is
required](/third_party/java_src/soy/g3doc/reference/templates.md#param).

#### Pass all of the caller's `data`

A template's *data* is a record that contains:

*   All of the fields passed to the template with the `data` attribute, even if
    they did not match a parameter.
*   One field for each parameter not passed with the `data` attribute.

If a call includes a `param` command with the same name as a field in the `data`
record, the value from the `param` command is used.

For example, in the following call, the data of `.exampleCallee` will be a
record containing the fields `largerNum`, `smallerNum`, and `otherNum`:

```soy
{template .exampleCaller}
  {let $pair : record(largerNum: 20, smallerNum: 10) /}
  {let $otherNum : 5 /}
  {call .exampleCallee data="$pair"}
    {param otherNum: $otherNum /}
  {/call}
{/template}
```

To pass all of the caller's data, you can use the special attribute
`data="all"`. This means that the callee's required parameters must be a subset
of the caller's data, and have the same names. In such cases, you can use
`data="all"` instead of re-listing the parameters. For example, if the caller's
data contains two fields `aaa` and `bbb`, and you want to pass both to the
callee (with the same parameter names), you can simply set `data="all"`.

```soy {.bad}
// Discouraged.
{template .exampleCaller}
  {call .exampleCallee data="all" /}
{/template}
```

While this seems advantageous, it has considerable drawbacks for code
maintenance, as parameters *could* potentially be passed through several call
layers before they're used and obfuscate where a value is coming from. Take the
following example:

```soy
{template originalCallee}
  {@param requiredNum: int}
  {@param? optionalNum: int}

  {call passthroughCallee1 data="all" /}
{/template}

{template passthroughCallee1}
  {@param requiredNum: int}

  {call passthroughCallee2 data="all" /}
{/template}

{template passthroughCallee2}
  {@param requiredNum: int}

  {call finalCallee data="all" /}
{/template}

{template finalCallee}
  {@param requiredNum: int}
  {@param? optionalNum: int}
{/template}
```

If you were debugging why `optionalNum` is being passed to `finalCallee` when
called through `originalCallee`, it may not be straightforward to determine that
it's been "passed through" on two levels of calls implicitly.

This is a fairly simple example where the templates are short and take few
parameters, however you can see this becoming much harder to traceback in
real-world usage. Therefore, the aforementioned explicit usage of `param`
enclosed parameters is considered the best practice, and usage of `data="all"`
is discouraged.

#### Construct values to pass

The `param` command constructs values to pass to the callee.

Syntax for arbitrary values:

```soy
{param <key>: <EXPRESSION> /}
```

Syntax for rendering to string ("block form"):

```soy
{param <key> kind="<kind>"}...{/param}
```

The first form is for arbitrary values and should be used in most cases.

The second form renders the contents of the `param` block to a string, including
applying autoescaping (based on the specified content `kind`). It is needed in
some situations, but it's usually an error to use it when the first form would
suffice. A common heuristic is that if you're using the second form with a
`param` block that contains a single `print` tag:

```soy
{param aaa kind="html"}{$bbb}{/param}
```

then it's usually an error, and should be replaced with:

```soy
{param aaa: $bbb /}
```

As a more complex example, assume that the caller's parameters include a list
`pairs` where each element is a record always containing a value for the field
`largerInt` and only sometimes containing a value for the field `smallerInt`
(otherwise undefined). Use this to make the call:

```soy
{for $pair in $pairs}
  {call .exampleCallee}
    {param largerNum: $pair.largerInt /}
    {param smallerNum kind="text"}
      {if $pair.smallerInt}
        {$pair.smallerInt}  // if defined, pass it
      {else}
        {$pair.largerInt - 100}
      {/if}
    {/param}
  {/call}
{/for}
```

The above example demonstrates the two forms of the `param` command. The first
form evaluates an EXPRESSION and passes the result as the parameter. The second
form renders an arbitrary section of Soy code and passes the result as the
parameter.

If you're observant, you've probably noticed two issues. The first issue is the
value passed for `smallerNum` is now a string, not an integer (fortunately in
this case the callee only uses the parameter in a string context). If you
noticed this issue, then perhaps you also noticed that the computation for
`smallerNum` can be put into an EXPRESSION so that it can be passed as an
integer:

```soy
{param smallerNum: $pair.smallerInt ? $pair.smallerInt : $pair.largerInt - 100 /}
```

The second issue is if `$pair.smallerInt == 0`, then it would be falsy and so
`$largerInt - 100` would be passed even though `$pair.smallerInt` is defined and
should be passed. This is why you should generally avoid using sometimes-defined
field values. If you do use them, consider the possibility of falsy values. In
this example, if there's a possibility that `$pair.smallerInt == 0`, you could
correct the bug by rewriting the EXPRESSION using the function `isNonnull`:

```soy
{param smallerNum: isNonnull($pair.smallerInt) ? $pair.smallerInt : $pair.largerInt - 100 /}
```

or better yet, use the null-coalescing operator:

```soy
{param smallerNum: $pair.smallerInt ?: $pair.largerInt - 100 /}
```

#### Use a combination of these

Finally, you can mix the two options above: you can pass some values with the
`data` attribute and some values with `param` commands. For example, if the
caller's parameters include an integer `largerNum` and a list of integers
`smallerNums` that are all smaller than `largerNum`, you could use the following
template:

```soy
{for $smallerNum in $smallerNums}
  {call .exampleCallee data="record(largerNum: $largerNum)"}
    {param smallerNum: $smallerNum /}
  {/call}
{/for}
```

If a call includes a `param` command with the same name as a field in the `data`
record, the value from the `param` command is used. In subsequent calls that use
`data="all"`, the value of the field is the `param` value.

### Calling a template in a different namespace

All of the above examples demonstrate calling a template in the same namespace,
hence the partial template name (beginning with a dot) used in the `call`
command text. To call a template from a different namespace, use the full
template name including namespace. For example:

```soy
{call myproject.mymodule.myfeature.exampleCallee}
  {param pair: $pair /}
{/call}
```

Or, if you've aliased the namespace of the template you're calling to its last
identifier, then

```soy
{call myfeature.exampleCallee}
  {param pair: $pair /}
{/call}
```

### Aliasing

Check out how to shorten calls with [alias
declarations](file-declarations.md#alias).
