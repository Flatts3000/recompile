# Handoff: devbridge ports, and a verification pattern that transferred

**Written 2026-08-04 from the Trashlands session that consumed
`../trashlands/docs/handoff_gamebridge.md`.** Everything below was verified on this machine during
that session. This is a task brief; the design is `F:\devbridge\SPEC.md` and
`F:\minecraft-repos\mc-pack-toolkit\gamebridge\README.md`.

## The one thing to change

**`build.gradle` line 31 sets `systemProperty 'devbridge.port', '25580'`, which is devbridge's own
default.** Every project that uses the mod therefore lands on the same port, and two of them now do.

Trashlands has claimed **8604** for its instance. Recompile should claim its own rather than keep the
shared default:

```bash
ports claim Recompile --root F:/minecraft-repos/recompile --service devbridge --band tool
```

then put the number it returns in `build.gradle` in place of `25580`. The registry lives at
`~/.claude/port_registry.yaml`; `Trashlands [packwiz-serve] 8603` and `Trashlands [devbridge] 8604`
are already in it.

## Why it matters, with the actual failure

The first run of Trashlands' quest verifier reported **all six item references resolving**. It had
connected to this repo's gradle dev client, not to the pack. The pid's command line was
`F:\minecraft-repos\recompile\build\...` on NeoForge 26.1.2.76.

That result was not a lie so much as an answer about the wrong game: this dev run has Recompile and
Modonomicon loaded, so ids from both resolved. It would have missed anything from a mod that only the
pack has, which is most of the pack.

**A tool that connects to a fixed port needs to prove what answered it.** Trashlands' verifier now
probes `ftbquests:book` first, which the pack has and this dev run does not, and exits rather than
report a pass it cannot stand behind. If anything here ever verifies against a port, it wants the
same shape of guard - a sentinel that is present in *this* repo's run and absent from the others.

## `ports check` will not catch this

Worth knowing before trusting the registry as protection:

```
$ ports who 25580
port 25580  band=(none)
  registry: (unassigned)
  live:     not listening

$ netstat -ano | grep 25580
  TCP    [::1]:25580   [::]:0   LISTENING   57436
```

**The ports helper only enumerates IPv4 listeners.** devbridge binds
`InetAddress.getLoopbackAddress()`, which is `::1` on a JVM that prefers IPv6 - gamebridge's own
`devbridge.py` carries a comment about this, and it is why the client must dial `localhost` rather
than `127.0.0.1`. So the helper reports the port free while a game is listening on it.

Claiming a port prevents the clash. It does not detect one. The sentinel probe is the real guard.

## The version question in the Trashlands handoff is answered

That doc flagged devbridge as built against NeoForge 26.1.2.76 and untested on the pack's .94. The
built jar's own metadata settles it:

```
loaderVersion = "[4,)"
neoforge      = "[4,)"
minecraft     = "[26.1.2]"
```

Nothing in it constrains the loader build. It loads on .94.

## A pattern worth stealing back

Trashlands needed to answer "does this item id still exist" without a player noticing. The technique
is to hand the id to the command parser, which rejects an unknown item as a *parse* error before the
command looks for anything to act on:

```
give @a[tag=nobody_carries_this] recompile:garbage_block   ->  "No player was found"
give @a[tag=nobody_carries_this] recompile:not_a_real_item ->  "Unknown item 'recompile:...'"
```

Both verified live against this repo's dev client.

**The selector matching nobody is the load-bearing part.** Against a playerless dedicated server a
bare `@a` is harmless. In a singleplayer world there is a real player connected, and the same command
hands them one of everything you were only trying to ask about. A tag no entity carries keeps the
command fully parsed and completely inert, which is what makes it safe to run against a world you
care about.

**Where this could pay here.** This repo has 117 recipe files plus loot tables, data maps and tag
files, all naming item ids in JSON that nothing type-checks. A sweep asserting every id in
`src/main/resources/data/recompile/` resolves at runtime would catch the class of bug where a rename
lands in the code and one datapack file keeps the old name - which fails silently, because a recipe
with an unknown ingredient is dropped at load with no crash. This is a suggestion, not a verified
need: nobody has checked whether such a drift exists today.

## Not applicable here, recorded so nobody re-derives it

FTB Quests on MC 26.x stores its book as **JSON5**, not SNBT, and `lang/<locale>` became a directory.
Sky Frogs is on the previous major version and is not a format reference for anything 26.x. That cost
a full round trip on the pack side. This repo has no FTB Quests, so it only matters if a future mod
here reads that format.

## Where things live

| Thing | Path |
| --- | --- |
| devbridge mod, spec, own repo | `F:\devbridge\` |
| gamebridge CLI and README | `F:\minecraft-repos\mc-pack-toolkit\gamebridge\` |
| The pack-side brief this answers | `F:\minecraft-repos\trashlands\docs\handoff_gamebridge.md` |
| The verifier built from it | `F:\minecraft-repos\trashlands\tools\verify_quests.py` |
| This repo's devbridge wiring | `build.gradle`, the `client` run block |
| Port registry | `~/.claude/port_registry.yaml`, via `ports` |
