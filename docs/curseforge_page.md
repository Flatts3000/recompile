# CurseForge project page

Source of truth for the Recompile CurseForge listing (project `1625740`, slug `recompile`).
Edit here, then paste to CurseForge. The pack has its own page copy at
`../trashlands/docs/curseforge_page.md`.

**Voice for this page: what it is like to play, not what it contains** (owner, 2026-08-30). This
read as a spec sheet - a thirteen-row machine table, exhaustive bullets, every number the mod has -
and a page that lists a mod does not tell anyone what playing it feels like. Winning ModJam pages are
short, image-led, and written from inside the game.

**That is a reversal, and only of the shape.** The old rule said lists over paragraphs and detail over
prose. What survives it: no selling, no marketing verbs, nothing personified - "soil bordering
unhealed ground reverts to coarse dirt", never "the junkyard takes it back" - and every line still
has to be true of the current release. The reference is the `quest-voice` spec in
`../mc-pack-toolkit/quest-voice/voice_spec.md`.

**Detail did not get deleted, it got moved.** The guidebook ships in-game and the README carries the
long form. A CurseForge page is the first thirty seconds.

**Claim discipline.** Every line describes something in the current release. Designed-but-unbuilt
systems go under "Not in yet" or are left out. Check `docs/roadmap.md` phase status and the code
before restating a feature - the design docs describe the intended end state, not what ships.

---

## Project fields

| Field | Value |
|---|---|
| **Summary** | `A world buried under its own rubbish, where the recipes went in the bin with everything else. Tear found objects apart to relearn them, and heal the ground a tier at a time.` |
| **Categories** | Processing, World Gen, Technology |
| **License** | MIT |
| **Source / issues** | `https://github.com/Flatts3000/recompile` |

The first summary sold the premise ("...and reclaim the wasteland back to life"). The second stated
the mechanic and nothing else, which is the opposite failure: it named the material source and left
out what the mod is *about*. This one leads with the loss and still names the two axes, teardown and
reclamation.

**Written for ModJam 2026 ("Echoes of the Past"), and that is a deliberate reframing rather than a
rewrite of the mod.** Theme Fit is one of the three judging pillars, every worked example the
organisers gave is literal history, and ours is interpretive - so the interpretation has to be on the
page instead of in a design doc. The literal half was already built and buried under "Also in": six
recovered masterworks and a shelf of found objects. See
`../mod-jam-2026/round_1_rewards_analysis.md`.

---

---

## Images

**The description carries images now, and it did not before.** Eighteen screenshots sat in the
gallery tab while the description was an unbroken wall of text - which is the half of a CurseForge
page a judge or a browsing player actually reads. The gallery is a tab you have to click.

They are served from this repo rather than from CurseForge's own CDN, because a gallery upload's URL
is only knowable after the upload and cannot be written down here in advance:

    https://raw.githubusercontent.com/Flatts3000/recompile/main/docs/cf%20image%20gallery/<file>

The directory name has a space in it, so the URL needs `%20`. **An image added to the description
must be committed and pushed before the link resolves** - an unpushed file renders as a broken image
on a live page.

<!-- PASTE MARKER - everything below this line goes in the CurseForge Description field, as-is -->

# Recompile

![Coarse dirt under mounds of garbage, horizon to horizon.](https://raw.githubusercontent.com/Flatts3000/recompile/main/docs/cf%20image%20gallery/01-garbage-world.jpg)

There is no ore, no trees and no water. The ground is coarse dirt, and everything standing on it was
thrown away by somebody who lived here first.

You start by pulling things out of the mounds with your hands. A block gives up one item at a time and
crumbles after a few pulls.

## The recipes went in the bin too

![Bulky waste opened with a prybar: a washing machine, a printer, a filing cabinet.](https://raw.githubusercontent.com/Flatts3000/recompile/main/docs/cf%20image%20gallery/06-bulky-waste-finds.png)

Buried in the mounds are whole objects. A prybar opens one and you get a washing machine, a printer, a
filing cabinet.

Take it apart at the Teardown Workbench and you get two things: what it was made of, and an idea of
how it was made. Four ideas about a mattress and you can make a mattress again.

That is the whole mod. The materials are the easy part. What you are actually recovering is how the
thing was made.

## Sorting is the job

![Scrap bins, each bound to one material, with barrels for the overflow.](https://raw.githubusercontent.com/Flatts3000/recompile/main/docs/cf%20image%20gallery/05-scrap-bins-and-barrels.png)

Bins bind to whatever you put in them first. Stand them next to each other and they become one
network, and everything you sort finds its own bin.

Later there are machines that do it while you are somewhere else. They have no screens. You drop
things on them.

## Ground you can stand on

![Grass spreading back, with the dump still standing behind it.](https://raw.githubusercontent.com/Flatts3000/recompile/main/docs/cf%20image%20gallery/07-machines-on-reclaimed-grass.png)

Grass does not grow here. A Grass Spreader puts it down, and the dump takes it back at the edges
unless something holds the line: plants, then wet farmland, then trees.

Trees hold it permanently. Getting a tree means a Tree Nursery, because no sapling can be found
anywhere in this world.

## Down the manhole

![A sewer run. Leachate down the middle, dry brick either side, silt in the corners.](https://raw.githubusercontent.com/Flatts3000/recompile/main/docs/cf%20image%20gallery/14-sewer-corridor.png)

A square of pale concrete with a rusted plate in it. The plate comes up with a prybar, and there is a
ladder under it.

Brick corridors with a channel of leachate down the middle. Mushrooms in the damp, silt in the
corners, mossy brick at the waterline. The rooms are lit and the corridors are not, because dark is
where things spawn.

![The sump: standing leachate, no lamp, and a drowned spawner on the walkway.](https://raw.githubusercontent.com/Flatts3000/recompile/main/docs/cf%20image%20gallery/15-sewer-sump.png)

Every channel runs downhill to the same room. Leachate deep enough to go over your head, no lamp, and
a drowned spawner on the walkway. There is one echo shard down there in the silt, and it is the only
one in the world.

Turtles and frogs live in dens off the corridors. There are only ever as many as you find.

## Out past the yard

![Tailings impoundments with decant ponds, drums at the toe.](https://raw.githubusercontent.com/Flatts3000/recompile/main/docs/cf%20image%20gallery/17-radioactive-dump.png)

Keep going past the demolition yard and the ground turns sallow. Somebody buried the things nobody
would take: mill tailings in flat-topped heaps, steel drums with a trefoil on the side, and the
household objects that were quietly radioactive the whole time.

![Mill tailings, waste drums and uranium glass, with the finds behind.](https://raw.githubusercontent.com/Flatts3000/recompile/main/docs/cf%20image%20gallery/18-radioactive-museum.png)

A radium dial clock. A smoke detector. Thoriated welding rods. Uranium glass, which goes on a
pedestal rather than into a machine.

Nothing out here grows back. You strip a dump and move to the next one.

## The Nether is solid

The overworld is a dump you clear. The Nether is a dump you mine.

Every column is full, floor to ceiling. No caverns, no lava sea, nothing to fall off. Fortresses and
bastions are down there, buried in it, and the only way in is obsidian you made yourself out of the
slag your own furnace rakes off.

## Machines

![The machine tier, built by stacking components on a core.](https://raw.githubusercontent.com/Flatts3000/recompile/main/docs/cf%20image%20gallery/03-machine-wall.png)

You place a core and stack parts on it and it forms where it stands.

A trommel sorts. A separator divides. A pulverizer grinds. A kiln puts powder back together, which is
the only machine here that builds rather than breaks. A cupola melts scrap into iron, and rakes off
slag while it does.

## Things worth finding

![Recovered paintings over pedestals holding found objects.](https://raw.githubusercontent.com/Flatts3000/recompile/main/docs/cf%20image%20gallery/02-museum.png)

Six real paintings turn up in the rubbish, and a painting keeps its picture when you break it and hang
it again. A puzzle cube in nine pieces. A toy car, a gold coin, a wrapped present, an avocado.

Amber comes out of the mounds with something caught in it. Read four of the same creature and you can
make its spawn egg, which is the only way to get an animal here.

Pedestals take any item and turn it over slowly.

## Worth knowing

- Alpha, and in active development. Drop rates and recipe costs are first-pass.
- Worldgen changes need a new world. An existing save keeps the ground it generated with.
- The End is closed. The Nether is not.
- Works with **AE2**, **Powah**, **Simple Magnets** and **Ender IO**, each of which has something in
  this world it cannot otherwise start from. None of them are required.
- **JEI** and **Jade** plugins, and an in-game guidebook with 3D multiblock pages.

## Links

- **Source and issues:** <https://github.com/Flatts3000/recompile>
- **The modpack built on it:** <https://www.curseforge.com/minecraft/modpacks/trashlands>

Art sourced from elsewhere is public domain or CC0. Mod content is MIT.
