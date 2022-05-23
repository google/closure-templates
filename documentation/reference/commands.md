Commands are statements in the Soy programming language. They can be used to:

*   Declare namespaces
*   Import templates and protos from other files
*   Declare and call templates and elements
*   Assign variables
*   Provide control flow (if, switch, for)
*   And so on.

Commands are delimited by braces (`{}`). Text in a Soy file is treated as a
command or raw text as follows:

*   Text that is **inside braces** is treated as a command. For example, the
    following uses the `call` and `param` commands:

    ```soy {highlight="content:call content:param"}
    {call foo}
      {param bar: "my value" /}
    {/call}
    ```

*   Text that is **outside braces but still inside the body of a template or
    element** is known as **raw text**. Raw text is returned by the template,
    except as modified by Soy's [raw text commands](textual-commands.md) and
    [line joining algorithm](textual-commands.md#line-joining). For example, the
    following template returns the string `<p>Return me</p>`:

    ```soy {highlight="context:1,Return,1"}
    {template baz}
      <p>Return me</p>
    {/template}
    ```

*   Text that is **outside braces and outside the body of a template or
    element** is invalid. For example:

    ```soy {.bad highlight="context:1,Invalid,1"}
    {namespace my.namespace.templates}

    Invalid text

    {template foo}
      ...
    {/template}
    ```
