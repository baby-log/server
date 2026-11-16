#!/usr/bin/env python3
"""M45 设备码 dev 冒烟：先启动 18080，再运行本文件。

仅使用运行时随机账号；不读取环境 yml，不打印 token / userCode / deviceCode。
真实 TTL 用例见 m45-device-bind-expiry-smoke.py（单独以 2 秒 TTL 启动）。
"""
import concurrent.futures
import json
import threading
import time
from datetime import datetime
from urllib import error, request

BASE = 'http://127.0.0.1:18080/app/babylog'
NOW = int(time.time() * 1000)
TODAY0 = int(datetime.now().replace(hour=0, minute=0, second=0, microsecond=0).timestamp() * 1000)
SUFFIX = str(NOW)[-9:]
PASSWORD = 'M45-smoke-' + SUFFIX
IDS = NOW * 1000
PASSED = 0
FAILED = 0


def scrub(value):
    if isinstance(value, dict):
        return {k: ('<redacted>' if any(x in k.lower() for x in ('token', 'usercode', 'devicecode'))
                    else scrub(v)) for k, v in value.items()}
    if isinstance(value, list):
        return [scrub(v) for v in value]
    return value


def check(name, condition, actual=None):
    global PASSED, FAILED
    if condition:
        PASSED += 1
        print('PASS ' + name)
    else:
        FAILED += 1
        print('FAIL ' + name + (' | ' + repr(scrub(actual))[:700] if actual is not None else ''))


def result(method, path, token=None, body=None, ip=None):
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = token
    if ip:
        headers['X-Forwarded-For'] = ip
    req = request.Request(BASE + path, method=method, headers=headers,
                          data=json.dumps(body, ensure_ascii=False).encode() if body is not None else None)
    try:
        with request.urlopen(req, timeout=60) as res:
            raw = res.read()
            status = res.status
    except error.HTTPError as e:
        raw = e.read()
        status = e.code
    try:
        payload = json.loads(raw)
    except Exception as e:
        raise AssertionError(f'{method} {path} HTTP {status} 非 JSON：{raw[:200]!r}') from e
    return payload


def api(method, path, token=None, body=None, ip=None):
    payload = result(method, path, token, body, ip)
    if payload.get('code') != 0:
        raise AssertionError(f'{method} {path}：{scrub(payload)}')
    return payload.get('data')


def expect_code(name, expected, method, path, token=None, body=None, ip=None):
    payload = result(method, path, token, body, ip)
    check(name, payload.get('code') == expected, payload)
    return payload


def register(label):
    return api('POST', '/auth/register', body={
        'username': 'wx_m45' + label + SUFFIX,
        'password': PASSWORD,
        'nickname': 'M45' + label,
        'device': {'deviceUuid': 'm45-' + label + '-' + SUFFIX,
                   'deviceName': 'M45手机' + label, 'platform': 'ANDROID', 'appVersion': '2.4.0'},
    })


def new_id():
    global IDS
    IDS += 1
    return str(IDS)


def device(uuid):
    return {'deviceUuid': uuid, 'deviceName': 'M45客厅电视',
            'platform': 'ANDROID_TV', 'appVersion': '0.1.0'}


def apply_code(uuid, ip):
    return api('POST', '/device-bind/apply', body={'device': device(uuid)}, ip=ip)


def confirm(token, code, ip, extra=None):
    body = {'userCode': code}
    if extra:
        body.update(extra)
    return api('POST', '/device-bind/confirm', token, body, ip)


def bind(token, uuid, ip, extra=None):
    applied = apply_code(uuid, ip)
    confirm(token, applied['userCode'], ip, extra)
    polled = api('POST', '/device-bind/poll', body={'deviceCode': applied['deviceCode']}, ip=ip)
    assert polled['status'] == 'approved' and polled.get('login')
    return applied, polled['login']


def push(token, uuid, table, rows):
    return api('POST', '/sync/push', token, {'deviceUuid': uuid, 'changes': {table: rows}})


def base_row(child_id, created_by, created_at):
    return {'id': new_id(), 'childId': child_id, 'createdBy': str(created_by),
            'createdAt': str(created_at), 'updatedAt': str(created_at),
            'deleted': 0, 'version': 1, 'deviceId': 'm45-dev-smoke'}


def main():
    print('== 账号、家庭与完整绑定链路 ==')
    owner = register('a')
    viewer = register('b')
    stranger = register('c')
    check('三个运行时账号注册成功', all(x.get('accessToken') and x.get('refreshToken')
                                      for x in (owner, viewer, stranger)))
    owner_token = owner['accessToken']
    child_id = new_id()
    child = {'id': child_id, 'name': 'M45宝宝', 'gender': 'FEMALE', 'birthday': str(TODAY0 - 60 * 86400000),
             'isPremature': False, 'archived': False, 'createdAt': '1', 'updatedAt': '1',
             'deleted': 0, 'version': 1}
    created = push(owner_token, 'm45-owner-phone', 'children', [child])
    check('宝宝基础数据入库', created.get('upserted') == 1 and not created.get('rejected'), created)
    family = api('POST', '/family', owner_token, {'name': 'M45验收家庭'})
    member_id = family['memberId']
    joined = api('POST', '/family/join', viewer['accessToken'], {'inviteCode': family['inviteCode']})
    check('OWNER 建家、家人默认 VIEWER', family.get('role') == 'OWNER'
          and joined.get('role') == 'VIEWER' and bool(member_id), joined)
    members = api('GET', '/family/members', owner_token)
    viewer_member = next(r for r in members if str(r.get('userId')) == str(viewer['userId']))

    applied = apply_code('m45-main-tv-' + SUFFIX, '10.45.1.1')
    check('申请返回 6 位短码、高熵轮询码与 TTL', len(applied['userCode']) == 6
          and applied['userCode'].isdigit() and len(applied['deviceCode']) >= 40
          and applied['expiresIn'] == 600 and applied['pollIntervalSeconds'] == 5, applied)
    pending = api('POST', '/device-bind/poll', body={'deviceCode': applied['deviceCode']})
    check('确认前 poll=pending 且无 login', pending.get('status') == 'pending'
          and pending.get('retryAfterSeconds') == 5 and pending.get('login') is None, pending)
    expect_code('同 deviceCode 立即轮询被 8401 限流', 8401, 'POST', '/device-bind/poll',
                body={'deviceCode': applied['deviceCode']})
    expect_code('手机确认无 token 返回 1002', 1002, 'POST', '/device-bind/confirm',
                body={'userCode': applied['userCode']}, ip='10.45.1.1')
    confirmed = confirm(owner_token, applied['userCode'], '10.45.1.1')
    check('OWNER 手机确认成功', confirmed.get('status') == 'approved', confirmed)
    time.sleep(5.2)
    won = api('POST', '/device-bind/poll', body={'deviceCode': applied['deviceCode']})
    tv = won.get('login') or {}
    check('一次性 approved 返回原 LoginTokenVO', won.get('status') == 'approved'
          and str(tv.get('userId')) == str(owner['userId']) and tv.get('accessToken')
          and tv.get('refreshToken') and tv.get('workerId') is not None and tv.get('deviceId') is not None, won)
    check('真实用户 ID 超过 JS 安全整数仍精确绑定', int(owner['userId']) > 9007199254740991
          and str(tv.get('userId')) == str(owner['userId']))
    expect_code('已领奖 deviceCode 重放返回 8403', 8403, 'POST', '/device-bind/poll',
                body={'deviceCode': applied['deviceCode']})
    six_as_device = api('POST', '/device-bind/poll', body={'deviceCode': applied['userCode']})
    check('6 位 userCode 在 poll 只得到 expired', six_as_device.get('status') == 'expired'
          and six_as_device.get('login') is None, six_as_device)

    print('== TV token 读、写、刷新与家庭权限 ==')
    summary = api('GET', '/summary/today', tv['accessToken'])
    check('TV token 可读同家庭今日汇总', any(str(c.get('id') or c.get('childId')) == child_id
                                         for c in summary.get('children', [])), summary)
    feeding = base_row(child_id, member_id, NOW)
    feeding.update({'type': 'BOTTLE', 'startedAt': str(NOW), 'amountMl': 88,
                    'amountUnit': 'ML', 'inProgress': 0, 'note': 'M45-TV奶瓶'})
    pushed = push(tv['accessToken'], 'm45-main-tv-' + SUFFIX, 'feedings', [feeding])
    check('TV token 通过既有 push 写完整 feeding', pushed.get('upserted') == 1
          and not pushed.get('rejected'), pushed)
    pulled = api('GET', '/sync/changes?since=0&sinceId=0&limit=500&categories=FEEDING', owner_token)
    feedings = pulled.get('changes', {}).get('feedings', [])
    matched = next((r for r in feedings if str(r.get('id')) == feeding['id']), None)
    check('手机 OWNER 增量可见 TV 记录且 inProgress=0', matched is not None
          and matched.get('note') == 'M45-TV奶瓶' and int(matched.get('inProgress', -1)) == 0
          and str(matched.get('createdBy')) == str(member_id), matched)
    refreshed = api('POST', '/auth/refresh', body={'refreshToken': tv['refreshToken']})
    refreshed_summary = api('GET', '/summary/today', refreshed['accessToken'])
    check('TV refresh 后仍可访问同家庭数据', bool(refreshed_summary.get('children')))

    _, viewer_tv = bind(viewer['accessToken'], 'm45-viewer-tv-' + SUFFIX, '10.45.2.1')
    viewer_feeding = base_row(child_id, viewer_member['id'], NOW + 1)
    viewer_feeding.update({'type': 'BOTTLE', 'startedAt': str(NOW + 1), 'amountMl': 99,
                           'amountUnit': 'ML', 'inProgress': 0, 'note': 'M45-VIEWER越权'})
    denied_write = push(viewer_tv['accessToken'], 'm45-viewer-tv-' + SUFFIX, 'feedings', [viewer_feeding])
    check('VIEWER 的设备码 token 写记录仍被 8343 拒绝', denied_write.get('upserted') == 0
          and denied_write.get('rejected', [{}])[0].get('code') == 8343, denied_write)

    _, stranger_tv = bind(stranger['accessToken'], 'm45-stranger-tv-' + SUFFIX, '10.45.3.1',
                          extra={'userId': int(owner['userId'])})
    check('请求体伪造 userId 不改变确认者身份', str(stranger_tv.get('userId')) == str(stranger['userId'])
          and str(stranger_tv.get('userId')) != str(owner['userId']), stranger_tv)
    stranger_summary = api('GET', '/summary/today', stranger_tv['accessToken'])
    check('陌生账号设备码 token 看不到 OWNER 家庭', not stranger_summary.get('children'), stranger_summary)

    print('== 拒绝、过期、重绑、错误码与限流 ==')
    denied_apply = apply_code('m45-denied-tv-' + SUFFIX, '10.45.4.1')
    denied = api('POST', '/device-bind/deny', owner_token,
                 {'userCode': denied_apply['userCode']}, '10.45.4.1')
    check('手机可显式拒绝申请', denied.get('status') == 'denied', denied)
    denied_poll = api('POST', '/device-bind/poll', body={'deviceCode': denied_apply['deviceCode']})
    check('拒绝后 poll=denied 且无 login', denied_poll.get('status') == 'denied'
          and denied_poll.get('login') is None, denied_poll)
    expect_code('重复处理同一短码返回 8400', 8400, 'POST', '/device-bind/deny', owner_token,
                {'userCode': denied_apply['userCode']}, '10.45.4.1')

    missing_poll = api('POST', '/device-bind/poll', body={'deviceCode': 'm45-not-exist-' + SUFFIX})
    check('不存在的高熵 deviceCode 只返回 expired', missing_poll.get('status') == 'expired', missing_poll)

    _, rebound = bind(owner_token, 'm45-main-tv-' + SUFFIX, '10.45.6.1')
    check('同电视重绑复用 deviceId 与 workerId', str(rebound.get('deviceId')) == str(tv.get('deviceId'))
          and rebound.get('workerId') == tv.get('workerId'), rebound)
    expect_code('申请缺 deviceUuid 返回 1001', 1001, 'POST', '/device-bind/apply',
                body={'device': {'deviceName': 'bad'}}, ip='10.45.7.1')
    expect_code('userCode 非 6 位数字被参数校验拒绝', 1001, 'POST', '/device-bind/confirm', owner_token,
                {'userCode': '12ab'}, ip='10.45.7.1')

    lock_user = register('d')
    for i in range(5):
        expect_code(f'短码防爆破失败计数 {i + 1}/5', 8400, 'POST', '/device-bind/confirm',
                    lock_user['accessToken'], {'userCode': f'990{i:03d}'}, '10.45.8.1')
    expect_code('同账号失败 5 次后进入 8402 锁定', 8402, 'POST', '/device-bind/confirm',
                lock_user['accessToken'], {'userCode': '999999'}, '10.45.8.1')

    rate_codes = []
    rate_ip = '10.45.9.' + str(int(SUFFIX[-3:]) % 250 + 1)
    for i in range(5):
        rate_codes.append(apply_code(f'm45-rate-{i}-' + SUFFIX, rate_ip))
    check('同 IP 每分钟前 5 次申请可用', len(rate_codes) == 5)
    expect_code('同 IP 第 6 次申请返回 8401', 8401, 'POST', '/device-bind/apply',
                body={'device': device('m45-rate-5-' + SUFFIX)}, ip=rate_ip)

    print('== 真实 Redis 并发领奖 ==')
    concurrent_apply = apply_code('m45-concurrent-tv-' + SUFFIX, '10.45.10.1')
    confirm(owner_token, concurrent_apply['userCode'], '10.45.10.1')
    barrier = threading.Barrier(3)

    def concurrent_poll():
        barrier.wait()
        return result('POST', '/device-bind/poll', body={'deviceCode': concurrent_apply['deviceCode']})

    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        futures = [pool.submit(concurrent_poll) for _ in range(2)]
        barrier.wait()
        outcomes = [f.result() for f in futures]
    approved = [p for p in outcomes if p.get('code') == 0
                and (p.get('data') or {}).get('status') == 'approved']
    check('两个并发 poll 只有一个获得 token', len(approved) == 1
          and approved[0]['data'].get('login', {}).get('accessToken'), outcomes)
    check('另一侧只见 pending 或一次性重放 8403', all(
        (p.get('code') == 8403) or (p.get('code') == 0
                                   and (p.get('data') or {}).get('status') in ('pending', 'approved'))
        for p in outcomes), outcomes)


if __name__ == '__main__':
    try:
        main()
    except Exception as exc:
        FAILED += 1
        print('FAIL 冒烟异常：' + repr(scrub(exc)))
    print(f'\nM45 device-bind dev smoke: {PASSED} passed / {FAILED} failed')
    raise SystemExit(1 if FAILED else 0)
