#!/usr/bin/env python3
"""真实 TTL 冒烟：以 --babylog.device-bind.code-ttl-seconds=2 启动 18080 后运行。"""
import json
import time
from urllib import request

BASE = 'http://127.0.0.1:18080/app/babylog/device-bind'
SUFFIX = str(int(time.time() * 1000))


def call(path, body):
    req = request.Request(BASE + path, method='POST', headers={'Content-Type': 'application/json'},
                          data=json.dumps(body).encode())
    with request.urlopen(req, timeout=30) as res:
        payload = json.loads(res.read())
    assert payload.get('code') == 0, payload
    return payload['data']


applied = call('/apply', {'device': {
    'deviceUuid': 'm45-real-ttl-' + SUFFIX,
    'deviceName': 'M45 TTL电视', 'platform': 'ANDROID_TV', 'appVersion': '0.1.0',
}})
assert applied['expiresIn'] == 2, {k: v for k, v in applied.items() if k not in ('userCode', 'deviceCode')}
pending = call('/poll', {'deviceCode': applied['deviceCode']})
assert pending['status'] == 'pending' and pending.get('login') is None, pending
print('PASS 短 TTL 申请确认前为 pending')
time.sleep(2.2)
expired = call('/poll', {'deviceCode': applied['deviceCode']})
assert expired['status'] == 'expired' and expired.get('login') is None, expired
print('PASS 真实 Redis TTL 到期后为 expired')
print('\nM45 real-TTL smoke: 2 passed / 0 failed')
