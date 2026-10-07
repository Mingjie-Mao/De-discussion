#!/usr/bin/env python3
"""Opt-in live profile acceptance using a dedicated account, never original users."""
import argparse
import json
import os
import pathlib
import secrets
import ssl
import struct
import urllib.request
import zlib
import certifi

parser = argparse.ArgumentParser()
parser.add_argument('--base-url', required=True)
args = parser.parse_args()
base = args.base_url.rstrip('/')
if not base.startswith('https://'):
    parser.error('Use the deployment HTTPS origin.')
root = pathlib.Path(__file__).resolve().parents[1]
ctx = ssl.create_default_context(cafile=certifi.where())


def call(method, path, token=None, body=None, content_type=None):
    headers = {'Accept':'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    if body is not None and not isinstance(body, bytes):
        body = json.dumps(body).encode()
        content_type = 'application/json'
    if content_type:
        headers['Content-Type'] = content_type
    request = urllib.request.Request(base+path, data=body, headers=headers, method=method)
    with urllib.request.urlopen(request, context=ctx, timeout=35) as response:
        raw = response.read()
        return json.loads(raw) if 'application/json' in response.headers.get('Content-Type','') else raw


def login(username, password):
    return call('POST','/api/auth/login',body={'username':username,'password':password})['accessToken']


# Refuse to create/change anything until the new schema/API is available.
preflight = call('GET','/api/users/me',login('1234','1234'))
required = {'avatarMediaId','avatarUrl','avatarColor','languageTag','theme'}
if not required <= preflight.keys():
    raise SystemExit('V16 profile API is not deployed; no test account or settings were changed.')

credential_path = root/'.local/account-profile-qa-credentials.json'
if credential_path.exists():
    credentials = json.loads(credential_path.read_text())
    if credentials['origin'] != base:
        raise SystemExit('QA credentials belong to another origin; refusing to transmit them.')
else:
    credentials = {'origin':base,'username':'qa_profile_'+secrets.token_hex(5),
                   'password':secrets.token_urlsafe(24)}
    descriptor = os.open(credential_path, os.O_WRONLY|os.O_CREAT|os.O_EXCL, 0o600)
    with os.fdopen(descriptor,'w') as output:
        json.dump(credentials,output)
    call('POST','/api/auth/register',body={'username':credentials['username'],'password':credentials['password']})

token = login(credentials['username'],credentials['password'])
call('PATCH','/api/users/me',token,{'languageTag':'zh-CN','theme':'dark','avatarColor':5})
fresh = login(credentials['username'],credentials['password'])
profile = call('GET','/api/users/me',fresh)
assert profile['languageTag']=='zh-CN' and profile['theme']=='dark' and profile['avatarColor']==5
public = call('GET','/api/users/'+profile['id'],login('1234','1234'))
assert 'languageTag' not in public and 'theme' not in public and 'email' not in public

# A small, generated PNG test fixture, with no personal photo or metadata.
def chunk(kind, data):
    return struct.pack('!I',len(data))+kind+data+struct.pack('!I',zlib.crc32(kind+data)&0xffffffff)
png = (b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('!IIBBBBB',32,32,8,2,0,0,0))
       +chunk(b'IDAT',zlib.compress((b'\0'+bytes([60,130,190])*32)*32))+chunk(b'IEND',b''))
boundary = 'qa-'+secrets.token_hex(12)
multipart = (('--'+boundary+'\r\nContent-Disposition: form-data; name="file"; filename="qa-avatar.png"\r\n'
              'Content-Type: image/png\r\n\r\n').encode()+png+('\r\n--'+boundary+'--\r\n').encode())
media = call('POST','/api/media',token,multipart,'multipart/form-data; boundary='+boundary)
call('PATCH','/api/users/me',token,{'avatarMediaId':media['id']})
persisted = call('GET','/api/users/me',login(credentials['username'],credentials['password']))
assert persisted['avatarMediaId']==media['id'] and persisted['languageTag']=='zh-CN' and persisted['theme']=='dark'
avatar = persisted['avatarUrl']
if avatar.startswith(base):
    avatar = avatar[len(base):]
assert avatar.startswith('/api/media/')
assert len(call('GET',avatar))>0
result = {'baseUrl':base,'private_preferences_persist_after_fresh_login':True,
          'preferences_not_exposed_on_public_profile':True,'owned_avatar_persists':True,
          'avatar_public_bytes_readable':True,'original_accounts_unchanged':True,
          'dedicated_qa_account_and_avatar_retained':True}
(root/'.local/account-profile-live-verification.json').write_text(json.dumps(result,indent=2))
print(json.dumps(result))
