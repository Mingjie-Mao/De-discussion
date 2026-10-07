#!/usr/bin/env python3
"""Import an exported legacy fixture through normal authenticated forum APIs.

Credentials and checkpoints stay in .local (ignored by Git). Re-running skips
completed rows. Existing accounts are never overwritten or granted admin roles.
"""
import argparse, json, os, pathlib, secrets, ssl, time, urllib.error, urllib.request
import certifi

parser = argparse.ArgumentParser()
parser.add_argument('export', type=pathlib.Path)
parser.add_argument('--base-url', default='https://de-moderation-api-demo.onrender.com')
args = parser.parse_args()
repo = pathlib.Path(__file__).resolve().parents[1]
path = repo / '.local' / 'forum-import-state.json'
path.parent.mkdir(mode=0o700, exist_ok=True)
data = json.loads(args.export.read_text())
state = json.loads(path.read_text()) if path.exists() else {'base': args.base_url, 'users': {}, 'posts': {}, 'comments': {}, 'media': {}}
if state['base'] != args.base_url: raise SystemExit('Checkpoint belongs to another backend.')
ctx = ssl.create_default_context(cafile=certifi.where())
tokens = {}
blocked = False

def save():
    tmp = path.with_suffix('.tmp')
    fd = os.open(tmp, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(fd, 'w') as stream: json.dump(state, stream, ensure_ascii=False, indent=2)
    os.replace(tmp, path)
    os.chmod(path, 0o600)

def call(method, endpoint, body=None, token=None, raw=None, content_type=None):
    headers = {'Accept': 'application/json'}
    if token: headers['Authorization'] = 'Bearer ' + token
    payload = raw
    if body is not None: payload = json.dumps(body).encode(); content_type = 'application/json'
    if content_type: headers['Content-Type'] = content_type
    req = urllib.request.Request(args.base_url + endpoint, data=payload, headers=headers, method=method)
    with urllib.request.urlopen(req, context=ctx, timeout=120) as response:
        value = response.read()
        return json.loads(value) if value else None

def authenticate(local_id):
    u = state['users'][local_id]
    if local_id not in tokens:
        tokens[local_id] = call('POST', '/api/auth/login', {'username': u['username'], 'password': u['password']})['accessToken']
    return tokens[local_id]

def media(author, name):
    if not name: return None
    key = author + '/' + name
    if key not in state['media']:
        image = repo / 'android/app/src/main/res/drawable-nodpi' / name
        boundary = 'deimport' + secrets.token_hex(16)
        payload = ('--' + boundary + '\r\nContent-Disposition: form-data; name="file"; filename="' + name + '"\r\nContent-Type: image/jpeg\r\n\r\n').encode() + image.read_bytes() + ('\r\n--' + boundary + '--\r\n').encode()
        state['media'][key] = call('POST', '/api/media', token=authenticate(author), raw=payload, content_type='multipart/form-data; boundary=' + boundary)['id']
        save()
    return state['media'][key]

for user in data['users']:
    local = user['localId']
    u = state['users'].setdefault(local, {'username': user['username'], 'password': secrets.token_urlsafe(24), 'displayName': user['displayName']})
    save()
    try:
        if not u.get('id'):
            try: response = call('POST', '/api/auth/register', {'username': u['username'], 'password': u['password']})
            except urllib.error.HTTPError as error:
                if error.code != 409: raise
                # A timed-out registration can still have committed. Recover it
                # with the checkpoint password before choosing another name.
                try:
                    response = call('POST', '/api/auth/login', {'username': u['username'], 'password': u['password']})
                except urllib.error.HTTPError as login_error:
                    if login_error.code != 401: raise
                    alternate = 'seed_' + user['username'] + '_' + local[:6]
                    if u['username'] == alternate: raise SystemExit('Account conflict requires manual reconciliation.')
                    u['username'] = alternate
                    save()
                    response = call('POST', '/api/auth/register', {'username': u['username'], 'password': u['password']})
            u['id'] = response['userId']; tokens[local] = response['accessToken']; save()
        token = authenticate(local)
        if not u.get('profileUpdated'):
            call('PATCH', '/api/users/me', {'displayName': user['displayName']}, token=token)
            u['profileUpdated'] = True
            save()
    except urllib.error.HTTPError as error:
        if error.code != 429: raise SystemExit('Account migration failed: HTTP ' + str(error.code))
        blocked = True
        print(json.dumps({'rate_limited': True, 'retry_after': error.headers.get('Retry-After'), 'registered': sum('id' in item for item in state['users'].values())}), flush=True)
        break

def pages(endpoint):
    from urllib.parse import quote
    result = []
    cursor = None
    while True:
        page = call('GET', endpoint + ('&cursor=' + quote(cursor, safe='') if cursor else ''))
        result.extend(page['items'])
        if not page.get('hasMore'): return result
        cursor = page.get('nextCursor')
        if not cursor: raise SystemExit('Invalid pagination response.')

# Reconcile rows whose write succeeded but whose response was lost. Author IDs
# scope the match to imported accounts; no existing users' content is modified.
existing_posts = []
for forum in sorted({post['forum'] for post in data['posts']}):
    existing_posts.extend(pages('/api/posts?forum=' + forum + '&size=100'))

def recover(rows, used, author_id, body, title=None, parent=None):
    candidates = [row for row in rows if row['id'] not in used
                  and row['author']['id'] == author_id and row['body'] == body
                  and (title is None or row['title'] == title)
                  and (title is not None or row.get('parentCommentId') == parent)]
    if len(candidates) > 1: raise SystemExit('Ambiguous content match requires manual reconciliation.')
    return candidates[0]['id'] if candidates else None

for post in data['posts']:
    if post['author'] not in tokens: continue
    local = post['localId']
    if local not in state['posts']:
        recovered = recover(existing_posts, set(state['posts'].values()), state['users'][post['author']]['id'], post['body'], title=post['title'])
        if recovered: state['posts'][local] = recovered; save()
    if local not in state['posts']:
        body = {'forumKey': post['forum'], 'title': post['title'], 'body': post['body']}
        image = media(post['author'], post.get('image'))
        if image: body['mediaId'] = image
        state['posts'][local] = call('POST', '/api/posts', body, token=authenticate(post['author']))['id']; save()
    existing_comments = pages('/api/posts/' + state['posts'][local] + '/comments?size=100')
    for comment in post['comments']:
        if comment['author'] not in tokens: continue
        if comment['localId'] in state['comments']: continue
        parent = comment.get('parent')
        if parent and parent not in state['comments']: continue
        recovered = recover(existing_comments, set(state['comments'].values()), state['users'][comment['author']]['id'], comment['body'], parent=state['comments'].get(parent))
        if recovered: state['comments'][comment['localId']] = recovered; save(); continue
        body = {'body': comment['body']}
        if parent: body['parentCommentId'] = state['comments'][parent]
        image = media(comment['author'], comment.get('image'))
        if image: body['mediaId'] = image
        state['comments'][comment['localId']] = call('POST', '/api/posts/' + state['posts'][local] + '/comments', body, token=authenticate(comment['author']))['id']; save()
    if len(state['posts']) % 10 == 0: print(json.dumps({'posts':len(state['posts']), 'comments':len(state['comments'])}), flush=True)

print(json.dumps({'registered_users':sum('id' in item for item in state['users'].values()), 'posts':len(state['posts']), 'comments':len(state['comments']), 'media':len(state['media']), 'complete':len(state['posts'])==len(data['posts']) and len(state['comments'])==sum(len(p['comments']) for p in data['posts']), 'rate_limited':blocked}), flush=True)
