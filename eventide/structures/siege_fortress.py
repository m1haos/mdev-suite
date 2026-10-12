"""Builds the siege fortress for Eventide as a WorldEdit schematic and as a vanilla structure file.

The origin is the middle of the yard, one block above the floor: that is where the event spot lands.
Waves, guards and caches choose their place by the highest block inside a square of `spread` blocks
around the spot, so that square is kept flat and open. A roof or a pillar there would put mobs on top of it.
"""

import gzip
import random
import struct
from pathlib import Path

HALF = 23
Y_MIN, Y_MAX = -4, 15
SPAWN_HALF = 14
DATA_VERSION = 4440  # Minecraft 1.21.8; WorldEdit and Paper upgrade older data on load
AIR = "minecraft:air"

rng = random.Random(1936)
blocks = {}


def put(x, y, z, state):
    blocks[(x, y, z)] = state


def at(x, y, z):
    return blocks.get((x, y, z), AIR)


def mix(*weighted):
    states, weights = zip(*weighted)
    return rng.choices(states, weights)[0]


def wall():
    return mix(("minecraft:stone_bricks", 62), ("minecraft:mossy_stone_bricks", 16),
               ("minecraft:cracked_stone_bricks", 22))


def rubble():
    return mix(("minecraft:cobblestone", 40), ("minecraft:mossy_cobblestone", 20),
               ("minecraft:gravel", 20), ("minecraft:cracked_stone_bricks", 20))


def stairs(facing, half="bottom"):
    return f"minecraft:stone_brick_stairs[facing={facing},half={half},shape=straight,waterlogged=false]"


def ring(x, z):
    return max(abs(x), abs(z))


def in_tower(x, z):
    return 15 <= abs(x) <= 21 and 15 <= abs(z) <= 21


CELLS = [(x, z) for x in range(-HALF, HALF + 1) for z in range(-HALF, HALF + 1)]


def foundation():
    for x, z in CELLS:
        solid = ring(x, z) <= 18 or in_tower(x, z)
        for y in range(-4, -1):
            put(x, y, z, mix(("minecraft:stone", 3), ("minecraft:cobblestone", 2), ("minecraft:stone_bricks", 2))
                if solid else "minecraft:dirt")
        if solid:
            continue
        ground = mix(("minecraft:grass_block", 55), ("minecraft:coarse_dirt", 20), ("minecraft:gravel", 15),
                     ("minecraft:podzol", 10))
        if abs(x) <= 1 and z > 18:
            ground = mix(("minecraft:dirt_path", 60), ("minecraft:gravel", 40))
        put(x, -1, z, ground)
        if ground == "minecraft:grass_block" and rng.random() < 0.12:
            put(x, 0, z, "minecraft:short_grass")


def yard():
    for x, z in CELLS:
        c = ring(x, z)
        if c > 15:
            continue
        if c <= 1:
            floor = "minecraft:red_terracotta"
        elif c <= 3:
            floor = mix(("minecraft:polished_andesite", 75), ("minecraft:andesite", 25))
        elif c == 5:
            floor = mix(("minecraft:chiseled_stone_bricks", 70), ("minecraft:stone_bricks", 30))
        elif abs(x) <= 1 and z > 5:
            floor = mix(("minecraft:polished_andesite", 40), ("minecraft:stone_bricks", 35), ("minecraft:gravel", 25))
        else:
            floor = mix(("minecraft:stone_bricks", 34), ("minecraft:cobblestone", 24), ("minecraft:gravel", 12),
                        ("minecraft:coarse_dirt", 10), ("minecraft:mossy_cobblestone", 10),
                        ("minecraft:cracked_stone_bricks", 10))
            if c > 6 and rng.random() < 0.03:
                floor = "minecraft:blackstone"
        put(x, -1, z, floor)


def curtain_walls():
    for x, z in CELLS:
        c = ring(x, z)
        if not 16 <= c <= 18:
            continue
        for y in range(-1, 6):
            put(x, y, z, wall())
        if c == 18 and (x + z) % 2 == 0:
            put(x, 6, z, wall())


def towers():
    for sx in (-1, 1):
        for sz in (-1, 1):
            for a in range(15, 22):
                for b in range(15, 22):
                    for y in range(-1, 12):
                        put(sx * a, y, sz * b, wall())
                    if (a in (15, 21) or b in (15, 21)) and (a + b) % 2 == 0:
                        put(sx * a, 12, sz * b, wall())
            for a in range(16, 21):
                for b in range(16, 21):
                    for y in range(0, 11):
                        put(sx * a, y, sz * b, AIR)
                    put(sx * a, 5, sz * b, "minecraft:spruce_planks")

            ladder = f"minecraft:ladder[facing={'west' if sx > 0 else 'east'},waterlogged=false]"
            for y in range(0, 12):
                put(sx * 20, y, sz * 18, ladder)
            put(sx * 18, 10, sz * 18, "minecraft:lantern[hanging=true,waterlogged=false]")

            for y in (6, 7):
                put(sx * 15, y, sz * 17, AIR)
                put(sx * 17, y, sz * 15, AIR)
            for y in (0, 1):
                put(sx * 16, y, sz * 14, AIR)
                put(sx * 16, y, sz * 15, AIR)

            for y in (2, 3, 8, 9):
                put(sx * 18, y, sz * 21, "minecraft:iron_bars")
                put(sx * 21, y, sz * 20, "minecraft:iron_bars")

            for y in (12, 13, 14):
                put(sx * 18, y, sz * 18, "minecraft:dark_oak_fence")
            put(sx * 18, 15, sz * 18, f"minecraft:red_banner[rotation={rng.randrange(16)}]")


def gate():
    for x in range(-2, 3):
        for z in (16, 17, 18):
            for y in range(0, 5):
                put(x, y, z, AIR)
            put(x, -1, z, mix(("minecraft:polished_andesite", 40), ("minecraft:stone_bricks", 35),
                              ("minecraft:gravel", 25)))
    for x in (-3, 3):
        for z in (16, 18):
            for y in range(0, 5):
                put(x, y, z, "minecraft:dark_oak_log[axis=y]")
        put(x, 2, 15, "minecraft:wall_torch[facing=north]")
    for x in range(-2, 3):
        for y in (3, 4):
            put(x, y, 17, "minecraft:iron_bars")

    put(0, 0, 21, "minecraft:spruce_planks")
    put(0, 0, 23, "minecraft:spruce_planks")
    for z in (21, 22, 23):
        put(0, 1, z, "minecraft:stripped_oak_log[axis=z]")
    put(0, 1, 20, "minecraft:iron_block")


def breach():
    for z in range(-7, 8):
        az = abs(z)
        for x in (16, 17, 18):
            if az <= 3:
                for y in range(0, 7):
                    put(x, y, z, AIR)
                for y in range(rng.choice([0, 1, 1, 2, 3])):
                    put(x, y, z, rubble())
            else:
                limit = rng.randint(1, 3) if az <= 5 else rng.randint(3, 5)
                for y in range(limit + 1, 7):
                    put(x, y, z, AIR)
        if az <= 4:
            for x in (15, 19, 20):
                if rng.random() < 0.35:
                    put(x, 0, z, rubble())


def slits():
    for p in (-12, -6, 6, 12):
        for y in (2, 3):
            put(p, y, -16, AIR)
            put(p, y, -17, AIR)
            put(p, y, -18, "minecraft:iron_bars")
            put(p, y, 16, AIR)
            put(p, y, 17, AIR)
            put(p, y, 18, "minecraft:iron_bars")
            put(-16, y, p, AIR)
            put(-17, y, p, AIR)
            put(-18, y, p, "minecraft:iron_bars")
    for p in (-12, 12):
        for y in (2, 3):
            put(16, y, p, AIR)
            put(17, y, p, AIR)
            put(18, y, p, "minecraft:iron_bars")


def ramp(z, xs, facing):
    for step, x in enumerate(xs):
        for y in range(step):
            put(x, y, z, "minecraft:stone_bricks")
        put(x, step, z, stairs(facing))


def ramps_and_ladders():
    ramp(-15, range(-9, -15, -1), "west")
    ramp(15, range(9, 15), "east")
    for x in (-5, 7):
        for y in range(0, 7):
            put(x, y, -19, "minecraft:ladder[facing=north,waterlogged=false]")
    for y in range(0, 7):
        put(-19, y, 5, "minecraft:ladder[facing=west,waterlogged=false]")


def wall_dressing():
    for x in (-4, 4):
        put(x, 3, -15, "minecraft:red_wall_banner[facing=south]")
    for x in (-5, 5):
        put(x, 3, 15, "minecraft:red_wall_banner[facing=north]")
    for z in (-5, 5):
        put(-15, 3, z, "minecraft:red_wall_banner[facing=east]")
    for z in (-10, 10):
        put(15, 3, z, "minecraft:red_wall_banner[facing=west]")
    for x in (-8, 8):
        put(x, 3, -15, "minecraft:wall_torch[facing=south]")
        put(x, 3, 15, "minecraft:wall_torch[facing=north]")
    for z in (-9, 9):
        put(-15, 3, z, "minecraft:wall_torch[facing=east]")
        put(15, 3, z, "minecraft:wall_torch[facing=west]")


def supplies():
    spots = [(x, z) for x, z in CELLS
             if ring(x, z) == 15 and min(abs(x), abs(z)) <= 11
             and not (x == 15 and abs(z) <= 5) and not (z == 15 and abs(x) <= 4)
             and at(x, 0, z) == AIR]
    for x, z in rng.sample(spots, 16):
        if rng.random() < 0.5:
            put(x, 0, z, "minecraft:barrel[facing=up,open=false]")
        else:
            for y in range(rng.choice([1, 2])):
                put(x, y, z, "minecraft:hay_block[axis=y]")


def connect_bars_and_fences():
    loose = ("air", "ladder", "torch", "lantern", "banner", "grass", "cobweb", "stairs", "slab")
    for (x, y, z), state in list(blocks.items()):
        if state not in ("minecraft:iron_bars", "minecraft:dark_oak_fence"):
            continue
        sides = {}
        for name, (dx, dz) in {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}.items():
            other = at(x + dx, y, z + dz)
            base = other.split("[")[0]
            sides[name] = base == state or not any(word in base for word in loose)
        flags = ",".join(f"{name}={str(value).lower()}" for name, value in sorted(sides.items()))
        put(x, y, z, f"{state}[{flags},waterlogged=false]")


def build():
    foundation()
    yard()
    curtain_walls()
    towers()
    gate()
    breach()
    slits()
    ramps_and_ladders()
    wall_dressing()
    supplies()
    connect_bars_and_fences()
    return blocks


def tag_string(text):
    raw = text.encode("utf-8")
    return struct.pack(">H", len(raw)) + raw


def compound(items):
    body = b"".join(struct.pack(">B", tag_id) + tag_string(name) + payload for name, (tag_id, payload) in items)
    return 10, body + b"\x00"


def t_int(value):
    return 3, struct.pack(">i", value)


def t_short(value):
    return 2, struct.pack(">h", value)


def t_string(text):
    return 8, tag_string(text)


def t_bytes(data):
    return 7, struct.pack(">i", len(data)) + data


def t_ints(values):
    return 11, struct.pack(">i", len(values)) + struct.pack(f">{len(values)}i", *values)


def t_list(tag_id, payloads):
    return 9, struct.pack(">Bi", tag_id, len(payloads)) + b"".join(payloads)


def write_nbt(path, root_name, root):
    with gzip.open(path, "wb", compresslevel=9) as out:
        out.write(struct.pack(">B", root[0]) + tag_string(root_name) + root[1])


def varint(number):
    out = bytearray()
    while True:
        low = number & 0x7F
        number >>= 7
        if number:
            out.append(low | 0x80)
        else:
            out.append(low)
            return bytes(out)


def split_state(state):
    if "[" not in state:
        return state, {}
    name, rest = state.split("[", 1)
    return name, dict(pair.split("=", 1) for pair in rest.rstrip("]").split(","))


def volume():
    for y in range(Y_MIN, Y_MAX + 1):
        for z in range(-HALF, HALF + 1):
            for x in range(-HALF, HALF + 1):
                yield x, y, z, at(x, y, z)


def write_schematic(path):
    palette, data = {}, bytearray()
    for _, _, _, state in volume():
        data += varint(palette.setdefault(state, len(palette)))
    write_nbt(path, "Schematic", compound([
        ("Version", t_int(2)),
        ("DataVersion", t_int(DATA_VERSION)),
        ("Metadata", compound([("Name", t_string("Siege fortress")), ("Author", t_string("M/Dev Studio"))])),
        ("Width", t_short(2 * HALF + 1)),
        ("Height", t_short(Y_MAX - Y_MIN + 1)),
        ("Length", t_short(2 * HALF + 1)),
        ("Offset", t_ints([-HALF, Y_MIN, -HALF])),
        ("PaletteMax", t_int(len(palette))),
        ("Palette", compound([(state, t_int(index)) for state, index in palette.items()])),
        ("BlockData", t_bytes(bytes(data))),
    ]))


def write_structure(path):
    palette, entries = {}, []
    for x, y, z, state in volume():
        index = palette.setdefault(state, len(palette))
        position = t_list(3, [struct.pack(">i", x + HALF), struct.pack(">i", y - Y_MIN), struct.pack(">i", z + HALF)])
        entries.append(compound([("pos", position), ("state", t_int(index))])[1])
    palette_entries = []
    for state in palette:
        name, props = split_state(state)
        items = [("Name", t_string(name))]
        if props:
            items.append(("Properties", compound([(key, t_string(value)) for key, value in props.items()])))
        palette_entries.append(compound(items)[1])
    write_nbt(path, "", compound([
        ("DataVersion", t_int(DATA_VERSION)),
        ("size", t_list(3, [struct.pack(">i", n) for n in (2 * HALF + 1, Y_MAX - Y_MIN + 1, 2 * HALF + 1)])),
        ("palette", t_list(10, palette_entries)),
        ("blocks", t_list(10, entries)),
        ("entities", t_list(0, [])),
    ]))


if __name__ == "__main__":
    build()
    folder = Path(__file__).parent
    write_schematic(folder / "siege-fortress.schem")
    write_structure(folder / "siege-fortress.nbt")
