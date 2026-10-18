"""Export a reviewable, source-only Haven project folder and matching ZIP."""

from __future__ import annotations

import hashlib
from pathlib import Path
import shutil
import zipfile


ROOT = Path(__file__).resolve().parents[1]
RELEASE = ROOT / "release"
DESTINATION = RELEASE / "Notes-Complete-Project"
ARCHIVE = RELEASE / "Notes-Complete-Project.zip"

FILES = [
    ".gitignore",
    ".github/workflows/ios-build.yml",
    "README.md",
    "FAMILY.md",
    "WINDOWS-IPHONE.md",
    "PROJECT-GUIDE.md",
    "ANDROID-FEASIBILITY.md",
    "ios/project.yml",
    "ios/Haven/HavenApp.swift",
    "ios/Haven/SafetyModel.swift",
    "ios/Haven/ContentView.swift",
    "ios/Haven/FamilyModel.swift",
    "ios/Haven/FamilyView.swift",
    "server/config.example.json",
    "server/server.py",
    "server/family.py",
    "server/test_server.py",
    "server/test_family.py",
    "controller/controller.py",
    "windows/start_haven.py",
    "windows/Start Haven.vbs",
    "windows/Install connection.ps1",
    "scripts/package_source.py",
    "scripts/export_project.py",
]


def main() -> None:
    if DESTINATION.resolve().parent != RELEASE.resolve() or RELEASE.resolve().parent != ROOT.resolve():
        raise RuntimeError("Export path escaped the workspace")
    if DESTINATION.is_symlink():
        raise RuntimeError("Refusing to replace a linked export folder")
    missing = [name for name in FILES if not (ROOT / name).is_file()]
    if missing:
        raise FileNotFoundError(f"Missing project files: {missing}")

    RELEASE.mkdir(exist_ok=True)
    if DESTINATION.exists():
        shutil.rmtree(DESTINATION)
    hashes = []
    for name in FILES:
        source = ROOT / name
        target = DESTINATION / name
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, target)
        hashes.append(f"{hashlib.sha256(target.read_bytes()).hexdigest()}  {name}")
    (DESTINATION / "SHA256SUMS.txt").write_text("\n".join(hashes) + "\n", encoding="utf-8")

    with zipfile.ZipFile(ARCHIVE, "w", zipfile.ZIP_DEFLATED) as output:
        for file in sorted(DESTINATION.rglob("*")):
            if file.is_file():
                output.write(file, file.relative_to(RELEASE).as_posix())
    with zipfile.ZipFile(ARCHIVE) as output:
        actual = set(output.namelist())
        expected = {f"{DESTINATION.name}/{name}" for name in [*FILES, "SHA256SUMS.txt"]}
        if actual != expected or output.testzip() is not None:
            raise RuntimeError("Project ZIP verification failed")
    shutil.copy2(ARCHIVE, RELEASE / "Haven-Complete-Project.zip")
    print(f"Exported {len(FILES)} source and documentation files to {DESTINATION}")
    print(f"ZIP copy: {ARCHIVE}")


if __name__ == "__main__":
    main()
