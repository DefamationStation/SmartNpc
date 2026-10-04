"""Print only the changes to shared source files, to aid upstream review.

Run from any directory with Python 3. New platform files can be reviewed normally
in Git; this view avoids reading unchanged context in the source overlays.
"""
from pathlib import Path
import difflib
import sys

module = Path(__file__).resolve().parents[1]
original = module.parent / "src/main/java"
overlay = module / "src/main/java"
for path in sorted(overlay.rglob("*.java")):
    relative = path.relative_to(overlay)
    base = original / relative
    if base.exists():
        sys.stdout.writelines(difflib.unified_diff(
            base.read_text(encoding="utf-8").splitlines(keepends=True),
            path.read_text(encoding="utf-8").splitlines(keepends=True),
            fromfile="upstream/" + relative.as_posix(),
            tofile="fabric/" + relative.as_posix(),
        ))
