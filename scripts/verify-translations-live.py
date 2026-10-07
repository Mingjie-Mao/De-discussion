#!/usr/bin/env python3
"""Verify live cached translations without printing tokens or user content.

--run-model explicitly permits submitting an existing public demo post/comment
to the already configured model. No forum content is created or edited.
"""
import argparse
import http.client
import json
from pathlib import Path
import ssl
import time
from urllib.parse import urlsplit
import certifi


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--base-url', required=True)
    parser.add_argument('--output', required=True)
    parser.add_argument('--run-model', action='store_true')
    args = parser.parse_args()
    origin = urlsplit(args.base_url)
    if origin.scheme != 'https' or origin.path not in ('', '/') or origin.username or origin.password:
        parser.error('Use an HTTPS origin without credentials.')
    connection = http.client.HTTPSConnection(origin.hostname, origin.port or 443, timeout=12,
                    context=ssl.create_default_context(cafile=certifi.where()))
    records = []

    def call(label, method, path, token=None, body=None):
        headers = {'Accept': 'application/json'}
        if token: headers['Authorization'] = 'Bearer ' + token
        payload = None if body is None else json.dumps(body).encode()
        if payload is not None: headers['Content-Type'] = 'application/json'
        start = time.perf_counter()
        connection.request(method, path, payload, headers)
        response = connection.getresponse()
        raw = response.read()
        record = {'endpoint': label, 'status': response.status,
                  'totalMs': round((time.perf_counter()-start)*1000),
                  'requestId': response.getheader('X-Request-Id')}
        records.append(record)
        print(json.dumps(record), flush=True)
        if response.status != 200:
            raise RuntimeError(label + ' returned HTTP ' + str(response.status))
        return json.loads(raw)

    def has_han(value):
        return any('\u3400' <= char <= '\u9fff' for char in value)

    result = {'baseUrl': args.base_url, 'samples': records, 'success': False}
    try:
        auth = call('login', 'POST', '/api/auth/login', body={'username':'1234','password':'1234'})
        token = auth['accessToken']
        empty = call('endpoint_probe', 'POST', '/api/translations', token,
                     {'language':'zh-CN', 'postIds':[], 'commentIds':[]})
        assert not empty['pending'] and not empty['posts'] and not empty['comments']
        if args.run_model:
            posts = call('feed', 'GET', '/api/posts?forum=anu&size=20', token)['items']
            post = comment = None
            for candidate in [p for p in posts if p.get('title') and not has_han(p['title'])][:10]:
                comments = call('comments', 'GET', '/api/posts/'+candidate['id']+'/comments?size=20', token)['items']
                comment = next((c for c in comments if c.get('body') and not has_han(c['body'])), None)
                if comment is not None:
                    post = candidate
                    break
            assert post is not None and comment is not None, 'Both a live English post and comment are required.'
            body = {'language':'zh-CN','postIds':[post['id']],
                    'commentIds':[comment['id']]}
            translated = call('translation_first', 'POST', '/api/translations', token, body)
            result['firstPending'] = translated['pending']
            # The first miss must fit comfortably inside the Android 12s read timeout.
            assert records[-1]['totalMs'] < 5000
            deadline = time.monotonic() + 90
            while translated['pending'] and time.monotonic() < deadline:
                time.sleep(5)
                translated = call('translation_poll', 'POST', '/api/translations', token, body)
            assert not translated['pending'], 'Translation remained pending for 90 seconds.'
            assert len(translated['posts']) == 1 and has_han(translated['posts'][0]['title'])
            assert len(translated['comments']) == 1 and has_han(translated['comments'][0]['body'])
            cached = call('translation_cached', 'POST', '/api/translations', token, body)
            assert cached == translated, 'The next reader must receive the persisted cached result.'
            result['translatedPosts'] = len(translated['posts'])
            result['translatedComments'] = len(translated['comments'])
        result['success'] = True
    except Exception as error:
        result['errorCategory'] = type(error).__name__
        raise
    finally:
        connection.close()
        Path(args.output).write_text(json.dumps(result, indent=2))


if __name__ == '__main__':
    main()
