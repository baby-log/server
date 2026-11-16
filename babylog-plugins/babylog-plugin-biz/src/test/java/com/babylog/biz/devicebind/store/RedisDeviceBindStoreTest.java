package com.babylog.biz.devicebind.store;

import com.babylog.biz.auth.dto.DeviceInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RedisDeviceBindStoreTest {

    @Mock
    private StringRedisTemplate redis;

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void createUsesHashedKeysAndStringApprovedByWithoutRawCodes() {
        doReturn(1L).when(redis).execute(any(RedisScript.class), anyList(), any(Object[].class));
        RedisDeviceBindStore store = new RedisDeviceBindStore(redis);
        DeviceInfo device = new DeviceInfo();
        device.setDeviceUuid("tv-1");
        DeviceBindRecord record = new DeviceBindRecord(DeviceBindRecord.APPROVED, device,
                "9007199254740993");

        boolean created = store.create("user-code-hash", "device-code-hash", record, Duration.ofSeconds(600));

        assertThat(created).isTrue();
        ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(redis).execute(any(RedisScript.class), keys.capture(), args.capture());
        assertThat(keys.getValue()).containsExactly(
                "bl:device-bind:user:user-code-hash", "bl:device-bind:device:device-code-hash");
        assertThat(args.getValue()[0]).isEqualTo("device-code-hash");
        assertThat(args.getValue()[1]).isEqualTo("600000");
        String json = (String) args.getValue()[2];
        assertThat(json).contains("\"approvedBy\":\"9007199254740993\"")
                .doesNotContain("012345", "raw-device-code");
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void claimResultKeepsLongUserIdAsExactString() throws Exception {
        DeviceInfo device = new DeviceInfo();
        device.setDeviceUuid("tv-1");
        String json = new ObjectMapper().writeValueAsString(
                new DeviceBindRecord(DeviceBindRecord.APPROVED, device, "9007199254740993"));
        doReturn(List.of("approved", json)).when(redis)
                .execute(any(RedisScript.class), anyList(), any(Object[].class));
        RedisDeviceBindStore store = new RedisDeviceBindStore(redis);

        DeviceBindClaim claim = store.beginClaim("device-code-hash", "claim-1", Duration.ofSeconds(30));

        assertThat(claim.outcome()).isEqualTo(DeviceBindClaim.Outcome.APPROVED);
        assertThat(claim.record().approvedBy()).isEqualTo("9007199254740993");
        assertThat(claim.record().device().getDeviceUuid()).isEqualTo("tv-1");
    }
}
