#!/usr/bin/env python3
"""Read back an import and check content, authors, reply parents and images."""
import argparse, json, pathlib, ssl, urllib.request
import certifi
parser=argparse.ArgumentParser()
parser.add_argument('export', type=pathlib.Path)
parser.add_argument('--base-url', help='Current endpoint for the same database; read-only verification can follow a platform move.')
parser.add_argument('--interview-aliases', action='store_true', help='Verify campusviewer/modmentor content under the real public demo accounts.')
args=parser.parse_args()
repo=pathlib.Path(__file__).resolve().parents[1]
state=json.loads((repo/'.local/forum-import-state.json').read_text())
data=json.loads(args.export.read_text())
base=(args.base_url or state['base']).rstrip('/')
context=ssl.create_default_context(cafile=certifi.where())
def read(path, image=False):
    with urllib.request.urlopen(base+path, context=context, timeout=30) as response:
        value=response.read()
        if image:
            assert response.headers.get_content_type().startswith('image/')
            assert len(value)>100
            return
        return json.loads(value)
def pages(path):
    from urllib.parse import quote
    result=[]
    while True:
        page=read(path)
        result.extend(page['items'])
        if not page.get('hasMore'): return result
        assert page.get('nextCursor')
        path=path.split('&cursor=')[0]+'&cursor='+quote(page['nextCursor'],safe='')
users={user['localId']:user for user in data['users']}
authors={key:dict(value) for key,value in state['users'].items()}
if args.interview_aliases:
    for local,source in users.items():
        name={'campusviewer':'1234','modmentor':'12345'}.get(source['username'])
        if name:
            request=urllib.request.Request(base+'/api/auth/login',data=json.dumps({'username':name,'password':name}).encode(),headers={'Content-Type':'application/json'},method='POST')
            with urllib.request.urlopen(request,context=context,timeout=30) as response: login=json.load(response)
            request=urllib.request.Request(base+'/api/users/me',headers={'Authorization':'Bearer '+login['accessToken']})
            with urllib.request.urlopen(request,context=context,timeout=30) as response: profile=json.load(response)
            authors[local]={'id':profile['id'],'displayName':profile['displayName']}

posts={}
for forum in sorted({post['forum'] for post in data['posts']}):
    posts.update({post['id']:post for post in pages('/api/posts?forum='+forum+'&size=100')})
checked_comments=0
for expected in data['posts']:
    actual=posts[state['posts'][expected['localId']]]
    assert (actual['title'],actual['body'],actual['forumKey'])==(expected['title'],expected['body'],expected['forum'])
    author=authors[expected['author']]
    assert actual['author']['id']==author['id']
    assert actual['author']['displayName']==authors[expected['author']].get('displayName',users[expected['author']]['displayName'])
    if expected.get('image'): assert actual['mediaUrl'].split('?')[0].endswith(state['media'][expected['author']+'/'+expected['image']])
    if 'category' in expected: assert actual['category']==expected['category']
    if 'pinRank' in expected: assert actual.get('pinRank')==expected['pinRank']
    comments={row['id']:row for row in pages('/api/posts/'+actual['id']+'/comments?size=100')}
    for comment in expected['comments']:
        received=comments[state['comments'][comment['localId']]]
        assert received['body']==comment['body']
        assert received['author']['id']==authors[comment['author']]['id']
        assert received['author']['displayName']==authors[comment['author']].get('displayName',users[comment['author']]['displayName'])
        assert received.get('parentCommentId')==state['comments'].get(comment.get('parent'))
        if comment.get('image'): assert received['mediaUrl'].split('?')[0].endswith(state['media'][comment['author']+'/'+comment['image']])
        checked_comments+=1
for media_id in set(state['media'].values()): read('/api/media/'+media_id+'?v=2', image=True)
result={'users':len(users),'posts_verified':len(data['posts']),'comments_verified':checked_comments,'images_verified':len(set(state['media'].values())),'content_authors_parents_match':True}
(repo/'.local/import-verification.json').write_text(json.dumps(result,indent=2))
print(json.dumps(result),flush=True)
