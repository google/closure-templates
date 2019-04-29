# Translation (Msg, Plurals and Gender)


<!--#include file="commands-blurb-include.md"-->

This chapter describes the Translation commands.

[TOC]

## msg {#msg}

Syntax (basic form):

```soy
{msg desc="<help_text_for_translators>"}
  ...
{/msg}
```

With all optional attributes:

```soy
{msg meaning="<differentiator>" desc="<help_text_for_translators>"}
  ...
{/msg}
```

Use the `msg` tag to mark a section of the template as a message that needs to
be translated. The required `desc` attribute provides explanation text to the
(human) translators.

<p class="note">NOTE: Do not break up a sentence or phrase into multiple messages.
Translation is most effective with the context of the entire sentence or phrase.</p>

Within a `msg` block, the compiler turns HTML tags and Soy tags into
placeholders, with the exception of template tags that generate raw text (e.g.
special character commands and `literal` blocks), which are simply substituted.
Messages that contain placeholders might be confusing to translators, so you
should consider providing (in the `desc` attribute) an example of how the
message looks to the user.

Here are details on how Soy generates placeholders in messages:

-   The compiler uses fixed names for common HTML tags, e.g. `<a ...>` becomes
    `{START_LINK}` and `<br />` becomes `{BREAK}`.

-   A simple `print` tag that prints a data reference turns into a placeholder
    that's named according to the last key name, by default. For example,
    `{$user.emailAddress}` turns into the placeholder `{EMAIL_ADDRESS}`.

-   A complete `call` block (up to the closing `/call`) turns into a single
    placeholder. The placeholder is named `{XXX}` by default, so if you put a
    `call` inside a `msg`, be sure to provide a clear example of what the
    message might look like in the `desc` attribute and consider setting a
    `phname`.

-   If you find a need to specify a placeholder name for a `print` or `call`
    instead of allowing the compiler generate the default name, you can add the
    `phname` attribute. The value can either be camel case or upper underscore.
    For example, `{call .emailAddr phname="emailAddress" /}` causes the
    placeholder name to be `EMAIL_ADDRESS` instead of `XXX`. Note that changing
    a placeholder name changes the message id, causing retranslation.

-   You can set placeholder examples to aid translation. For example, `{$url
    phex="www.google.com"}` or `{$name phex="Alice"}`. Placeholder examples are
    configured via the `phex` attribute and can be specified everywhere that
    `phname` attributes can. `phex` attributes act like the `desc` attribute on
    messages in that they do not change identifiers.

-   If a message contains multiple placeholders that would result in the same
    placeholder name, then the compiler appends number suffixes to distinguish
    them, e.g. `START_LINK_1`, `START_LINK_2`.

-   Template commands for control flow (`if`, `switch`, `for`) are not allowed
    in `msg` blocks. However, you can put them in a `let` block and then print
    the resulting reference from a within a `msg` block. If you do this, make
    sure the `let` block has absolutely no translatable content.

Example message:

```soy
{msg desc="Says hello and tells user to click a link."}
  Hello {$userName}! Please click <a href="{$url}">here</a>.
{/msg}
```

Example of what the translators see:

    Hello {USER_NAME}! Please click {START_LINK}here{END_LINK}.

Example translation that one translator might give us:

    {START_LINK}Zhere{END_LINK} zclick zplease. {USER_NAME} zhello!

Example output (for `userName = 'Jess'` and `url = 'http://www.google.com/'`):

    <a href="http://www.google.com/">Zhere</a> zclick zplease. Jess zhello!

Use the `meaning` attribute when you have two messages that are exactly the same
string in English, but might be translated to different strings in other
languages. The `meaning` should then be a short string that distinguishes the
two messages, and is used for generating different message ids. Translators do
not see the `meaning` attribute, so you must still communicate all the details
in the `desc`. For example:

```soy
{msg meaning="noun" desc="The word 'Archive' used as a noun, i.e. an information store."}
  Archive
{/msg}
{msg meaning="verb" desc="The word 'Archive' used as a verb, i.e. to store information."}
  Archive
{/msg}
```

See more information about translating messages in the
[Translation](../dev/localization.md) chapter.

### Genders

Syntax for a gender aware message in Soy is `{msg genders=...}`.

#### Does not vary in English {#gender}

The syntax for a message that doesn't vary in English is:

```soy
{msg genders="$userGender, $targetGender" desc="..."}
  Join {$targetName}'s community.
{/msg}
```

Even though a message doesn't vary in English, it may vary in other languages
based on gender. For the example above, the form of the verb "join" may vary
based on the gender of the user and the rest of the sentence may vary based on
the gender of the target.

The `genders` attribute is metadata used by translators to provide different
translations of the same English message for different genders. The `genders`
attribute value may contain one or more expressions (though using three or more
gender expressions in one message is strongly discouraged). Each gender
expression should evaluate to a string. There are three recognized cases:
'female', 'male', and any other
string, which is treated as
unknown gender.

For gender expressions, prefer data references (for example,
`$userInfo.userGender`) instead of more complex expressions. Translators see the
last key name in the data reference (the identifier after the last dot), and
this can help a translator determine how to match up the genders with parts of
the message. For example, if there are multiple gender expressions, a translator
might not know whether an expression `$gender` applies to the gender of the user
or the target. Expressions like `$userGender` and `$targetGender` clearly
represent the gender of the user and the gender of the target.

#### Varies in English {#gender-vary}

Gender-aware messages that vary in English can be written using a `select`
operation:

```soy
{msg desc="..."}
  {select $ownerGender}
    {case 'female'}
      {select $targetGender}
        {case 'female'}She shared a document with her.
        {case 'male'}She shared a document with him.
        {default}She shared a document with them.
      {/select}
    {case 'male'}
      {select $targetGender}
        {case 'female'}He shared a document with her.
        {case 'male'}He shared a document with him.
        {default}He shared a document with them.
      {/select}
    {default}
      {select $targetGender}
        {case 'female'}They shared a document with her.
        {case 'male'}They shared a document with him.
        {default}They shared a document with them.
      {/select}
  {/select}
{/msg}
```

If a message varies in English due to only some of the genders involved, the
`select` operation must be used for each gender causing the message to vary in
English. Genders not affecting the message in English may <a href="#gender">use
the `genders` attribute</a>.

For example, In the syntax example below, the message only varies in English due
to `$targetGender`, but not due to `$userGender`. `$targetGender` is therefore
written using a select statement, while `$userGender` is declared in the
`genders` attribute.

```soy
{msg genders="$userGender" desc="..."}
  {select $targetGender}
    {case 'female'}Reply to her.
    {case 'male'}Reply to him.
    {default}Reply to them.
  {/select}
{/msg}
```

### Plural {#plural}

Message pluralization syntax:

```soy
{msg desc="..."}
  {plural $numDrafts}
    {case 0}No drafts
    {case 1}1 draft
    {default}{$numDrafts} drafts
  {/plural}
{/msg}
```

WARNING: You cannot nest `plural` tags.

NOTE: If you need to use `plural` and `select` in the same message (i.e. the
message contains a number and varies in English due to a gender value), then you
must put `plural` within `select`, not the other way around.

#### Offset and remainder {#offset-and-remainder}

There is a rarely used `offset` attribute that works in conjunction with a
special `remainder` function:

```soy
{msg genders="$attendees[0]?.gender, $attendees[1]?.gender"
     desc="Says how many people are attending, listing up to 2 names."}
  {plural length($attendees) offset="2"}
    // Note: length($attendees) is never 0.
    {case 1}{$attendees[0].name} is attending
    {case 2}{$attendees[0].name} and {$attendees[1]?.name} are attending
    {case 3}{$attendees[0].name}, {$attendees[1]?.name}, and 1 other are attending
    {default}{$attendees[0].name}, {$attendees[1]?.name}, and {remainder(length($attendees))} others are attending
  {/plural}
{/msg}
```

The expression inside the `remainder` function must be exactly identical to the
expression in the `plural` tag (in this case `length($attendees)`). However, the
above example could just as well be written without the use of `offset` and
`remainder`. It would be:

```soy
{msg genders="$attendees[0]?.gender, $attendees[1]?.gender"
     desc="Says how many people are attending, listing up to 2 names."}
  {plural length($attendees)}
    // Note: length($attendees) is never 0.
    {case 1}{$attendees[0].name} is attending
    {case 2}{$attendees[0].name} and {$attendees[1]?.name} are attending
    {case 3}{$attendees[0].name}, {$attendees[1]?.name}, and 1 other are attending
    {default}{$attendees[0].name}, {$attendees[1]?.name}, and {length($attendees) - 2} others are attending
  {/plural}
{/msg}
```

NOTE: In the example above, the `attendees` subkeys are accessed using null-safe
accesses (e.g. `$attendees[1]?.gender` instead of `$attendees[1].gender`) see
the [pitfalls section](#placholder_error) for more information about this.

## fallbackmsg {#fallbackmsg}

Syntax:

```soy
{msg <new_attributes>}
  <new_message>
{fallbackmsg <old_attributes>}
  <old_message>
{/msg}
```

If you want to change a message in-place, without worrying about accidentally
displaying the untranslated new source message to foreign-language users while
the new message is getting translated, use the `fallbackmsg` feature. The
compiler automatically chooses the appropriate version of the message to use. If
the new message's translation is in the message bundle, then it is used, else
the compiler falls back to using the old message's translation. (This decision
happens at message insertion time in JavaScript, and at render time in Java.)

When changing a message, move the old message attributes and content to the
`fallbackmsg` portion. Then, write the new message attributes and content in the
`msg` portion.

For example, to fix a typo in this message:

```soy
{msg meaning="verb" desc="button label"}
  Arcive
{/msg}
```

replace it with the new message:

```soy
{msg meaning="verb" desc="button label"}
  Archive
{fallbackmsg meaning="verb" desc="button label"}
  Arcive
{/msg}
```

When replacing an existing message, you can change the message structure,
meaning, placeholders, etc. There are no technical limitations on the
relationship between the old and new messages.

Periodically, you might want to clean up your code by grepping for old
`fallbackmsg`s and removing them. Although, it's not particularly harmful to
leave `fallbackmsg`s in the code longer than necessary.

## Pitfalls

### Runtime errors in `plural`/`select` placeholders {#placholder_error}

The syntax for `plural` and `select` resemble a `switch` statement, however the
actual runtime behavior does not conditionally execute the different 'branches'.
Instead all placeholders in all branches are evaluated eagerly and then passed
to a formatting library. This is because the actual branch that is selected
depends on the translation itself and cannot be easily predicted. This can cause
issues if some branches have placeholders that will cause runtime errors if
evaluated when their branch won't be expected.

```soy
{msg desc="Welcomes everybody."}
  {plural length($people) offset="2"}
    {case 0}Nobody showed up :(
    {case 1}Greetings {$people[0].name}
    {case 2}Greeting {$people[0].name} and {$people[1].name})}
    {default}Greetings y'all
  {/plural}
{/msg}
```

The problem is that if there are 0 items in the list, then all the placeholders
will fail at runtime trying to access the `name` field of `null`.

To fix this you can use null safe access patterns:

```soy
{msg desc="Welcomes everybody."}
  {plural length($people) offset="2"}
    {case 0}Nobody showed up :(
    {case 1}Greetings {$people[0]?.name}
    {case 2}Greetings {$people[0]?.name} and {$people[1]?.name})}
    {default}Greetings y'all
  {/plural}
{/msg}
```

Now, if the list has too few people in it, the later placeholders will just
evaluate to `null` instead of throwing an error.

