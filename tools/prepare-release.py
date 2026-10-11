"""Prepare a signed APK and update.json for the project's public GitHub release.

Requires Android build-tools and Java. Does not log in, push, or publish anything.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess

PACKAGE = 'cn.cloudcurtain.tv'
SIGNER = 'bd876cc0b82d02864e8d7af31bc5a11c90e6c907906e906ab1f931a38d5be576'
REPO = 'https://github.com/hlhlive0304/cloudcurtain-tv'


def run(args):
    result = subprocess.run([str(a) for a in args], capture_output=True, text=True, encoding='utf-8', errors='replace')
    if result.returncode:
        raise RuntimeError('APK verification command failed')
    return result.stdout


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apk', type=Path, required=True)
    parser.add_argument('--build-tools', type=Path, required=True)
    parser.add_argument('--java', default='java')
    parser.add_argument('--notes', type=Path, required=True)
    parser.add_argument('--output-dir', type=Path, required=True)
    args = parser.parse_args()
    apk = args.apk.resolve(strict=True)
    aapt = args.build_tools / ('aapt.exe' if os.name == 'nt' else 'aapt')
    signer = args.build_tools / 'lib/apksigner.jar'
    cert = run([args.java, '-jar', signer, 'verify', '--verbose', '--print-certs', apk])
    found = re.findall(r'^Signer #\d+ certificate SHA-256 digest: ([0-9a-f]+)$', cert, re.M)
    if found != [SIGNER]:
        raise RuntimeError('Signing identity differs from the installed application')
    info = run([aapt, 'dump', 'badging', apk])
    metadata = re.search(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", info, re.M)
    sdk = re.search(r"^sdkVersion:'(\d+)'", info, re.M)
    if not metadata or not sdk or metadata[1] != PACKAGE:
        raise RuntimeError('Unexpected APK package')
    code, name = int(metadata[2]), metadata[3]
    if not 0 < code <= 2147483647 or not re.fullmatch(r'[A-Za-z0-9._-]{1,80}', name):
        raise RuntimeError('Unsupported version')
    notes = args.notes.read_text(encoding='utf-8-sig').strip()
    if len(notes) > 12000:
        raise RuntimeError('Release notes exceed application limit')
    size = apk.stat().st_size
    if not 0 < size <= 512 * 1024 * 1024:
        raise RuntimeError('APK exceeds application limit')
    with apk.open('rb') as stream:
        digest = hashlib.file_digest(stream, 'sha256').hexdigest()
    filename = f'CloudCurtainTV-{name}.apk'
    manifest = dict(schemaVersion=1, packageName=PACKAGE, versionCode=code, versionName=name,
                    minSdk=int(sdk[1]), apkUrl=f'{REPO}/releases/download/v{name}/{filename}',
                    apkSize=size, sha256=digest, notes=notes)
    args.output_dir.mkdir(parents=True, exist_ok=True)
    destination = args.output_dir / filename
    if destination.resolve() != apk:
        shutil.copyfile(apk, destination)
    (args.output_dir / 'update.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    (args.output_dir / 'SHA256SUMS.txt').write_text(f'{digest}  {filename}\n', encoding='ascii')
    print(f'Prepared v{name}: {filename}, update.json and SHA256SUMS.txt. Nothing published.')


if __name__ == '__main__':
    main()
