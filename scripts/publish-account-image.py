#!/usr/bin/env python3
"""Publish a prepared backend image using a memory-only GHCR helper.

Run in your own terminal. The PAT is prompted without echo, passed to Docker on
stdin, and kept only in this process. No global Docker/Keychain config changes.
"""
import getpass
import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import resource
import re
import shutil
import socket
import socketserver
import subprocess
import sys
import tempfile
import threading

IMAGE = 'ghcr.io/mingjie-mao/de-moderation-backend:account-settings-20261007'
USERNAME = 'Mingjie-Mao'
REGISTRY = 'ghcr.io'
MAX_MESSAGE = 16384


def registry_matches(value):
    return value in (REGISTRY, 'https://' + REGISTRY, 'https://' + REGISTRY + '/')


def helper():
    """Docker helper protocol; output is consumed by Docker, never by the user."""
    try:
        operation = sys.argv[2]
        raw = sys.stdin.buffer.read(MAX_MESSAGE + 1)
        if len(raw) > MAX_MESSAGE or operation not in ('store', 'get', 'erase', 'list'):
            raise ValueError()
        request = {'operation': operation, 'input': raw.decode().strip()}
        with socket.socket(socket.AF_UNIX, socket.SOCK_STREAM) as connection:
            connection.settimeout(10)
            connection.connect(os.environ['CAMPUSGUARD_AUTH_SOCKET'])
            connection.sendall(json.dumps(request).encode() + b'\n')
            reply = json.loads(connection.makefile('rb').readline(MAX_MESSAGE + 1))
        if not reply.get('ok'):
            raise ValueError()
        if operation in ('get', 'list'):
            print(json.dumps(reply['result']))
        return 0
    except Exception:
        print('Temporary GHCR credential helper failed.', file=sys.stderr)
        return 1


def handle_request(request, secret, state):
    operation, raw = request['operation'], request['input']
    if operation == 'store':
        supplied = json.loads(raw)
        if (not registry_matches(supplied.get('ServerURL'))
                or supplied.get('Username') != USERNAME
                or supplied.get('Secret') != secret):
            raise ValueError()
        state['stored'] = True
        return {'ok': True}
    if operation == 'list':
        return {'ok': True, 'result': {REGISTRY: USERNAME} if state['stored'] else {}}
    if not registry_matches(raw):
        raise ValueError()
    if operation == 'erase':
        state['stored'] = False
        return {'ok': True}
    if operation == 'get' and state['stored']:
        return {'ok': True, 'result': {'Username': USERNAME, 'Secret': secret}}
    raise ValueError()


def publish(image_name=IMAGE):
    if not re.fullmatch(r'ghcr\.io/mingjie-mao/de-moderation-backend:[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}', image_name):
        raise SystemExit('Only a tagged image in the existing backend GHCR repository is allowed.')
    if not sys.stdin.isatty():
        raise SystemExit('Run this script directly in your own interactive terminal.')
    resource.setrlimit(resource.RLIMIT_CORE, (0, 0))
    docker = shutil.which('docker')
    if not docker:
        raise SystemExit('Docker CLI is not installed.')
    image = subprocess.check_output([docker, 'image', 'inspect', image_name,
                                    '--format', '{{.Os}}/{{.Architecture}}'], text=True).strip()
    if image != 'linux/amd64':
        raise SystemExit('The expected linux/amd64 image is missing.')
    endpoint = subprocess.check_output([docker, 'context', 'inspect', '--format',
                                       '{{.Endpoints.docker.Host}}'], text=True).strip()
    if not endpoint.startswith('unix:///') or '\n' in endpoint:
        raise SystemExit('Expected a local Unix-socket Docker runtime.')
    print('Publish image: ' + image_name)
    print('Paste your GitHub classic PAT with write:packages. Input is hidden.')
    secret = getpass.getpass('GitHub token: ').strip()
    if not secret.startswith('ghp_') or any(character.isspace() for character in secret):
        raise SystemExit('Expected a GitHub classic PAT (ghp_...), not your account password.')
    state = {'stored': False}

    class Handler(socketserver.StreamRequestHandler):
        def handle(self):
            self.connection.settimeout(10)
            try:
                raw = self.rfile.readline(MAX_MESSAGE + 1)
                if len(raw) > MAX_MESSAGE:
                    raise ValueError()
                reply = handle_request(json.loads(raw), secret, state)
            except Exception:
                reply = {'ok': False}
            self.wfile.write(json.dumps(reply).encode() + b'\n')

    # Short socket path for macOS; directory 0700 and socket 0600 restrict access.
    with tempfile.TemporaryDirectory(prefix='cg-publish-', dir='/private/tmp') as folder:
        directory = Path(folder)
        directory.chmod(0o700)
        socket_path = directory / 'auth.sock'
        with socketserver.UnixStreamServer(str(socket_path), Handler) as server:
            socket_path.chmod(0o600)
            thread = threading.Thread(target=server.serve_forever, daemon=True)
            thread.start()
            try:
                wrapper = directory / 'docker-credential-campusguard-memory'
                # JSON strings are Python literals for these path values, not shell code.
                wrapper.write_text('#!' + sys.executable + '\nimport os\nos.execv(' +
                                   repr(sys.executable) + ', [' + repr(sys.executable) + ', ' +
                                   repr(str(Path(__file__).resolve())) +
                                   ', "--helper"] + __import__("sys").argv[1:])\n')
                wrapper.chmod(0o700)
                config = directory / 'config.json'
                config.write_text(json.dumps({'credHelpers': {REGISTRY: 'campusguard-memory'}}))
                config.chmod(0o600)
                environment = dict(os.environ, DOCKER_HOST=endpoint,
                                   CAMPUSGUARD_AUTH_SOCKET=str(socket_path))
                environment.pop('DOCKER_CONTEXT', None)
                environment['PATH'] = str(directory) + os.pathsep + environment.get('PATH', '')
                command = [docker, '--config', folder]
                login = subprocess.run(command + ['login', REGISTRY, '-u', USERNAME,
                                                  '--password-stdin'], input=secret + '\n',
                                       text=True, capture_output=True, env=environment, timeout=120)
                print((login.stdout + login.stderr).replace(secret, '[redacted]').strip())
                if login.returncode:
                    raise SystemExit('GHCR login failed; no image was published.')
                process = subprocess.Popen(command + ['push', image_name], stdout=subprocess.PIPE,
                                           stderr=subprocess.STDOUT, text=True, env=environment)
                digest = None
                try:
                    for line in process.stdout:
                        print(line.replace(secret, '[redacted]'), end='', flush=True)
                        match = re.search(r'\bdigest: (sha256:[a-f0-9]{64})\b', line)
                        if match:
                            digest = match.group(1)
                    if process.wait():
                        raise SystemExit('Image publication failed.')
                finally:
                    if process.poll() is None:
                        process.terminate()
                        process.wait()
                if digest:
                    evidence = Path(__file__).resolve().parents[1] / '.local'
                    evidence.mkdir(mode=0o700, exist_ok=True)
                    with tempfile.NamedTemporaryFile(mode='w', prefix='publish-', suffix='.json',
                                                     dir=evidence, delete=False) as output:
                        json.dump({'image': image_name, 'digest': digest,
                                   'publishedAt': datetime.now(timezone.utc).isoformat(),
                                   'authenticationStorage': 'memory only'}, output, indent=2)
                        result_path = Path(output.name)
                    result_path.replace(evidence / 'registry-image-publish.json')
                    if image_name == IMAGE:
                        (evidence / 'account-image-publish.json').write_text((evidence / 'registry-image-publish.json').read_text())
                else:
                    print('Registry digest not captured; verify it before deployment.')
                print('\nImage published. Temporary authentication is now discarded.')
            finally:
                server.shutdown()
                thread.join(timeout=5)


if __name__ == '__main__':
    try:
        if len(sys.argv) > 1 and sys.argv[1] == '--helper':
            sys.exit(helper())
        parser = argparse.ArgumentParser(description=__doc__)
        parser.add_argument('--image', default=IMAGE, help='Existing backend image tag to publish; never pushes Git commits.')
        sys.exit(publish(parser.parse_args().image))
    except KeyboardInterrupt:
        raise SystemExit('\nCancelled. Temporary authentication discarded.')
    except (OSError, subprocess.SubprocessError):
        raise SystemExit('Publication stopped due to a local Docker or connection error.')
