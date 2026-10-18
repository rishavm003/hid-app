"""Package an explicit source allowlist; never include runtime data or credentials."""
from pathlib import Path
import shutil
import zipfile

ROOT = Path(__file__).resolve().parents[1]
paths = [ROOT / name for name in ('.gitignore', 'README.md', 'FAMILY.md', 'WINDOWS-IPHONE.md',
    'PROJECT-GUIDE.md', 'ANDROID-FEASIBILITY.md',
    'ios/project.yml', 'server/config.example.json', '.github/workflows/ios-build.yml')]
for directory, pattern in [('ios/Haven', '*.swift'), ('server', '*.py'), ('controller', '*.py'),
                           ('windows', '*.py'), ('windows', '*.vbs'), ('windows', '*.ps1'), ('scripts', '*.py')]:
    paths.extend(sorted((ROOT / directory).glob(pattern)))
output = ROOT / 'release' / 'Notes-GitHub-Source.zip'
output.parent.mkdir(exist_ok=True)
with zipfile.ZipFile(output, 'w', zipfile.ZIP_DEFLATED) as archive:
    for path in paths:
        archive.write(path, path.relative_to(ROOT).as_posix())
with zipfile.ZipFile(output) as archive:
    names = archive.namelist()
    assert not any('/private/' in name or '/tools/' in name or name.endswith(('.log', '.sqlite3', '.dpapi')) for name in names)
    assert '.github/workflows/ios-build.yml' in names
shutil.copy2(output, ROOT / 'release' / 'Haven-GitHub-Source.zip')
print(f'Packaged {len(names)} source files: {output}')

# Keep a small, ready-to-upload tree for the iPhone build. The workflow and
# these six project files are all GitHub Actions needs to produce the IPA.
build_paths = [ROOT / '.github/workflows/ios-build.yml', ROOT / 'ios/project.yml']
build_paths.extend(sorted((ROOT / 'ios/Haven').glob('*.swift')))
assert len(build_paths) == 7 and all(path.is_file() for path in build_paths)
build_folder = ROOT / 'release' / 'Notes-GitHub-Build-Files'
if build_folder.exists():
    shutil.rmtree(build_folder)
for path in build_paths:
    destination = build_folder / path.relative_to(ROOT)
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(path, destination)
build_zip = ROOT / 'release' / 'Notes-GitHub-Build-Files.zip'
with zipfile.ZipFile(build_zip, 'w', zipfile.ZIP_DEFLATED) as archive:
    for path in build_paths:
        archive.write(path, path.relative_to(ROOT).as_posix())
with zipfile.ZipFile(build_zip) as archive:
    assert sorted(archive.namelist()) == sorted(path.relative_to(ROOT).as_posix() for path in build_paths)
shutil.copy2(build_zip, ROOT / 'release' / 'Haven-GitHub-Build-Files.zip')
print(f'Packaged {len(build_paths)} iPhone build files: {build_zip}')
