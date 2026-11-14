"""Read-only verification of NuvioDV's MSI or installed application.

Uses Windows Installer's read-only API and extracts only the application JAR with
the installed 7-Zip. Never installs an MSI or prints provider credentials.
"""
import argparse
import ctypes as C
from ctypes import wintypes as W
import hashlib
import json
import os
from pathlib import Path
import struct
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def constants(data):
    offset = 0
    def take(size):
        nonlocal offset
        value = data[offset:offset + size]
        offset += size
        return value
    def u2():
        return struct.unpack('>H', take(2))[0]
    assert take(4) == b'\xca\xfe\xba\xbe'
    take(4)
    pool = [None] * u2()
    index = 1
    while index < len(pool):
        tag = take(1)[0]
        if tag == 1:
            pool[index] = take(u2()).decode('utf-8', errors='replace')
        elif tag == 3:
            pool[index] = struct.unpack('>i', take(4))[0]
        elif tag == 8:
            pool[index] = ('string', u2())
        elif tag in (5, 6):
            take(8)
            index += 1
        else:
            take({4: 4, 7: 2, 9: 4, 10: 4, 11: 4, 12: 4, 15: 3,
                  16: 2, 17: 4, 18: 4, 19: 2, 20: 2}[tag])
        index += 1
    take(6)
    take(2 * u2())
    result = {}
    for _ in range(u2()):
        take(2)
        name = pool[u2()]
        take(2)
        for _ in range(u2()):
            attribute = pool[u2()]
            size = struct.unpack('>I', take(4))[0]
            if attribute == 'ConstantValue':
                value = pool[u2()]
                result[name] = pool[value[1]] if isinstance(value, tuple) else value
            else:
                take(size)
    return result


def inspect_jar(path, version, code):
    pins = dict(line.split() for line in
        (ROOT / 'composeApp/src/desktopMain/native/windows/runtime-pins.sha256').read_text().splitlines()
        if line.strip())
    with zipfile.ZipFile(path) as jar:
        names = jar.namelist()
        def config(suffix):
            matches = [name for name in names if name.endswith('/' + suffix + '.class')]
            assert len(matches) == 1, suffix
            return constants(jar.read(matches[0]))
        app = config('AppVersionConfig')
        assert app['DESKTOP_VERSION_NAME'] == version
        assert app['DESKTOP_VERSION_CODE'] == code
        clients = {}
        for name in ('SimklConfig', 'MdbListConfig', 'TraktConfig'):
            value = config(name)['CLIENT_ID']
            assert value.strip(), name + ' not configured'
            clients[name] = hashlib.sha256(value.encode()).hexdigest()
        supabase = config('SupabaseConfig')
        assert supabase['URL'].strip() and supabase['ANON_KEY'].strip()
        updater = [jar.read(name) for name in names
                   if '/updater/AppUpdaterPlatform' in name and name.endswith('.class')]
        assert any(b'NuvioDesktopDV' in value and b'Darkaxt' in value for value in updater)
        assert any(b't0-free-rpdb' in jar.read(name) for name in names
                   if 'Rpdb' in name and name.endswith('.class'))
        for expected, name in pins.items():
            with jar.open('native/windows/' + name) as stream:
                assert hashlib.file_digest(stream, 'sha256').hexdigest() == expected, name
        assert any('Animation' in name and name.endswith('.class') for name in names)
    return {'jar': str(path), 'jar_sha256': digest(path), 'version': version, 'code': code,
            'client_id_sha256': clients, 'supabase_configured': True,
            'updater': 'Darkaxt/NuvioDesktopDV', 'rpdb_public_key': True,
            'runtime_pins': pins}


def msi_metadata(path):
    msi = C.WinDLL('msi')
    msi.MsiOpenDatabaseW.argtypes = [W.LPCWSTR, W.LPCWSTR, C.POINTER(W.UINT)]
    msi.MsiDatabaseOpenViewW.argtypes = [W.UINT, W.LPCWSTR, C.POINTER(W.UINT)]
    msi.MsiViewExecute.argtypes = [W.UINT, W.UINT]
    msi.MsiViewFetch.argtypes = [W.UINT, C.POINTER(W.UINT)]
    msi.MsiRecordGetStringW.argtypes = [W.UINT, W.UINT, W.LPWSTR, C.POINTER(W.DWORD)]
    database = W.UINT()
    assert msi.MsiOpenDatabaseW(str(path), None, C.byref(database)) == 0
    def rows(query, fields):
        view = W.UINT()
        assert msi.MsiDatabaseOpenViewW(database, query, C.byref(view)) == 0
        try:
            assert msi.MsiViewExecute(view, 0) == 0
            result = []
            while True:
                record = W.UINT()
                status = msi.MsiViewFetch(view, C.byref(record))
                if status == 259:
                    return result
                assert status == 0, status
                try:
                    values = []
                    for index in range(1, fields + 1):
                        size = W.DWORD()
                        assert msi.MsiRecordGetStringW(record, index, None, C.byref(size)) in (0, 234)
                        buffer = C.create_unicode_buffer(size.value + 1)
                        size.value += 1
                        assert msi.MsiRecordGetStringW(record, index, buffer, C.byref(size)) == 0
                        values.append(buffer.value)
                    result.append(values)
                finally:
                    msi.MsiCloseHandle(record)
        finally:
            msi.MsiCloseHandle(view)
    try:
        properties = dict(rows('SELECT `Property`, `Value` FROM `Property`', 2))
        jars = [(key, name.split('|')[-1]) for key, name in
            rows('SELECT `File`, `FileName` FROM `File`', 2)
            if name.split('|')[-1].startswith('composeApp-desktop-') and name.endswith('.jar')]
        assert len(jars) == 1
        return {key: properties[key] for key in ('ProductName', 'ProductVersion', 'ProductCode', 'UpgradeCode')} | {
            'jar_key': jars[0][0], 'jar_name': jars[0][1]}
    finally:
        msi.MsiCloseHandle(database)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--msi', type=Path)
    parser.add_argument('--scratch', type=Path)
    parser.add_argument('--version', required=True)
    parser.add_argument('--code', required=True, type=int)
    parser.add_argument('--msi-version')
    parser.add_argument('--baseline', type=Path)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    if args.msi:
        assert args.scratch and args.msi_version
        metadata = msi_metadata(args.msi.resolve())
        assert metadata['ProductName'] == 'NuvioDV'
        assert metadata['ProductVersion'] == args.msi_version
        assert metadata['UpgradeCode'].strip('{}').lower() == '9710d835-a9d6-5e42-a15f-6ff37d179456'
        args.scratch.mkdir(parents=True, exist_ok=False)
        subprocess.run([r'C:\Users\darka\scoop\shims\7z.exe', 'e', str(args.msi.resolve()),
            metadata['jar_key'], '-y', '-o' + str(args.scratch)], check=True, capture_output=True)
        result = inspect_jar(args.scratch / metadata['jar_key'], args.version, args.code)
        result.update(msi_sha256=digest(args.msi), msi_properties=metadata)
    else:
        jars = list(Path(r'C:\Program Files\NuvioDV\app').glob('composeApp-desktop-*.jar'))
        assert len(jars) == 1
        result = inspect_jar(jars[0], args.version, args.code)
        result['profiles'] = {str(path): path.stat().st_ctime_ns for path in (
            Path(os.environ['LOCALAPPDATA']) / 'NuvioDV', Path(os.environ['APPDATA']) / 'NuvioDV')}
    if args.baseline:
        baseline = json.loads(args.baseline.read_text())
        assert result['client_id_sha256'] == baseline['client_id_sha256']
    args.output.write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(result, indent=2))


if __name__ == '__main__':
    main()
