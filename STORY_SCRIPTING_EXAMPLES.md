# SME — Story Scripting Examples

A cookbook, one construct at a time, then the full `prologue.sme` demo walked through. See
`STORY_SCRIPTING_DESIGN.md` for the grammar, compiler mapping, and architecture behind each of these.

## Story, variables, `set`

```sme
story my_story {
    var village.reputation = 0        // a world-scoped variable — no "player." prefix
    var player.has_met_guard = false  // a per-player variable — routes to the player's own capability

    // (declarations go here — dialogue/quest/trigger/sequence)
}
```

`set` inside any statement block:

```sme
set village.reputation += 1
set village.reputation -= 1
set player.has_met_guard = true
```

Reading a variable that was never `set` returns its declared type's zero value (`0`/`false`/`0.0`/
`""`) regardless of the `var` declaration's own initial literal — see the design doc §4f for why.

## Conditions

```sme
if (village.reputation >= 50) {
    start dialogue friendly_guard
} elseif (village.reputation >= 0) {
    start dialogue neutral_guard
} else {
    start dialogue hostile_guard
}
```

Operators: `== != > < >= <= && || !`. A comparison's operands can be variables or literals of any
matching type (numbers compare numerically, strings compare lexicographically, booleans only support
`==`/`!=`).

## Dialogue

```sme
dialogue guard_intro {
    Guard: "Who are you?"

    choice {
        "I'm a traveller." {
            set village.reputation += 1
            jump welcome
        }
        "None of your business." when (village.reputation < 10) {
            end
        }
    }

    node welcome
    Guard: "Then welcome to the village."
    end
}
```

- `Speaker: "line"` is a plain line.
- `choice { "text" [when (expr)] { ...body... } }` — the body is an arbitrary statement list (any
  `set`/`start quest`/`start dialogue`/`play cinematic`/command call/`if`), ending optionally in
  `jump <node>` or `end`. `wait`/`wait event`/`call` are **not allowed** inside a dialogue body — it
  runs synchronously and cannot suspend (a compile-time error, not a silent no-op, if you try).
- `node <id>` is a bare label (no braces) — everything after it, up to the next label or the closing
  `}`, belongs to that node. Entries before the first label form an implicit `"start"` node.
- `gate (expr)` ends the dialogue outright if the condition fails at that point.
- A bare `{ ... }` block (no `choice`/`gate` prefix) is an unconditional action, run every time that
  point in the dialogue is reached.

## Quest

```sme
quest village_trial {
    title "The Guard's Trial"
    description "Prove your worth."
    prerequisite met_the_guard          // another quest's id — must already be completed

    objective kill zombie 3             // unqualified ids default to the "minecraft" namespace
    objective collect emerald 5
    objective talk_to elder
    objective interact village_gate
    objective find_entity wolf 10       // the trailing number here is a search RADIUS, not a count
    objective dialogue farewell_speech  // waits for another dialogue to complete
    objective quest side_quest_id       // waits for another quest to complete

    reward {
        item emerald 3
        xp 100
        command "say Congratulations!"
        unlock_quest next_chapter
    }
}
```

## Trigger

```sme
trigger village_entry {
    when player enters "village_gate"   // a zone registered via ZoneRegistry.register(...) from Java
    once                                 // or "repeat" (the default if omitted)
    run {
        start dialogue guard_intro
    }
}

trigger midnight_event {
    when time 18000
    run { play cinematic night_reveal }
}

trigger on_kill {
    when event entity.kill               // resolved via EventNameRegistry
    run { set player.kills += 1 }
}
```

## Sequence (Flow scripting)

```sme
sequence intro_sequence {
    start dialogue guard_intro
    wait 2s                              // "s"=seconds (×20 ticks), "t"=raw ticks, "m"=minutes
    play cinematic village_attack
    start quest village_trial
    wait event quest.completed
    call another_sequence
}
```

A `sequence <id> { }` is directly callable via `call <id>` from another sequence or a trigger's `run`
body. An anonymous `sequence { }` (no id) is only legal directly as a trigger's `run` body — which is
exactly what `run { ... }` already is, so you rarely write the `sequence` keyword yourself outside a
named, reusable one.

## Raycast

```sme
if raycast.entity {
    give player diamond 1
} else {
    play_sound minecraft:block.note_block.bass
}

raycast {
    distance 30
    entities
    living
} {
    set player.found_something = true
}
```

## Custom commands (`@StoryCommand`)

Java side, in your own mod:

```java
public final class MyStoryCommands {
    @StoryCommand("heal")
    public static void heal(ServerPlayer player, int amount) {
        player.heal(amount);
    }
}
```

`.sme` side, once your mod's own `@StoryCommand` methods are discovered at boot:

```sme
heal player 10
```

Built-in commands (same mechanism, ship with the engine): `give <item> <count>`,
`teleport <zone>`, `play_sound <sound_id>`, `spawn <entity_id>`, `set_block <block_id> <x> <y> <z>`.

## Metadata tags

```sme
@chapter("1")
@author("DimaLab")
story prologue {
    @debug
    trigger test_trigger { ... }
}
```

Informational only — never affects compilation.

---

## `prologue.sme`, walked through

The full demo, shipped at
`src/main/resources/data/storymodengine/storymodengine/stories/prologue.sme`:

```sme
story prologue {

    var village.reputation = 0
    var player.has_met_guard = false

    trigger village_entry {
        when player enters "village_gate"
        once
        run {
            if (!player.has_met_guard) {
                set player.has_met_guard = true
                start dialogue guard_intro
            }
        }
    }

    dialogue guard_intro {
        Guard: "Halt! State your business in our village."
        choice {
            "I come in peace." when (village.reputation >= 0) {
                set village.reputation += 1
                start quest village_trial
                jump farewell
            }
            "None of your business." {
                set village.reputation -= 1
                end
            }
        }
        node farewell
        Guard: "Then welcome, traveller. Prove yourself and we'll trust you."
        end
    }

    quest village_trial {
        title "The Guard's Trial"
        description "Prove your worth to the village guard."
        objective kill zombie 3
        objective collect emerald 5
        reward {
            item emerald 3
            xp 100
        }
    }

    trigger trial_complete {
        when event quest.completed
        once
        run {
            set village.reputation += 5
            play cinematic village_welcome
            wait 3s
            start dialogue guard_welcome
        }
    }

    dialogue guard_welcome {
        Guard: "You've proven yourself. Welcome to our village, friend."
        end
    }
}
```

Beat by beat:

1. **Village entry → trigger**: `village_entry` fires when the player enters the `"village_gate"`
   zone (registered from Java in `DemoZones.java`, bundled with the engine). It guards against firing
   twice for the same reason a player might re-enter — `player.has_met_guard` — even though the
   trigger itself is already `once`-per-player, as a belt-and-suspenders example of reading a
   variable inside a trigger's `run` body.
2. **Guard dialogue → player choice**: `guard_intro` opens with one line, then offers two choices.
   The `when (village.reputation >= 0)` guard on the first choice demonstrates a condition reading a
   *different* variable than the one the choice itself mutates.
3. **Reputation change**: both choices adjust `village.reputation` via `+=`/`-=` — a world-scoped
   variable, so it persists per level, not per player.
4. **Quest start**: the "I come in peace" branch starts `village_trial` immediately, then `jump`s to
   the `farewell` node to deliver a closing line before the dialogue ends naturally.
5. **Objectives**: `village_trial` needs 3 zombie kills and 5 emeralds collected — both wired
   automatically to the engine's existing Forge-event bridge, no event-hooking code needed in `.sme`
   or in this demo's own Java.
6. **Completion → trigger → cinematic**: `trial_complete` listens for `quest.completed` and, once it
   fires, bumps reputation again, plays the `village_welcome` cutscene (a minimal hand-authored Java
   cutscene in `DemoCutscenes.java`, since full cinematic authoring isn't part of SME itself), waits 3
   seconds, and starts the closing dialogue.
7. **Next dialogue**: `guard_welcome` delivers the final line and ends.

Every required construct from the language surface appears at least once in this one file: `var`/
`set` (both scopes, both operators), `if`, dialogue lines/choices/conditions/jump/end/labeled nodes,
quest objectives (two kinds) and rewards (two kinds), both a location- and an event-driven trigger,
`play cinematic`, and `wait <duration>`.
