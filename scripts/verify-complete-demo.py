#!/usr/bin/env python3
"""Read-only full scenario acceptance. Tokens and imported passwords are never printed."""
import argparse,json,pathlib,ssl,urllib.request
import certifi
p=argparse.ArgumentParser();p.add_argument('--base-url',required=True);a=p.parse_args()
repo=pathlib.Path(__file__).resolve().parents[1];fixture=json.loads((repo/'scripts/fixtures/legacy-demo.json').read_text());private=json.loads((repo/'.local/complete-demo-credentials.json').read_text())
ctx=ssl.create_default_context(cafile=certifi.where());base=a.base_url.rstrip('/')
def call(method,path,token=None,body=None):
    headers={'Accept':'application/json'}
    if token:headers['Authorization']='Bearer '+token
    if body is not None:headers['Content-Type']='application/json'
    req=urllib.request.Request(base+path,data=None if body is None else json.dumps(body).encode(),headers=headers,method=method)
    with urllib.request.urlopen(req,context=ctx,timeout=45) as r:return json.load(r)
def login(name,password,role):
    auth=call('POST','/api/auth/login',body={'username':name,'password':password});token=auth['accessToken'];profile=call('GET','/api/users/me',token)
    assert profile['role']==role and profile['status']=='ACTIVE';return token,profile
member,profile=login('1234','1234','MEMBER');admin,_=login('12345','12345','ADMIN')
reader,_=login('demo_reader_01',private['demo_reader_01'],'MEMBER')
trader,tprofile=login('KoalaQuant',private['KoalaQuant'],'MEMBER')
posts=[]
for forum in ['anu','unsw','usyd','um']:posts+=call('GET','/api/posts?forum='+forum+'&size=100')['items']
assert len(posts)>=47
own=[v for v in posts if v['author']['id']==profile['id']];assert len(own)>=3
comments=call('GET','/api/community/users/'+profile['id']+'/comments?size=30',member);assert len(comments['items'])>=10
liked=call('GET','/api/community/posts?kind=LIKED&size=100',member)['items'];saved=call('GET','/api/community/posts?kind=BOOKMARKED&size=100',member)['items']
assert len(liked)>=5 and len(saved)>=5
following=call('GET','/api/community/users/'+profile['id']+'/following?size=100',member)['items'];followers=call('GET','/api/community/users/'+profile['id']+'/followers?size=100',member)['items'];assert len(following)>=3 and len(followers)>=9
state=call('POST','/api/community/state',member,{'postIds':[v['id'] for v in posts],'commentIds':[],'userIds':[profile['id']]})
assert all(v['score']>0 for v in state['posts'])
# The final image supplies database counts, even when no threads are loaded on a client.
assert any(v.get('comments',0)>0 for v in state['posts'])
alerts=call('GET','/api/notifications',member);types={v['type'] for v in alerts};assert {'LIKE','BOOKMARK','MENTION'}<=types
market=call('GET','/api/market',member);quotes=market['quotes'];assert len(quotes)==4
imported=sum(sum(c['source']=='IMPORTED_DEMO' for c in q['candles']) for q in quotes);assert imported==24
board=call('GET','/api/market/leaderboard?page=0&size=30',member)['items'];byname={v['username']:v for v in board}
for t in fixture['leaderboard']:
    row=byname[t['username']];assert row['totalAssets']==t['assets'] and row['activeDays']==t['activeDays']
history=call('GET','/api/market/trades?size=100',trader)['items'];assert len(history)==18 and all(v['source']=='IMPORTED_DEMO' for v in history)
# Existing administrator API remains usable after the migration and owner mapping.
call('GET','/api/admin/moderation-cases?size=5',admin)
result={'base':base,'auth_roles_verified':['MEMBER','ADMIN'],'generated_member_login':True,'imported_trader_login':True,'visible_posts':len(posts),'own_posts':len(own),'own_comments':len(comments['items']),'liked':len(liked),'saved':len(saved),'following':len(following),'followers':len(followers),'alerts':len(alerts),'market_quotes':len(quotes),'imported_candles':imported,'original_traders':len(fixture['leaderboard']),'trader_history_entries':len(history),'server_comment_counts':True,'admin_api_available':True}
(repo/'.local/complete-demo-api-verification.json').write_text(json.dumps(result,indent=2));print(json.dumps(result))
