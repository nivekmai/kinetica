#!/usr/bin/env python3
"""Fetch pinned public build inputs for -PvoicePunctuation=true (no user data).

Only two fixed model members are unpacked. Downloads are checked before use.
Re-run to verify the cache. Optional --host adds a matching CPU evaluation CLI.
"""
import argparse
import hashlib
from pathlib import Path
import tarfile
import urllib.request

ROOT = Path(__file__).resolve().parents[2] / 'build/voice-punctuation'
RELEASE = 'https://github.com/k2-fsa/sherpa-onnx/releases/download/'
FILES = {
    'sherpa-onnx-1.13.8.aar': (RELEASE + 'v1.13.8/sherpa-onnx-1.13.8.aar', '633c24321e06b1fe79feafa03ea16cbc0f8a286641e2da3559bac91bdb13bd96'),
    'model.tar.bz2': (RELEASE + 'punctuation-models/sherpa-onnx-online-punct-en-2024-08-06.tar.bz2', '9f5e5a72c7d2829635bd074fce92b6bbd5b78da8a52e7ad8ed1be933f366b99d'),
}
MODEL = {
    'model.int8.onnx': '9d611f445fe4a46186080fe161be6059d87d72eb88d3a8cb00c1a06e83a6067e',
    'bpe.vocab': 'e118b7ad88c54db562517df49e1cffd4836d166c34fb190fd311d7f34eb238f5',
}

def fetch(name, url, digest):
    path = ROOT / name
    if not path.exists():
        temp = path.with_suffix(path.suffix + '.part')
        urllib.request.urlretrieve(url, temp)
        if hashlib.sha256(temp.read_bytes()).hexdigest() != digest:
            temp.unlink()
            raise ValueError('Checksum mismatch: ' + name)
        temp.replace(path)
    if hashlib.sha256(path.read_bytes()).hexdigest() != digest:
        raise ValueError('Cached checksum mismatch: ' + name)
    print('Verified', name)
    return path

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--host', action='store_true')
    args = parser.parse_args()
    ROOT.mkdir(parents=True, exist_ok=True)
    for name, (url, digest) in FILES.items():
        fetch(name, url, digest)
    assets = ROOT / 'assets/punctuation'
    assets.mkdir(parents=True, exist_ok=True)
    with tarfile.open(ROOT / 'model.tar.bz2') as archive:
        for name, digest in MODEL.items():
            member = archive.getmember('./sherpa-onnx-online-punct-en-2024-08-06/' + name)
            if not member.isfile() or member.size > 8_000_000:
                raise ValueError('Invalid model member')
            data = archive.extractfile(member).read()
            if hashlib.sha256(data).hexdigest() != digest:
                raise ValueError('Model checksum mismatch')
            (assets / name).write_bytes(data)
    if args.host:
        archive = fetch('host.tar.bz2', RELEASE + 'v1.13.8/sherpa-onnx-v1.13.8-linux-x64-shared-no-tts.tar.bz2',
                        'd0f96c8b65c6cd0974fada22737e337de81bc8cd2abbec2e39caf358b1eec5fc')
        with tarfile.open(archive) as tar:
            tar.extractall(ROOT / 'host', filter='data')
    print('Ready for ./gradlew -PvoicePunctuation=true ...')

if __name__ == '__main__':
    main()
