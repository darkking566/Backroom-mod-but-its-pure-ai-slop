"""
Pure-Python Wavefront OBJ + MTL generator for full-scale Level 1 multi-block vehicles.
Exports triangulated meshes directly to:
  src/main/resources/assets/noclip_backrooms/models/blender_props/
    - abandoned_car.obj / .mtl   (2x2x4 blocks: 1990s dust-worn sedan)
    - abandoned_van.obj / .mtl   (2x2x5 blocks: 1990s utility cargo van)

Coordinate convention (matching Level1VehicleRenderer / BlenderPropRenderer):
  - Origin (0, 0, 0) = bottom-center of the master block (0, 0, 0).
  - Facing NORTH in Minecraft:
      * Front of vehicle = -Z, Rear = +Z
      * Width along +X: local dx in {0, 1} -> X in [-0.5 .. +1.5], center X = 0.5
      * Height along +Y: floor = 0.0, dy in {0, 1}
  - Material buckets recognized by BlenderWeaponMesh:
      * MAT_*     -> shaded triangles (Diffuse color baked per vertex from MTL Kd)
      * EMIT_HOT  -> headlight lenses / inner glow
      * EMIT_ALT  -> taillight / brake lenses
      * EMIT_MAIN -> dome / dashboard faint glow
"""
import math
import os

REPO_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
OUT_DIR = os.path.join(
    REPO_ROOT, "src", "main", "resources", "assets", "noclip_backrooms", "models", "blender_props"
)
os.makedirs(OUT_DIR, exist_ok=True)


class MeshBuilder:
    def __init__(self, palette):
        self.palette = palette
        self.vertices = []
        self.normals = []
        self.faces = {}  # mat_name -> list of ((v0, v1, v2), n_idx)

    def _add_v(self, x, y, z):
        self.vertices.append((x, y, z))
        return len(self.vertices)

    def _add_n(self, nx, ny, nz):
        length = math.sqrt(nx * nx + ny * ny + nz * nz) or 1.0
        self.normals.append((nx / length, ny / length, nz / length))
        return len(self.normals)

    def add_quad(self, p0, p1, p2, p3, mat):
        """Adds a quad split into 2 CCW triangles with outward geometric normal."""
        ax, ay, az = p1[0] - p0[0], p1[1] - p0[1], p1[2] - p0[2]
        bx, by, bz = p2[0] - p0[0], p2[1] - p0[1], p2[2] - p0[2]
        nx = ay * bz - az * by
        ny = az * bx - ax * bz
        nz = ax * by - ay * bx
        n_idx = self._add_n(nx, ny, nz)
        v0 = self._add_v(*p0)
        v1 = self._add_v(*p1)
        v2 = self._add_v(*p2)
        v3 = self._add_v(*p3)
        bucket = self.faces.setdefault(mat, [])
        bucket.append(((v0, v1, v2), n_idx))
        bucket.append(((v0, v2, v3), n_idx))

    def add_tri(self, p0, p1, p2, mat):
        ax, ay, az = p1[0] - p0[0], p1[1] - p0[1], p1[2] - p0[2]
        bx, by, bz = p2[0] - p0[0], p2[1] - p0[1], p2[2] - p0[2]
        n_idx = self._add_n(ay * bz - az * by, az * bx - ax * bz, ax * by - ay * bx)
        v0, v1, v2 = self._add_v(*p0), self._add_v(*p1), self._add_v(*p2)
        self.faces.setdefault(mat, []).append(((v0, v1, v2), n_idx))

    def add_box(self, x0, y0, z0, x1, y1, z1, mat):
        """Axis-aligned box with outward normals."""
        # +X
        self.add_quad((x1, y0, z1), (x1, y0, z0), (x1, y1, z0), (x1, y1, z1), mat)
        # -X
        self.add_quad((x0, y0, z0), (x0, y0, z1), (x0, y1, z1), (x0, y1, z0), mat)
        # +Y (top)
        self.add_quad((x0, y1, z1), (x1, y1, z1), (x1, y1, z0), (x0, y1, z0), mat)
        # -Y (bottom)
        self.add_quad((x0, y0, z0), (x1, y0, z0), (x1, y0, z1), (x0, y0, z1), mat)
        # +Z (rear)
        self.add_quad((x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1), mat)
        # -Z (front)
        self.add_quad((x1, y0, z0), (x0, y0, z0), (x0, y1, z0), (x1, y1, z0), mat)

    def add_frustum_z(self, x0_bot, x1_bot, z0_bot, z1_bot, y0,
                      x0_top, x1_top, z0_top, z1_top, y1, mat):
        """Tapered prism (great for sloped sedan/van cabins & windshields)."""
        b0 = (x0_bot, y0, z0_bot)
        b1 = (x1_bot, y0, z0_bot)
        b2 = (x1_bot, y0, z1_bot)
        b3 = (x0_bot, y0, z1_bot)
        t0 = (x0_top, y1, z0_top)
        t1 = (x1_top, y1, z0_top)
        t2 = (x1_top, y1, z1_top)
        t3 = (x0_top, y1, z1_top)
        self.add_quad(t3, t2, t1, t0, mat)  # roof
        self.add_quad(b0, b1, b2, b3, mat)  # base
        self.add_quad(b1, b0, t0, t1, mat)  # front slope (-Z)
        self.add_quad(b3, b2, t2, t3, mat)  # rear slope (+Z)
        self.add_quad(b0, b3, t3, t0, mat)  # left (-X)
        self.add_quad(b2, b1, t1, t2, mat)  # right (+X)

    def add_wheel_x(self, cx_left, width, cy, cz, radius, segments, mat_tire, mat_rim):
        """Cylindrical wheel aligned along X axis (left face at cx_left, right at cx_left + width)."""
        x0 = cx_left
        x1 = cx_left + width
        ring0, ring1 = [], []
        rim_r = radius * 0.56
        rim0, rim1 = [], []
        for i in range(segments):
            ang = (2.0 * math.pi * i) / segments
            dy = math.cos(ang)
            dz = math.sin(ang)
            ring0.append((x0, cy + dy * radius, cz + dz * radius))
            ring1.append((x1, cy + dy * radius, cz + dz * radius))
            rim0.append((x0 - 0.01, cy + dy * rim_r, cz + dz * rim_r))
            rim1.append((x1 + 0.01, cy + dy * rim_r, cz + dz * rim_r))
        c0 = (x0, cy, cz)
        c1 = (x1, cy, cz)
        rc0 = (x0 - 0.015, cy, cz)
        rc1 = (x1 + 0.015, cy, cz)
        for i in range(segments):
            j = (i + 1) % segments
            # Tread band
            self.add_quad(ring0[j], ring0[i], ring1[i], ring1[j], mat_tire)
            # Sidewalls
            self.add_tri(c0, ring0[j], ring0[i], mat_tire)
            self.add_tri(c1, ring1[i], ring1[j], mat_tire)
            # Alloy hubcap disc
            self.add_tri(rc0, rim0[j], rim0[i], mat_rim)
            self.add_tri(rc1, rim1[i], rim1[j], mat_rim)

    def write(self, prefix):
        obj_path = os.path.join(OUT_DIR, f"{prefix}.obj")
        mtl_path = os.path.join(OUT_DIR, f"{prefix}.mtl")
        with open(mtl_path, "w", encoding="utf-8") as mf:
            mf.write("# Auto-generated Level 1 vehicle material library\n")
            for mat, rgb in self.palette.items():
                mf.write(f"newmtl {mat}\n")
                mf.write(f"Kd {rgb[0]:.4f} {rgb[1]:.4f} {rgb[2]:.4f}\n")
                if mat.startswith("EMIT_"):
                    mf.write(f"Ke {rgb[0]:.4f} {rgb[1]:.4f} {rgb[2]:.4f}\n")
                mf.write("d 1.0\n\n")

        with open(obj_path, "w", encoding="utf-8") as of:
            of.write(f"# Auto-generated by make_level1_vehicles.py ({prefix})\n")
            of.write(f"mtllib {prefix}.mtl\n")
            for vx, vy, vz in self.vertices:
                of.write(f"v {vx:.5f} {vy:.5f} {vz:.5f}\n")
            for nx, ny, nz in self.normals:
                of.write(f"vn {nx:.5f} {ny:.5f} {nz:.5f}\n")
            for mat, tris in self.faces.items():
                of.write(f"usemtl {mat}\n")
                for (a, b, c), n in tris:
                    of.write(f"f {a}//{n} {b}//{n} {c}//{n}\n")
        print(f"Exported {obj_path} ({len(self.vertices)} verts)")


PALETTE = {
    "MAT_BODY_SEDAN": (0.48, 0.52, 0.56),   # Dusty metallic grey-blue 90s sedan
    "MAT_BODY_VAN": (0.79, 0.77, 0.72),     # Faded off-white/beige cargo van
    "MAT_TRIM": (0.18, 0.19, 0.21),         # Rubber bumpers, grilles, door moldings
    "MAT_TIRE": (0.10, 0.10, 0.11),         # Tires & wheel wells
    "MAT_RIM": (0.64, 0.66, 0.69),          # Steel/alloy wheel rims
    "MAT_GLASS": (0.14, 0.22, 0.28),        # Tinted dusty windshield & windows
    "MAT_UNDER": (0.12, 0.11, 0.11),        # Undercarriage / exhaust / roof rack
    "MAT_RUST": (0.42, 0.26, 0.16),         # Rust accents
    "EMIT_HOT": (0.95, 0.92, 0.80),         # Headlight housings
    "EMIT_ALT": (0.85, 0.16, 0.16),         # Taillight housings
    "EMIT_MAIN": (0.35, 0.72, 0.82),        # Dashboard faint cluster glow
}


def build_sedan():
    """2 wide (X in [-0.42..1.42]) x 1.50 tall x 4 long (Z in [-1.42..2.42]). Center X=0.5, Z=0.5."""
    m = MeshBuilder(PALETTE)
    cx = 0.50

    # Undercarriage pan
    m.add_box(cx - 0.76, 0.18, -1.28, cx + 0.76, 0.28, 2.28, "MAT_UNDER")

    # Lower body shell: front hood, mid belt, rear trunk
    m.add_box(cx - 0.86, 0.26, -1.36, cx + 0.86, 0.78, 2.36, "MAT_BODY_SEDAN")
    # Sloped nose hood taper
    m.add_frustum_z(cx - 0.84, cx + 0.84, -1.38, -0.35, 0.76,
                    cx - 0.80, cx + 0.80, -1.34, -0.35, 0.84, "MAT_BODY_SEDAN")
    # Side protective molding strips
    m.add_box(cx - 0.88, 0.48, -1.10, cx - 0.85, 0.56, 2.10, "MAT_TRIM")
    m.add_box(cx + 0.85, 0.48, -1.10, cx + 0.88, 0.56, 2.10, "MAT_TRIM")

    # Tapered Cabin + Windshield + Rear Window
    m.add_frustum_z(cx - 0.82, cx + 0.82, -0.45, 1.72, 0.80,
                    cx - 0.68, cx + 0.68, -0.02, 1.28, 1.46, "MAT_GLASS")
    # Roof cap & structural A/B/C pillars frame (solid body around top edge)
    m.add_box(cx - 0.70, 1.41, -0.04, cx + 0.70, 1.49, 1.30, "MAT_BODY_SEDAN")
    m.add_box(cx - 0.72, 0.80, 0.46, cx - 0.66, 1.44, 0.58, "MAT_BODY_SEDAN")
    m.add_box(cx + 0.66, 0.80, 0.46, cx + 0.72, 1.44, 0.58, "MAT_BODY_SEDAN")

    # Front & Rear Bumpers + Grille
    m.add_box(cx - 0.88, 0.22, -1.44, cx + 0.88, 0.42, -1.34, "MAT_TRIM")
    m.add_box(cx - 0.88, 0.22, 2.34, cx + 0.88, 0.42, 2.44, "MAT_TRIM")
    m.add_box(cx - 0.42, 0.46, -1.40, cx + 0.42, 0.68, -1.35, "MAT_TRIM")

    # Side mirrors
    m.add_box(cx - 0.98, 0.78, -0.26, cx - 0.82, 0.90, -0.14, "MAT_TRIM")
    m.add_box(cx + 0.82, 0.78, -0.26, cx + 0.98, 0.90, -0.14, "MAT_TRIM")

    # Headlights (front -Z) & Taillights (rear +Z)
    m.add_box(cx - 0.76, 0.50, -1.40, cx - 0.48, 0.66, -1.35, "EMIT_HOT")
    m.add_box(cx + 0.48, 0.50, -1.40, cx + 0.76, 0.66, -1.35, "EMIT_HOT")
    m.add_box(cx - 0.78, 0.54, 2.35, cx - 0.46, 0.69, 2.40, "EMIT_ALT")
    m.add_box(cx + 0.46, 0.54, 2.35, cx + 0.78, 0.69, 2.40, "EMIT_ALT")

    # Four 16-sided wheels resting flush at Y = 0.0
    w_r = 0.34
    for wz in (-0.82, 1.72):
        m.add_wheel_x(cx - 0.92, 0.24, w_r, wz, w_r, 16, "MAT_TIRE", "MAT_RIM")
        m.add_wheel_x(cx + 0.68, 0.24, w_r, wz, w_r, 16, "MAT_TIRE", "MAT_RIM")

    m.write("abandoned_car")


def build_van():
    """2 wide (X in [-0.46..1.46]) x 2.02 tall x 5 long (Z in [-2.38..2.38]). Center X=0.5, Z=0.0."""
    m = MeshBuilder(PALETTE)
    cx = 0.50

    # Heavy chassis frame
    m.add_box(cx - 0.82, 0.20, -2.26, cx + 0.82, 0.32, 2.26, "MAT_UNDER")

    # Main cargo body (rear 3.2m section)
    m.add_box(cx - 0.92, 0.30, -0.95, cx + 0.92, 1.88, 2.34, "MAT_BODY_VAN")
    # Sloped upper roof cap on cargo bay
    m.add_frustum_z(cx - 0.90, cx + 0.90, -0.95, 2.32, 1.86,
                    cx - 0.82, cx + 0.82, -0.88, 2.26, 2.00, "MAT_BODY_VAN")

    # Side cargo panel grooves / rust stripe
    m.add_box(cx - 0.94, 0.82, -0.85, cx - 0.91, 0.92, 2.28, "MAT_RUST")
    m.add_box(cx + 0.91, 0.82, -0.85, cx + 0.94, 0.92, 2.28, "MAT_RUST")

    # Front nose / engine bay & cab lower
    m.add_box(cx - 0.90, 0.30, -2.36, cx + 0.90, 1.02, -0.94, "MAT_BODY_VAN")
    # Sloped windshield cab
    m.add_frustum_z(cx - 0.88, cx + 0.88, -1.78, -0.92, 1.00,
                    cx - 0.80, cx + 0.80, -1.28, -0.92, 1.88, "MAT_GLASS")
    # Cab roof visor connecting seamlessly to cargo box
    m.add_box(cx - 0.82, 1.84, -1.32, cx + 0.82, 1.98, -0.88, "MAT_BODY_VAN")

    # Roof rack rails
    m.add_box(cx - 0.72, 2.00, -0.75, cx - 0.65, 2.06, 2.05, "MAT_UNDER")
    m.add_box(cx + 0.65, 2.00, -0.75, cx + 0.72, 2.06, 2.05, "MAT_UNDER")
    for rz in (-0.50, 0.35, 1.20, 1.95):
        m.add_box(cx - 0.72, 2.01, rz - 0.03, cx + 0.72, 2.05, rz + 0.03, "MAT_UNDER")

    # Heavy steel bumpers, grille & rear cargo door seam
    m.add_box(cx - 0.94, 0.24, -2.44, cx + 0.94, 0.46, -2.32, "MAT_TRIM")
    m.add_box(cx - 0.94, 0.24, 2.32, cx + 0.94, 0.46, 2.44, "MAT_TRIM")
    m.add_box(cx - 0.48, 0.56, -2.40, cx + 0.48, 0.84, -2.34, "MAT_TRIM")
    m.add_box(cx - 0.03, 0.48, 2.33, cx + 0.03, 1.82, 2.37, "MAT_TRIM")
    # Rear small van windows
    m.add_box(cx - 0.72, 1.24, 2.33, cx - 0.16, 1.66, 2.36, "MAT_GLASS")
    m.add_box(cx + 0.16, 1.24, 2.33, cx + 0.72, 1.66, 2.36, "MAT_GLASS")

    # Headlights & Taillights
    m.add_box(cx - 0.80, 0.58, -2.40, cx - 0.54, 0.78, -2.34, "EMIT_HOT")
    m.add_box(cx + 0.54, 0.58, -2.40, cx + 0.80, 0.78, -2.34, "EMIT_HOT")
    m.add_box(cx - 0.86, 0.52, 2.33, cx - 0.68, 0.86, 2.38, "EMIT_ALT")
    m.add_box(cx + 0.68, 0.52, 2.33, cx + 0.86, 0.86, 2.38, "EMIT_ALT")

    # Side mirrors
    m.add_box(cx - 1.02, 1.02, -1.48, cx - 0.88, 1.22, -1.36, "MAT_TRIM")
    m.add_box(cx + 0.88, 1.02, -1.48, cx + 1.02, 1.22, -1.36, "MAT_TRIM")

    # Large utility tires resting flush at Y = 0.0
    w_r = 0.39
    for wz in (-1.52, 1.50):
        m.add_wheel_x(cx - 0.96, 0.26, w_r, wz, w_r, 16, "MAT_TIRE", "MAT_RIM")
        m.add_wheel_x(cx + 0.70, 0.26, w_r, wz, w_r, 16, "MAT_TIRE", "MAT_RIM")

    m.write("abandoned_van")


if __name__ == "__main__":
    build_sedan()
    build_van()
