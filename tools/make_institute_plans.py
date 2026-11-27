"""The floor plans of the M.E.G. institute in the Frontrooms (docs/project/1-gameplay/README.md, "研究所改版"), drawn
with rectangles and written out as InstitutePlans.java (one string a row, one character a block column). The building
itself (walls, windows, stairs, the atrium, the facade) is made from these by frontrooms/InstituteLayout.java.

The look follows the research facility at the end of the film (A24's Backrooms, 2026): white walls over a mustard
wainscot, dark observation windows, a hospital wing with mint walls; around it a grand atrium three floors high.

Legend (also in InstituteLayout):
  E outside wall   # inside wall   . floor   , work area (players may build)   ; floor of a dark room (no lights)
  + floor of the medical wing (mint walls)   _ atrium floor (open up to the skylight)   ' ' the atrium's void (upper floors)
  c the specimen chamber   v railing   s stairs (see InstituteLayout.FLIGHTS)   P pillar   w glass   W observation window
  o doorway in glass   g main gate   1 3 5 blast doors   a arrivals   k y u n q people   B S C M Q counters   L lockers
  r weapon lockers   b f bed head and foot   D B1 pads   d lobby pad   h hatch   l ladder   H shelves   F files
  T K V A R X N Y U Z workstations (V the workshop bench)

Run: python tools/make_institute_plans.py   (prints the plans; --write also writes the Java file)
"""
import sys
from pathlib import Path

W, D, MX, MZ = 96, 72, -48, -36
JAVA = Path(__file__).resolve().parent.parent / "src/main/java/dev/noclip/backrooms/frontrooms/InstitutePlans.java"


def new(ch="#"):
    return [[ch] * W for _ in range(D)]


def rect(p, x0, z0, x1, z1, ch):
    for z in range(z0, z1 + 1):
        for x in range(x0, x1 + 1):
            p[z - MZ][x - MX] = ch


def put(p, x, z, ch):
    p[z - MZ][x - MX] = ch


def get(p, x, z):
    return p[z - MZ][x - MX]


def outline(p, x0, z0, x1, z1, ch):
    for x in range(x0, x1 + 1):
        put(p, x, z0, ch)
        put(p, x, z1, ch)
    for z in range(z0, z1 + 1):
        put(p, x0, z, ch)
        put(p, x1, z, ch)


def outside(p):
    outline(p, MX, MZ, MX + W - 1, MZ + D - 1, "E")


# Ground floor ----------------------------------------------------------------------------------------------------------

def ground():
    p = new()
    outside(p)
    # The atrium hall: a ring of terrazzo around the atrium, the specimen chamber in the middle.
    rect(p, -24, -24, 23, 23, ".")
    rect(p, -12, -12, 11, 11, "_")
    rect(p, -4, -4, 3, 3, "c")
    for x, z in ((-18, -18), (17, -18), (-18, 17), (17, 17)):
        put(p, x, z, "P")
    # Reception: the commander behind a long desk, facing the way in.
    rect(p, -4, 15, 3, 15, "C")
    put(p, 0, 14, "k")
    # The buyer's and the supplier's windows on either side of the atrium.
    rect(p, -19, -4, -19, 3, "B")
    put(p, -20, 0, "y")
    rect(p, 18, -4, 18, 3, "S")
    put(p, 19, 0, "u")
    # The pad back to Level 0.
    rect(p, -9, 19, -8, 20, "d")
    # Stairs up on both sides (InstituteLayout.FLIGHTS): the first flight rises north from z 10 to z 5.
    rect(p, -24, 5, -22, 10, "s")
    rect(p, 21, 5, 23, 10, "s")
    # The entrance hall and the main gate.
    rect(p, -10, 24, 9, 34, ".")
    rect(p, -3, 35, 2, 35, "g")

    # West wing ------------------------------------------------------------------------------------------------
    rect(p, -47, -2, -25, 1, ".")                      # corridor into the atrium hall
    rect(p, -47, -35, -26, -15, ",")                   # the work area
    for i, ch in enumerate("TKVAR"):
        put(p, -45 + 3 * i, -35, ch)
    rect(p, -25, -20, -25, -17, ".")                   # its door onto the atrium hall
    # Dormitories off the corridor: glass fronts, two beds each.
    for x0, x1 in ((-47, -43), (-41, -37), (-35, -31), (-29, -26)):
        rect(p, x0, -13, x1, -4, ".")
        for x in range(x0, x1 + 1):
            put(p, x, -3, "w")
        put(p, x0 + 2, -3, "o")
        for bx in (x0 + 1, x0 + 3):
            if bx <= x1:
                put(p, bx, -13, "b")
                put(p, bx, -12, "f")
    # The locker room, south of the corridor.
    rect(p, -47, 3, -26, 12, ".")
    rect(p, -36, 2, -35, 2, ".")
    for z in range(4, 12):
        put(p, -47, z, "L")
    for x in range(-46, -38):
        put(p, x, 12, "L")
    put(p, -47, 12, "L")
    # The receiving room (where people arrive, as in the film: white over mustard, a one-way window) and the dark
    # observation room behind its glass.
    rect(p, -40, 14, -26, 33, ".")
    rect(p, -47, 14, -42, 33, ";")
    for z in range(15, 33):
        put(p, -41, z, "W")
    put(p, -33, 24, "a")
    put(p, -30, 13, ".")                               # door to the locker room
    rect(p, -25, 28, -25, 29, ".")                     # door to the waiting room
    # The waiting room between the receiving room and the entrance hall.
    rect(p, -24, 25, -12, 34, ".")
    rect(p, -11, 27, -11, 30, ".")

    # East wing ------------------------------------------------------------------------------------------------
    rect(p, 24, -2, 46, 1, ".")
    # The medical wing (one quota): mint walls, the medic's counter, beds in glass bays.
    rect(p, 25, 3, 46, 34, "+")
    rect(p, 34, 2, 35, 2, "1")
    rect(p, 31, 7, 38, 7, "M")
    put(p, 35, 8, "n")
    for x in (29, 33, 37, 41):
        for z in range(28, 34):
            put(p, x, z, "w")
    for x in (27, 31, 35, 39, 43):
        put(p, x, 33, "b")
        put(p, x, 32, "f")
    put(p, 46, 4, "A")
    put(p, 46, 5, "Y")
    put(p, 30, 18, "P")
    put(p, 40, 18, "P")
    # The staff lounge south of the atrium hall, off the entrance hall.
    rect(p, 11, 25, 23, 34, ".")
    rect(p, 10, 27, 10, 30, ".")
    # The armoury and workshop (three quotas).
    rect(p, 25, -35, 46, -4, ".")
    rect(p, 25, -35, 46, -22, ",")
    for i, ch in enumerate("XNVK"):
        put(p, 28 + 4 * i, -35, ch)
    rect(p, 34, -3, 35, -3, "3")
    rect(p, 31, -8, 38, -8, "Q")
    put(p, 35, -9, "q")
    for z in range(-20, -9):
        put(p, 46, z, "r")

    # North: the deep wing (five quotas) and the stairs down to B1 ----------------------------------------------
    rect(p, -24, -35, 23, -26, ".")
    rect(p, 6, -25, 7, -25, "5")
    rect(p, -9, -30, 0, -27, "s")
    for x in range(-9, 1):
        put(p, x, -31, "v")
        put(p, x, -26, "v")
    for z in range(-31, -25):
        put(p, -10, z, "v")
    return p


# B1 and B2 -------------------------------------------------------------------------------------------------------------

def b1():
    p = new()
    # The transport hall, under the north half of the building; the stairs come down from the east.
    rect(p, -22, -34, 21, 8, ".")
    rect(p, -9, -30, 0, -27, "s")
    for x0 in (-11, -1, 9):
        rect(p, x0, -8, x0 + 1, -7, "D")
    for x, z in ((-16, -20), (15, -20), (-16, 2), (15, 2)):
        put(p, x, z, "P")
    put(p, -22, -20, "U")
    put(p, -22, -16, "Z")
    # The containment cells behind their observation windows (M.E.G.'s secret: empty for now).
    for x in range(-22, 22):
        put(p, x, 9, "W")
    for x0 in range(-22, 20, 7):
        rect(p, x0, 10, min(x0 + 5, 21), 17, ";")
    # The alcove of the hatch down to B2.
    rect(p, 22, -6, 28, 0, ".")
    put(p, 27, -3, "h")
    return p


def b2():
    p = new()
    rect(p, 18, -14, 36, 6, ";")
    put(p, 27, -3, "l")
    put(p, 27, -4, "#")
    for x in range(19, 36, 2):
        put(p, x, -14, "H")
    for x, z in ((21, -8), (25, -8), (31, -8), (21, 2), (25, 2), (33, 3)):
        put(p, x, z, "F")
    return p


# Upper floors ----------------------------------------------------------------------------------------------------------

def upper(level):
    """Floors 2 and 3: balconies round the atrium; offices, labs and a control room in the wings."""
    p = new()
    outside(p)
    rect(p, -24, -24, 23, 23, ".")
    outline(p, -13, -13, 12, 12, "v")
    rect(p, -12, -12, 11, 11, " ")
    for x, z in ((-18, -18), (17, -18), (-18, 17), (17, 17)):
        put(p, x, z, "P")
    if level == 2:
        # The first flight arrives here at z 5; its opening is fenced on the open sides.
        for x0 in (-24, 21):
            rect(p, x0, 6, x0 + 2, 10, "s")
            rail = x0 + 3 if x0 < 0 else x0 - 1
            for z in range(6, 12):
                put(p, rail, z, "v")
            for x in range(x0, x0 + 3):
                put(p, x, 11, "v")
            # The second flight rises north from z -1 to z -6.
            rect(p, x0, -6, x0 + 2, -1, "s")
    else:
        for x0 in (-24, 21):
            rect(p, x0, -5, x0 + 2, -1, "s")
            rail = x0 + 3 if x0 < 0 else x0 - 1
            for z in range(-5, 1):
                put(p, rail, z, "v")
            for x in range(x0, x0 + 3):
                put(p, x, 0, "v")
    # The wings: a corridor down the middle, rooms with glass fronts either side.
    for sign in (-1, 1):
        x_in, x_out = (-26, -47) if sign < 0 else (25, 46)
        lo, hi = min(x_in, x_out), max(x_in, x_out)
        rect(p, lo, -2, hi, 1, ".")
        wall = -25 if sign < 0 else 24
        rect(p, wall, -1, wall, 0, ".")
        split = -37 if sign < 0 else 36
        for z0, z1, front in ((-35, -4, -3), (3, 34, 2)):
            dark = level == 3 and z0 > 0
            rect(p, lo, z0, hi, z1, ";" if dark else ".")
            for z in range(z0, z1 + 1):
                put(p, split, z, "#")
            for x in range(lo, hi + 1):
                put(p, x, front, "W" if dark else "w")
            for x in (lo + 4, hi - 4):
                put(p, x, front, "o")
        if level == 2:
            for x in range(lo, hi + 1):
                put(p, x, 18, "#")
            put(p, lo + 5, 18, ".")
            put(p, hi - 5, 18, ".")
    # North: the control room; south: the conference rooms over the entrance hall.
    rect(p, -24, -35, 23, -26, ".")
    rect(p, -1, -25, 0, -25, ".")
    rect(p, -24, 25, 23, 34, ".")
    rect(p, -1, 24, 0, 24, ".")
    for x in range(-24, 24):
        put(p, x, 29 if level == 2 else 30, "w")
    for x in (-16, -1, 0, 15):
        put(p, x, 29 if level == 2 else 30, "o")
    return p


PLANS = {"B2": b2(), "B1": b1(), "F1": ground(), "F2": upper(2), "F3": upper(3)}


def show():
    for name, p in PLANS.items():
        print(name)
        for row in p:
            print("".join(row))
        print()


def write():
    lines = ["package dev.noclip.backrooms.frontrooms;", "",
             "/** Generated by tools/make_institute_plans.py: change the plans there, not here. */",
             "final class InstitutePlans {"]
    for name, p in PLANS.items():
        lines.append(f"\tstatic final String[] {name} = {{")
        for row in p:
            lines.append('\t\t\t"' + "".join(row) + '",')
        lines.append("\t};")
    lines += ["", "\tprivate InstitutePlans() {", "\t}", "}", ""]
    JAVA.write_text("\n".join(lines), encoding="utf8")
    print("wrote", JAVA)


if __name__ == "__main__":
    show()
    if "--write" in sys.argv:
        write()
