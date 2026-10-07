#!/usr/bin/env python3
"""Check the current checkouts; push only with an explicit --push option."""
import argparse
import getpass
import json
import os
from pathlib import Path
import resource
import shutil
import socket
import socketserver
import subprocess
import sys
import tempfile
import threading
import re
from urllib.parse import urlsplit

ROOT = Path(__file__).resolve().parents[1]
BACKEND_ROOT = ROOT.parent / 'De-moderation'
if not BACKEND_ROOT.is_dir():
    BACKEND_ROOT = ROOT.parent / 'De-moderation-backend'
PROJECTS = (
    (ROOT, 'https://github.com/Mingjie-Mao/De-discussion.git'),
    (BACKEND_ROOT, 'https://github.com/Mingjie-Mao/De-moderation-backend.git'),
)


def askpass():
    try:
        prompt = sys.argv[2].lower()
        address = re.search(r'https://[^\s\'\"]+', prompt)
        if not address or urlsplit(address.group()).hostname != 'github.com':
            raise ValueError()
        kind = 'password' if 'password' in prompt else 'username' if 'username' in prompt else None
        if kind is None:
            raise ValueError()
        with socket.socket(socket.AF_UNIX, socket.SOCK_STREAM) as connection:
            connection.settimeout(10)
            connection.connect(os.environ['CAMPUSGUARD_GIT_AUTH_SOCKET'])
            connection.sendall(kind.encode() + b'\n')
            response = json.loads(connection.makefile('rb').readline(16384))
        print(response['value'])
        return 0
    except Exception:
        return 1


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument('--check', action='store_true', help='Inspect only (default); never requests a token.')
    mode.add_argument('--push', action='store_true', help='Explicitly publish clean committed checkouts without force.')
    args = parser.parse_args()
    if args.push and not sys.stdin.isatty():
        raise SystemExit('Run --push directly in your own terminal. Do not put a token in a command.')
    resource.setrlimit(resource.RLIMIT_CORE, (0, 0))
    git = shutil.which('git')
    if not git:
        raise SystemExit('Git is unavailable.')
    for folder, url in PROJECTS:
        dirty = subprocess.check_output([git, 'status', '--porcelain'], cwd=folder, text=True).strip()
        if args.push and dirty:
            raise SystemExit('Uncommitted changes remain in ' + folder.name + '; finish preparing commits first.')
        if args.push:
            subprocess.run([git, '-c', 'credential.helper=', 'fetch', '--no-tags', 'origin', 'main'],
                           cwd=folder, env=dict(os.environ, GIT_TERMINAL_PROMPT='0'), check=True, timeout=60)
        behind, ahead = map(int, subprocess.check_output(
            [git, 'rev-list', '--left-right', '--count', 'origin/main...HEAD'], cwd=folder, text=True).split())
        print(json.dumps({'repository': url, 'checkout': str(folder), 'commitsAhead': ahead,
                          'commitsBehind': behind, 'uncommitted': bool(dirty)}))
        if args.push and behind:
            raise SystemExit('Remote main has newer commits; reconcile before publishing. No force push was attempted.')
    if not args.push:
        print('Check only. No token requested; no commit or push executed. Future publication requires --push and clean checkouts.')
        return
    print('Enter a classic GitHub PAT with repo and workflow permissions. Input is hidden.')
    secret = getpass.getpass('GitHub token: ').strip()
    if not secret.startswith('ghp_') or any(c.isspace() for c in secret):
        raise SystemExit('Expected a classic GitHub PAT (ghp_...), not an account password.')

    class Handler(socketserver.StreamRequestHandler):
        def handle(self):
            self.connection.settimeout(10)
            kind = self.rfile.readline(32).strip()
            if kind in (b'username', b'password'):
                value = 'Mingjie-Mao' if kind == b'username' else secret
                self.wfile.write(json.dumps({'value': value}).encode() + b'\n')

    evidence = []
    with tempfile.TemporaryDirectory(prefix='cg-git-', dir='/private/tmp') as temporary:
        directory = Path(temporary)
        directory.chmod(0o700)
        socket_path = directory / 'auth.sock'
        with socketserver.UnixStreamServer(str(socket_path), Handler) as server:
            socket_path.chmod(0o600)
            thread = threading.Thread(target=server.serve_forever, daemon=True)
            thread.start()
            try:
                wrapper = directory / 'askpass'
                wrapper.write_text('#!' + sys.executable + '\nimport os,sys\nos.execv(' +
                                   repr(sys.executable) + ', [' + repr(sys.executable) + ', ' +
                                   repr(str(Path(__file__).resolve())) + ', "--askpass"] + sys.argv[1:])\n')
                wrapper.chmod(0o700)
                environment = dict(os.environ, GIT_ASKPASS=str(wrapper), GIT_TERMINAL_PROMPT='0',
                                   CAMPUSGUARD_GIT_AUTH_SOCKET=str(socket_path))
                for folder, url in PROJECTS:
                    # Explicit destinations, no force, no credential storage/helper invocation.
                    result = subprocess.run([git, '-c', 'credential.helper=', 'push', url,
                                             'HEAD:refs/heads/main'], cwd=folder, env=environment,
                                            text=True, capture_output=True, timeout=240)
                    print((result.stdout + result.stderr).replace(secret, '[redacted]').strip())
                    if result.returncode:
                        raise SystemExit('Push failed for ' + folder.name + '. Remote changes were not overwritten.')
                    revision = subprocess.check_output([git, 'rev-parse', 'HEAD'], cwd=folder, text=True).strip()
                    remote = subprocess.run([git, '-c', 'credential.helper=', 'ls-remote', url,
                                             'refs/heads/main'], cwd=folder, env=environment,
                                            text=True, capture_output=True, timeout=60)
                    remote_fields = remote.stdout.split()
                    if remote.returncode or not remote_fields or remote_fields[0] != revision:
                        raise SystemExit('Push returned success, but remote verification failed for ' + folder.name)
                    evidence.append({'repository': url, 'branch': 'main', 'commit': revision,
                                     'remoteVerified': True})
                    destination = PROJECTS[0][0] / '.local'
                    destination.mkdir(mode=0o700, exist_ok=True)
                    with tempfile.NamedTemporaryFile(mode='w', dir=destination, prefix='git-push-',
                                                     suffix='.json', delete=False) as output:
                        json.dump(evidence, output, indent=2)
                        path = Path(output.name)
                    path.replace(destination / 'project-push-verification.json')
                print('Both main branches pushed and verified. Temporary authentication discarded.')
            finally:
                server.shutdown()
                thread.join(timeout=5)


if __name__ == '__main__':
    try:
        sys.exit(askpass() if len(sys.argv) > 1 and sys.argv[1] == '--askpass' else main())
    except KeyboardInterrupt:
        raise SystemExit('\nCancelled. Temporary authentication discarded.')
    except (OSError, subprocess.SubprocessError):
        raise SystemExit('Stopped due to a local Git or connection error.')
