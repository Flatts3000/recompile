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

**Brought current to v0.18.0 (2026-09-04).** It had last been touched before v0.17.0, so it described
a game two releases old: no Garbage Vacuum, no Municipal Aquarium, no tire dumps. That is a failure of
the rule directly above it, and it is the failure mode that rule is least able to catch on its own - a
line does not stop being true when something new ships, it just stops being the whole picture, so
nothing reads as wrong. **Re-read this page on every minor release**, against `CHANGELOG.md`, not only
when a claim looks stale.

**Reordered 2026-09-05 to lead with the three things that pull people in now** (owner): the Garbage
Vacuum, the Scrap Hauler, and the structures. Nothing was deleted for it. Teardown is still what the
mod IS and still carries the summary, but it is no longer what a reader meets first, because the
opening premise hands off to clearing the piles rather than to a workbench. The structures were
scattered across six sections in world order; they are one run now, because a reader deciding whether
to install this is deciding about them as a group.

**Brought current to v0.19.0 (2026-09-05).** The Scrap Hauler section was written while the machine
was still sitting under `## Unreleased`, and carried a line saying so; cutting v0.19.0 the same day
made the line false, so it is gone. That is the shortest a "not yet" has ever lived here, and it only
worked because the release and the page moved together. A marker that outlives the release it
describes is worse than never having written one.

**Ahead of the release, as of 2026-09-10.** The lines saying rubble piles and tailings heaps grow back
(#424) describe `main`, not v0.20.0. Paste this page when the next release ships, not before, or the
live page claims something the downloadable jar does not do.

**The aquarium has a screenshot now** (2026-09-07, `26-municipal-aquarium.png`), shot against a
`runClient` over `gamebridge`. **The vacuum and the tire dump still do not**: frames for both were shot
in the same pass and the owner rejected them. Do not invent a filename to fill either gap - an image
link that does not resolve renders as alt text and a half-link on the live page. Shoot it, commit it,
push it, then add the link.

**What was wrong with the two that were cut is worth knowing before reshooting them.** The vacuum frame
was taken with the crosshair on distant mounds, so the intake happened far from the camera and the shot
showed a tool in hand rather than a tool working; aim down into a pile at close range and catch the
block in flight, which is what the animation is for. The tire frame had no naturally generated pile to
photograph - a 1-in-34-chunk rarity, but they are small, dark and swallowed by the mounds from any
altitude high enough to search from - so it used `/place feature recompile:tire_pile`, which runs the
real worldgen feature rather than building a pile by hand. That part is sound and worth reusing; what
failed is that it landed in a tight gap between mounds. Place it on open coarse dirt and shoot at eye
level.

**The cooling tower and smokestack frames were already in the gallery and had never been linked**,
which is a quieter version of the same failure: the images existed, the sections did not, so nothing
looked missing from either side. The Scrap Hauler's frame was shot for this pass with
`gamebridge`, against a `runClient`, and the first two attempts at it are worth recording because both
produce a picture that looks fine until you ask what it shows. A crop of an existing tour screenshot
had the camera above the machine, where it reads as a grey box rather than as a robot. Aiming the
replacement with fixed world coordinates left the machine at the edge of frame, because it had walked.
What works is `execute at @e[type=recompile:scrap_hauler,limit=1] run tp @s ^x ^y ^z` followed by
`facing entity`, with a POSITIVE local z - negative is behind the machine, which is how four
consecutive frames came back photographing its back.

---

## Project fields

| Field | Value |
|---|---|
| **Summary** | `A world buried under its own rubbish, where the recipes went in the bin with everything else. Vacuum the piles up or send a robot to do it, tear your finds apart for the parts that still work, and buy back what you have forgotten how to build.` |
| **Categories** | Processing, World Gen, Technology |
| **License** | MIT |
| **Source / issues** | `https://github.com/Flatts3000/recompile` |

The first summary sold the premise ("...and reclaim the wasteland back to life"). The second stated
the mechanic and nothing else, which is the opposite failure: it named the material source and left
out what the mod is *about*. The third led with the loss and named teardown and reclamation. This one
keeps the loss and the teardown and swaps reclamation for the things people actually arrive for,
because reclamation is a slow arc that reads as a chore in one line and the structures are the half a
browsing player can picture.

**It names the robot, and the draft written an hour earlier did not.** That draft left the Hauler out
on the grounds that it had not shipped, which was true when it was written and false by the time the
summary was pasted, because v0.19.0 went out in between. The cooling tower came out to make room, and
it is the right thing to drop: it is the one item on that list a reader has no picture of yet. 256
characters is the field's hard cap and this runs to 249, so there is no room to have kept both.

**Written for ModJam 2026 ("Echoes of the Past"), and that is a deliberate reframing rather than a
rewrite of the mod.** Theme Fit is one of the three judging pillars, every worked example the
organisers gave is literal history, and ours is interpretive - so the interpretation has to be on the
page instead of in a design doc. The literal half was already built and buried under "Also in": six
recovered masterworks and a shelf of found objects. See
`../mod-jam-2026/round_1_rewards_analysis.md`.

---

## Images

**No title and no hero image.** CurseForge puts the project name and the gallery carousel directly
above the description, so both were being shown twice. The page opens on the first line of prose.

**The description carries images now, and it did not before.** The screenshots all sat in the gallery
tab while the description was an unbroken wall of text - which is the half of a CurseForge page a
judge or a browsing player actually reads. The gallery is a tab you have to click.

**The gallery tab's contents are NOT tracked here, and this sentence used to imply they were.** It
gave a count ("eighteen screenshots"), which was true when written and then quietly went stale as
uploads happened; on 2026-09-05 it was read back as the live figure and produced a confident, wrong
statement about what the gallery was missing. The gallery had 23. Count it on the site - the public
`/gallery` tab lists every filename - or query it; never quote a number from this file.

They are served from this repo rather than from CurseForge's own CDN, because a gallery upload's URL
is only knowable after the upload and cannot be written down here in advance:

    https://raw.githubusercontent.com/Flatts3000/recompile/main/docs/cf_image_gallery/<file>

**The folder is `cf_image_gallery`, with no spaces, and that is the whole reason it was renamed.** It
used to be `cf image gallery`, so every URL here carried `%20`. CurseForge decodes that back to a
literal space before rendering, and markdown ends a URL at the first whitespace - so every image on the
live page broke, showing the alt text and a half-link. A path with no spaces cannot be mangled by
anything downstream.

**An image added to the description must be committed and pushed before its link resolves** - an
unpushed file renders as a broken image on a live page.

<!-- PASTE MARKER - everything below this line goes in the CurseForge Description field, as-is -->

There is no ore, no trees and no water. The ground is coarse dirt, and everything standing on it was
thrown away by somebody who lived here first.

You start by pulling things out of the mounds with your hands. A block gives up one item at a time and
crumbles after a few pulls.

## Then you stop doing it by hand

Hold right-click with a Garbage Vacuum and the piles in front of you leave the ground and fly into the
nozzle, about five a second. It takes whole blocks, not what is inside them, so you still sort them
afterwards.

Take a mound from the bottom and the rest comes down on top of you. That is the fast way to clear one.

It runs on power, and a bigger block costs more. Each one is rated for the waste it was built for:
copper handles household rubbish, iron adds the demolition yard, diamond the tailings out past it, and
only a netherite one will touch the Nether. Point one at something out of its league and it tells you
what the pile is instead of doing nothing.

It charges on a Charging Station with a generator touching it. Set the vacuum down on the station,
pick it back up when it is full. There is no screen and nothing can reach in and take it off the dock.

## And then you stop doing it at all

![A Scrap Hauler standing on a road at the edge of the sprawl, with mounds to the horizon.](https://raw.githubusercontent.com/Flatts3000/recompile/main/docs/cf_image_gallery/24-scrap-hauler.png)

Place a Hauler Depot, put a Scrap Hauler in its slot, press Deploy. It walks out to the nearest pile,
takes the block off the top of it, brings it back and drops it in the hold. Then it goes again.

It works a square of chunks around the Depot and you set how many from the Depot's screen. The
smallest is the three by three the Depot sits in the middle of.

It runs on sun out in the field and charges off the Depot while it is docked. Nothing in this world can
destroy it, which means the way it goes wrong is getting stuck rather than dying, and Recall brings it
back from wherever that is.

The hold is twenty-seven slots, and it pushes what is in it into any bins and barrels touching the
Depot. Park a Depot against your sorting wall and the wall fills itself.

There is only ever one of it. It is the item sitting in the Depot or the machine out in the field,
never both at once.

## The recipes went in the bin too

![Bulky waste opened with a prybar: a washing machine, a printer, a filing cabinet.](https://raw.githubusercontent.com/Flatts3000/recompile/main/docs/cf_image_gallery/06-bulky-waste-finds.png)

Buried in the mounds are whole objects. A prybar opens one and you get a washing machine, a printer, a
filing cabinet.

Take it apart at the Teardown Workbench and you get the part of it that still works. A washing
machine has a motor in it. A dead hauler has a solar panel. A broken hydroponics bay has a pump.

The materials are the easy part. There is an infinite amount of scrap out here and no amount of it
melts down into a working motor, which is why the motor is the thing worth having.

Knowing how to build it again is a separate problem, and the answer is money. Fix up a terminal, sell
what you make, and buy the recipe back.

## Sorting is the job

![Scrap bins, each bound to one material, with barrels for the overflow.](https://raw.githubusercontent.com/Flatts3000/recompile/main/docs/cf_image_gallery/05-scrap-bins-and-barrels.png)

A bin takes the first thing you put in it and nothing else after that. Stand a few side by side and
everything you sort goes where it belongs.

Later you throw the garbage at a machine and walk away, and it is sorted when you come back.

## Things are standing in the dump

Somebody built here before it was a dump, and some of it is still up. None of it is decoration: every
one of these is the only source of something.

### Down the manhole

![A sewer run. Leachate down the middle, dry brick either side, silt in the corners.](https://raw.githubusercontent.com/Flatts3000/recompile/main/docs/cf_image_gallery/14-sewer-corridor.png)

A square of pale concrete with a rusted plate in it. The plate comes up with a prybar, and there is a
ladder under it.

Brick corridors with a channel of leachate down the middle. Mushrooms in the damp, silt in the
corners, mossy brick at the waterline. The rooms are lit. The corridors are not.

![The sump: standing leachate, no lamp, and a drowned spawner on the walkway.](https://raw.githubusercontent.com/Flatts3000/recompile/main/docs/cf_image_gallery/15-sewer-sump.png)

Every channel runs downhill to the same room. Leachate deep enough to go over your head, no lamp, and
a drowned spawner on the walkway. There is an echo shard in the silt at the bottom of every sewer, and
nowhere else in the world.

Turtles and frogs live down there, in rooms of sand and mud off the corridors.

### The aquarium closed a long time ago

Out in the demolition yard there is a public aquarium with the water let out of it. Seven rooms:
a forecourt, a lobby, a gallery of tank bays, a centrepiece tank, a guardian tank, a filtration hall
half under water, and the back of house. Leachate has pooled in the floors and the glass is cracked
where the tanks leaked.

It is the only prismarine, coral, sponge and sea lantern in the world, and there is a heart of the sea
still on its stand in the middle.

![The Municipal Aquarium in the demolition yard: prismarine cladding, sea lanterns on the roof, building husks and a smokestack behind it.](https://raw.githubusercontent.com/Flatts3000/recompile/main/docs/cf_image_gallery/26-municipal-aquarium.png)

One tank still holds water, and there is a guardian in it. That is not decoration. Every prismarine
block is made from what a guardian drops, so the tank is the only reason the whole family is renewable
rather than a fixed stock. Bucket it dry and you have ended that.

Put a dead coral in a Hydroponics Bay and it comes back alive. The bay does not use up what you put in
it, so one of each colour is all you will ever need.

### The chimneys over the yard

![A brick smokestack standing over the demolition yard.](https://raw.githubusercontent.com/Flatts3000/recompile/main/docs/cf_image_gallery/23-smokestack.png)

Brick chimneys stand over the demolition yard, thirty to forty-eight blocks of them, and some are
still smoking. There is no door and no ladder, because a chimney is a flue and not a room.

A husk spawner sits at the foot of each one and its range reaches out past the brick, so passing a
chimney is an encounter rather than a view.

### Out past the yard

![Tailings impoundments with decant ponds, drums at the toe.](https://raw.githubusercontent.com/Flatts3000/recompile/main/docs/cf_image_gallery/17-radioactive-dump.png)

Keep going past the demolition yard and the ground turns sallow. Somebody buried the things nobody
would take: mill tailings in flat-topped heaps, steel drums with the radiation symbol stencilled on them, and the
household objects that were quietly radioactive the whole time.

![Mill tailings, waste drums and uranium glass, with the finds behind.](https://raw.githubusercontent.com/Flatts3000/recompile/main/docs/cf_image_gallery/18-radioactive-museum.png)

A radium dial clock. A smoke detector. Thoriated welding rods. Uranium glass.

The heaps grow back. Strip one and it refills, a block at a time out of the sky, the way the mounds
at home do. What does not come back is the ground: the stain under a heap never takes grass, so this
is the one place you can work forever and never finish.

### The cooling tower

![A decrepit cooling tower in the radioactive dump.](https://raw.githubusercontent.com/Flatts3000/recompile/main/docs/cf_image_gallery/21-cooling-tower.png)

There is a cooling tower out here, with its shell torn open at the top. It stands on legs over an open
basin and narrows to a throat about three quarters of the way up, the way one does when it is built
out of straight rods.

![Inside the tower shell, looking up from the basin.](https://raw.githubusercontent.com/Flatts3000/recompile/main/docs/cf_image_gallery/22-cooling-tower-inside.jpg)

You can walk in under the legs. There is a spawner down in the basin.

### A few thousand tires

Somebody tipped tires across the sprawl in circular heaps, and some of them are burning. A tire fire
does not go out in rain and does not go out with time. It does not eat the tires either, and there is
nothing on bare dump ground for it to spread to. Water still puts it out.

Break a tire by hand and you get the tire. Break it with a Scrap Knife and you get the rubber. Carry it
home and take it apart at the workbench instead and you get more, plus the steel belts out of the
middle.

Nothing regrows a tire, and tire dumps are the exception now rather than the rule - garbage mounds,
rubble piles and tailings heaps all refill. Strip a tire dump and it is gone.

### The Nether is solid

The overworld is a dump you clear. The Nether is a dump you mine.

Every column is full, floor to ceiling. No caverns, no lava sea, nothing to fall off. Fortresses and
bastions are down there, buried in it, and the only way in is obsidian you made yourself out of the
slag your own furnace rakes off.

## Ground you can stand on

![Grass spreading back, with the dump still standing behind it.](https://raw.githubusercontent.com/Flatts3000/recompile/main/docs/cf_image_gallery/07-machines-on-reclaimed-grass.png)

Grass does not grow here. A Grass Spreader puts it down, and it goes back to coarse dirt at the edges
unless something holds the line: plants, then wet farmland, then trees.

Trees hold it permanently. Getting a tree means a Tree Nursery, because no sapling can be found
anywhere in this world.

Once there is grass, leave bait on it and walk away. Nothing comes while you are standing there. What
turns up depends on the bait and on what is growing around it.

## Machines

![The machine tier, built by stacking components on a core.](https://raw.githubusercontent.com/Flatts3000/recompile/main/docs/cf_image_gallery/03-machine-wall.png)

The big machines are multiblocks. You place a core, stack parts on it, and it forms where it stands.

A trommel sorts. A separator divides. A pulverizer grinds. Beside them sit the furnaces: a kiln
presses powder back into something solid, and a cupola melts scrap down into iron and rakes off slag
while it does.

## Things worth finding

![Recovered paintings over pedestals holding found objects.](https://raw.githubusercontent.com/Flatts3000/recompile/main/docs/cf_image_gallery/02-museum.png)

You can pull a Van Gogh out of a bin bag. Six real paintings are down there, and the one you find is
the one that goes on your wall. A puzzle cube in nine pieces, never together. A toy car, a gold coin,
a wrapped present, an avocado.

Amber turns up with something caught in it. Read four of the same creature and you have its spawn
egg.

## Worth knowing

- Alpha, and still being worked on. The numbers are not balanced yet.
- Start a new world when you update. New regions do not appear in one you have already been to.
- The End is closed. The Nether is not.
- Works with **AE2**, **Powah** and **Ender IO**. AE2 and Powah each need something this world does
  not hand out, and each has a way in here. Ender IO runs as it is. None of them are required.
- **JEI** and **Jade** plugins, and an in-game guidebook with 3D multiblock pages.

## Links

- **Source and issues:** <https://github.com/Flatts3000/recompile>
- **The modpack built on it:** <https://www.curseforge.com/minecraft/modpacks/trashlands>

Art sourced from elsewhere is public domain or CC0. Mod content is MIT.
