Commands are instructions you give the compiler to create templates and add
custom logic to templates. Commands are anything inside Soy tags, delimited by
braces (`{}`). The syntax for commands depends on the specific command.

Within the body of a template, any text within Soy tags is parsed and understood
by the compiler, while anything outside of Soy tags is raw text that is output
which can be modified by Soy's [textual commands](textual-commands.md) and
[line joining algorithm](textual-commands.md#line-joining).
