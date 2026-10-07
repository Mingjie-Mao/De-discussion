#!/usr/bin/env python3
"""Measure the demo API without logging credentials, tokens, or response bodies."""
import argparse
import http.client
import json
import pathlib
import ssl
import statistics
import time
import urllib.parse

import certifi

parser = argparse.ArgumentParser()
parser.add_argument('--base-url', required=True)
parser.add_argument('--samples', type=int, default=5)
parser.add_argument('--idle-seconds', type=int, default=0)
parser.add_argument('--output', required=True)
parser.add_argument('--author-pages', action='store_true', help='Measure the new author page endpoint after V18 deployment.')
args = parser.parse_args()
origin = urllib.parse.urlsplit(args.base_url)
if origin.scheme != 'https' or origin.path not in ('', '/'):
    parser.error('Use an HTTPS origin.')
context = ssl.create_default_context(cafile=certifi.where())
conn = None
records = []


def call(label, method, path, token=None, body=None):
    global conn
    start = time.perf_counter()
    connected = start
    status = 0
    request_id = None
    try:
        if conn is None or conn.sock is None:
            conn = http.client.HTTPSConnection(origin.hostname, origin.port or 443,
                                               timeout=35, context=context)
            conn.connect()
            connected = time.perf_counter()
        headers = {'Accept': 'application/json'}
        if token:
            headers['Authorization'] = 'Bearer ' + token
        payload = None
        if body is not None:
            payload = json.dumps(body).encode()
            headers['Content-Type'] = 'application/json'
        conn.request(method, path, payload, headers)
        response = conn.getresponse()
        status = response.status
        request_id = response.getheader('X-Request-Id')
        raw = response.read()
        data = json.loads(raw) if raw else None
        error = None if status < 400 else 'HTTP_' + str(status)
    except Exception as exc:
        error = type(exc).__name__
        data = None
        if conn:
            conn.close()
        conn = None
    record = {'endpoint': label, 'status': status,
              'connectMs': round((connected-start)*1000),
              'requestMs': round((time.perf_counter()-connected)*1000),
              'totalMs': round((time.perf_counter()-start)*1000),
              'requestId': request_id, 'error': error}
    records.append(record)
    print(json.dumps(record), flush=True)
    return data


try:
    call('readiness', 'GET', '/actuator/health/readiness')
    auth = call('login', 'POST', '/api/auth/login', body={'username':'1234','password':'1234'})
    if not auth or not auth.get('accessToken'):
        raise SystemExit('Login failed; partial timing evidence was saved, without credentials.')
    token = auth['accessToken']
    if args.idle_seconds:
        print(json.dumps({'idleSeconds':args.idle_seconds}), flush=True)
        time.sleep(args.idle_seconds)
        call('profile_after_idle', 'GET', '/api/users/me', token)
    feed = call('feed_discovery', 'GET', '/api/posts?forum=anu&size=20')
    if not feed or not feed.get('items'):
        raise SystemExit('Feed unavailable; partial timing evidence was saved, without response bodies.')
    post_id = feed['items'][0]['id']
    for _ in range(args.samples):
        call('readiness', 'GET', '/actuator/health/readiness')
        call('profile', 'GET', '/api/users/me', token)
        call('feed', 'GET', '/api/posts?forum=anu&size=20', token)
        call('comments', 'GET', '/api/posts/'+post_id+'/comments?size=20', token)
        call('feed_public', 'GET', '/api/posts?forum=anu&size=20')
        call('comments_public', 'GET', '/api/posts/'+post_id+'/comments?size=20')
        if args.author_pages:
            call('author_posts_public', 'GET', '/api/posts/authors/'+auth['userId']+'?size=30')
        call('state', 'POST', '/api/community/state', token,
             {'postIds':[post_id], 'commentIds':[], 'userIds':[]})
        call('market', 'GET', '/api/market', token)
        call('leaderboard', 'GET', '/api/market/leaderboard?page=0&size=30', token)
finally:
    # A failed login or feed is precisely when diagnosis matters: never discard it.
    summary = {}
    for label in dict.fromkeys(r['endpoint'] for r in records):
        group = [r for r in records if r['endpoint'] == label]
        good = [r['totalMs'] for r in group if r['error'] is None]
        summary[label] = {'samples':len(group), 'errors':sum(r['error'] is not None for r in group),
                          'medianMs':round(statistics.median(good)) if good else None,
                          'maxMs':max(good) if good else None}
    result = {'baseUrl':args.base_url, 'idleSeconds':args.idle_seconds,
              'measuredAtUtc':time.strftime('%Y-%m-%dT%H:%M:%SZ',time.gmtime()),
              'samples':records, 'summary':summary}
    pathlib.Path(args.output).write_text(json.dumps(result, indent=2))
    print(json.dumps({'summary':summary}))
    if conn:
        conn.close()
