"""Package an explicit source allowlist; never include runtime data or credentials."""
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[1]
paths = [ROOT / name for name in ('.gitignore', 'README.md', 'FAMILY.md', 'WINDOWS-IPHONE.md',
    'ios/project.yml', 'server/config.example.json', '.github/workflows/ios-build.yml')]
for directory, pattern in [('ios/Haven', '*.swift'), ('server', '*.py'), ('controller', '*.py'),
                           ('windows', '*.py'), ('windows', '*.vbs'), ('windows', '*.ps1'), ('scripts', '*.py')]:
    paths.extend(sorted((ROOT / directory).glob(pattern)))
output = ROOT / 'release' / 'Haven-GitHub-Source.zip'
output.parent.mkdir(exist_ok=True)
with zipfile.ZipFile(output, 'w', zipfile.ZIP_DEFLATED) as archive:
    for path in paths:
        archive.write(path, path.relative_to(ROOT).as_posix())
with zipfile.ZipFile(output) as archive:
    names = archive.namelist()
    assert not any('/private/' in name or '/tools/' in name or name.endswith(('.log', '.sqlite3', '.dpapi')) for name in names)
    assert '.github/workflows/ios-build.yml' in names
print(f'Packaged {len(names)} source files: {output}')
