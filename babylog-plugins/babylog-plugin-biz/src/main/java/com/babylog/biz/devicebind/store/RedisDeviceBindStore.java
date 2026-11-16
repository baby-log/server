package com.babylog.biz.devicebind.store;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/** Redis 设备码状态机。所有多键状态变更与领奖占位均由 Lua 原子完成。 */
@Component
public class RedisDeviceBindStore implements DeviceBindStore {

    private static final String USER_KEY = "bl:device-bind:user:";
    private static final String DEVICE_KEY = "bl:device-bind:device:";
    private static final String CLAIM_KEY = "bl:device-bind:claim:";
    private static final String CREATE_KEY = "bl:device-bind:create:";
    private static final String POLL_KEY = "bl:device-bind:poll:";
    private static final String CONFIRM_FAIL_USER_KEY = "bl:device-bind:confirm-fail:user:";
    private static final String CONFIRM_FAIL_IP_KEY = "bl:device-bind:confirm-fail:ip:";

    private static final DefaultRedisScript<Long> CREATE_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 1 or redis.call('EXISTS', KEYS[2]) == 1 then
                return 0
            end
            redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[2])
            redis.call('SET', KEYS[2], ARGV[3], 'PX', ARGV[2])
            return 1
            """, Long.class);

    private static final DefaultRedisScript<Long> TRANSITION_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) ~= ARGV[1] then
                return 0
            end
            local raw = redis.call('GET', KEYS[2])
            if not raw then
                return 0
            end
            local record = cjson.decode(raw)
            if record.status ~= 'PENDING' then
                return 0
            end
            record.status = ARGV[2]
            if ARGV[3] ~= '' then
                record.approvedBy = ARGV[3]
            end
            redis.call('SET', KEYS[2], cjson.encode(record), 'KEEPTTL')
            redis.call('DEL', KEYS[1])
            return 1
            """, Long.class);

    @SuppressWarnings("rawtypes")
    private static final DefaultRedisScript<List> CLAIM_SCRIPT = new DefaultRedisScript<>("""
            local raw = redis.call('GET', KEYS[1])
            if not raw then
                return {'expired', ''}
            end
            local record = cjson.decode(raw)
            if record.status == 'PENDING' then
                return {'pending', ''}
            end
            if record.status == 'DENIED' then
                return {'denied', ''}
            end
            if record.status == 'CONSUMED' then
                return {'consumed', ''}
            end
            if record.status ~= 'APPROVED' then
                return {'expired', ''}
            end
            if not redis.call('SET', KEYS[2], ARGV[1], 'NX', 'PX', ARGV[2]) then
                return {'busy', ''}
            end
            return {'approved', raw}
            """, List.class);

    private static final DefaultRedisScript<Long> FINISH_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[2]) ~= ARGV[1] then
                return 0
            end
            local raw = redis.call('GET', KEYS[1])
            if not raw then
                return 0
            end
            local record = cjson.decode(raw)
            if record.status ~= 'APPROVED' or record.approvedBy ~= ARGV[2] then
                return 0
            end
            record.status = 'CONSUMED'
            redis.call('SET', KEYS[1], cjson.encode(record), 'KEEPTTL')
            redis.call('DEL', KEYS[2])
            return 1
            """, Long.class);

    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            end
            return 0
            """, Long.class);

    private static final DefaultRedisScript<Long> INCREMENT_SCRIPT = new DefaultRedisScript<>("""
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
                redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return current
            """, Long.class);

    private static final DefaultRedisScript<Long> CONFIRM_FAIL_SCRIPT = new DefaultRedisScript<>("""
            local userCount = redis.call('INCR', KEYS[1])
            if userCount == 1 then
                redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            local ipCount = redis.call('INCR', KEYS[2])
            if ipCount == 1 then
                redis.call('PEXPIRE', KEYS[2], ARGV[1])
            end
            return userCount
            """, Long.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper = new ObjectMapper();

    public RedisDeviceBindStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean create(String userCodeHash, String deviceCodeHash,
                          DeviceBindRecord record, Duration ttl) {
        Long created = redis.execute(CREATE_SCRIPT,
                List.of(USER_KEY + userCodeHash, DEVICE_KEY + deviceCodeHash),
                deviceCodeHash, String.valueOf(ttl.toMillis()), write(record));
        return created != null && created == 1L;
    }

    @Override
    public Optional<String> resolveDeviceHash(String userCodeHash) {
        return Optional.ofNullable(redis.opsForValue().get(USER_KEY + userCodeHash));
    }

    @Override
    public boolean approve(String userCodeHash, String deviceCodeHash, long userId) {
        return transition(userCodeHash, deviceCodeHash,
                DeviceBindRecord.APPROVED, Long.toString(userId));
    }

    @Override
    public boolean deny(String userCodeHash, String deviceCodeHash) {
        return transition(userCodeHash, deviceCodeHash, DeviceBindRecord.DENIED, "");
    }

    private boolean transition(String userCodeHash, String deviceCodeHash,
                               String status, String approvedBy) {
        Long changed = redis.execute(TRANSITION_SCRIPT,
                List.of(USER_KEY + userCodeHash, DEVICE_KEY + deviceCodeHash),
                deviceCodeHash, status, approvedBy);
        return changed != null && changed == 1L;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public DeviceBindClaim beginClaim(String deviceCodeHash, String claimToken, Duration claimTtl) {
        List result = redis.execute(CLAIM_SCRIPT,
                List.of(DEVICE_KEY + deviceCodeHash, CLAIM_KEY + deviceCodeHash),
                claimToken, String.valueOf(claimTtl.toMillis()));
        if (result == null || result.size() < 2) {
            throw new IllegalStateException("设备码领奖脚本返回异常");
        }
        String outcome = String.valueOf(result.get(0));
        return switch (outcome) {
            case "pending" -> new DeviceBindClaim(DeviceBindClaim.Outcome.PENDING, null);
            case "approved" -> new DeviceBindClaim(DeviceBindClaim.Outcome.APPROVED,
                    read(String.valueOf(result.get(1))));
            case "denied" -> new DeviceBindClaim(DeviceBindClaim.Outcome.DENIED, null);
            case "consumed" -> new DeviceBindClaim(DeviceBindClaim.Outcome.CONSUMED, null);
            case "busy" -> new DeviceBindClaim(DeviceBindClaim.Outcome.BUSY, null);
            default -> new DeviceBindClaim(DeviceBindClaim.Outcome.EXPIRED, null);
        };
    }

    @Override
    public boolean finishClaim(String deviceCodeHash, String claimToken, String approvedBy) {
        Long finished = redis.execute(FINISH_SCRIPT,
                List.of(DEVICE_KEY + deviceCodeHash, CLAIM_KEY + deviceCodeHash),
                claimToken, approvedBy);
        return finished != null && finished == 1L;
    }

    @Override
    public void releaseClaim(String deviceCodeHash, String claimToken) {
        redis.execute(RELEASE_SCRIPT, List.of(CLAIM_KEY + deviceCodeHash), claimToken);
    }

    @Override
    public long increaseApplyCount(String ipHash, Duration window) {
        return increment(CREATE_KEY + ipHash, window);
    }

    @Override
    public boolean acquirePollSlot(String deviceCodeHash, Duration interval) {
        Boolean acquired = redis.opsForValue().setIfAbsent(
                POLL_KEY + deviceCodeHash, "1", interval);
        return Boolean.TRUE.equals(acquired);
    }

    @Override
    public long confirmUserFailures(long userId) {
        return count(CONFIRM_FAIL_USER_KEY + userId);
    }

    @Override
    public long confirmIpFailures(String ipHash) {
        return count(CONFIRM_FAIL_IP_KEY + ipHash);
    }

    @Override
    public void increaseConfirmFailures(long userId, String ipHash, Duration window) {
        redis.execute(CONFIRM_FAIL_SCRIPT,
                List.of(CONFIRM_FAIL_USER_KEY + userId, CONFIRM_FAIL_IP_KEY + ipHash),
                String.valueOf(window.toMillis()));
    }

    @Override
    public void clearConfirmFailures(long userId, String ipHash) {
        redis.delete(List.of(CONFIRM_FAIL_USER_KEY + userId, CONFIRM_FAIL_IP_KEY + ipHash));
    }

    private long increment(String key, Duration window) {
        Long count = redis.execute(INCREMENT_SCRIPT, List.of(key), String.valueOf(window.toMillis()));
        return count == null ? 0L : count;
    }

    private long count(String key) {
        String value = redis.opsForValue().get(key);
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private String write(DeviceBindRecord record) {
        try {
            return mapper.writeValueAsString(record);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("设备码记录序列化失败", e);
        }
    }

    private DeviceBindRecord read(String raw) {
        try {
            return mapper.readValue(raw, DeviceBindRecord.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("设备码记录反序列化失败", e);
        }
    }
}
