#!/usr/bin/env python3
"""Opt-in acceptance of real administrator evidence translations.

Uses public demo accounts. Only reads cases and fills the existing model cache;
does not edit forum content, decisions, accounts or audit records. Tokens stay in
memory and the report contains counts/timings rather than credentials/content.
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
    parser.add_argument('--run-model', action='store_true', required=True)
    args = parser.parse_args()
    origin = urlsplit(args.base_url)
    if origin.scheme != 'https' or origin.path not in ('', '/') or origin.username or origin.password:
        parser.error('Use an HTTPS origin without credentials.')
    samples = []
    report = {'baseUrl': args.base_url, 'samples': samples, 'success': False}

    def call(label, method, path, token=None, body=None, expected=200):
        conn = http.client.HTTPSConnection(origin.hostname, origin.port or 443, timeout=12,
                    context=ssl.create_default_context(cafile=certifi.where()))
        headers = {'Accept': 'application/json'}
        if token: headers['Authorization'] = 'Bearer ' + token
        payload = None if body is None else json.dumps(body).encode()
        if payload is not None: headers['Content-Type'] = 'application/json'
        start = time.perf_counter()
        try:
            conn.request(method, path, payload, headers)
            response = conn.getresponse()
            raw = response.read()
            sample = {'endpoint': label, 'status': response.status,
                      'totalMs': round((time.perf_counter()-start)*1000),
                      'requestId': response.getheader('X-Request-Id')}
            samples.append(sample)
            print(json.dumps(sample), flush=True)
            if response.status != expected:
                raise RuntimeError(label + ' returned HTTP ' + str(response.status))
            return json.loads(raw) if raw else None
        finally:
            conn.close()

    def compact(value, limit):
        value = ' '.join((value or '').split())
        return value if len(value) <= limit else value[:limit] + '…'

    def han(value):
        return any('\u3400' <= c <= '\u9fff' for c in (value or ''))

    try:
        health = call('readiness', 'GET', '/actuator/health/readiness')
        assert health['status'] == 'UP'
        token = call('admin_login', 'POST', '/api/auth/login',
                     body={'username':'12345','password':'12345'})['accessToken']
        empty = {'language':'zh-CN','caseIds':[]}
        call('anonymous_denied', 'POST', '/api/admin/translations', body=empty, expected=401)
        member = call('member_login', 'POST', '/api/auth/login',
                      body={'username':'1234','password':'1234'})['accessToken']
        call('member_denied', 'POST', '/api/admin/translations', member, empty, expected=403)
        cases = call('queue', 'GET', '/api/admin/moderation-cases?status=AWAITING_REVIEW&size=30', token)
        assert cases, 'Existing cases required; this script does not create reports.'
        body = {'language':'zh-CN','caseIds':[row['id'] for row in cases]}
        translated = call('first_translation', 'POST', '/api/admin/translations', token, body)
        report['firstPending'] = translated['pending']
        assert samples[-1]['totalMs'] < 5000, 'Cache miss must return without blocking on the model.'
        deadline = time.monotonic() + 90
        while translated['pending'] and time.monotonic() < deadline:
            time.sleep(5)
            translated = call('translation_poll', 'POST', '/api/admin/translations', token, body)
        assert not translated['pending'], 'Translation pending after 90 seconds.'
        by_id = {row['id']:row for row in translated['cases']}
        assert len(by_id) == len(cases)
        for row in cases:
            item = by_id[row['id']]
            assert item['sourceTitle'] == compact(row.get('contentTitle'), 120)
            assert item['sourcePreview'] == compact(row.get('contentPreview'), 180)
            assert han(item.get('body') or item.get('title')), 'Chinese evidence required.'
        cached = call('cached_translation', 'POST', '/api/admin/translations', token, body)
        assert cached == translated
        report['translatedCases'] = len(by_id)
        report['sourceVersionMatches'] = True
        report['cacheReadbackMatches'] = True
        report['success'] = True
    except Exception as error:
        report['errorCategory'] = type(error).__name__
        raise
    finally:
        Path(args.output).write_text(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
