package com.babylog.biz.sync.store;

import com.babylog.biz.entity.BlChild;
import com.babylog.biz.sync.entity.BlChildSyncView;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M57 · 宝宝「喂养方式」跨端同步契约守卫。
 *
 * <p>这个字段走的是既有 children 同步通道，没有新端点、没有新表——列清单由
 * {@link SyncEntityMeta} 反射 {@link BlChildSyncView} 推导，加字段自动生效。
 * 但「自动」最容易悄悄失效：一旦有人把它误登记进 serverOwnedProperties，
 * 客户端推上去的值会被服务端忽略，四端宝宝管理改了也白改。这里把契约锁死。
 */
class ChildrenFeedingMethodContractTest {

    private static final Set<String> CHILDREN_SERVER_OWNED = Set.of("familyId");

    @Test
    void feedingMethodIsPushableAndPullable() {
        SyncEntityMeta meta = SyncEntityMeta.from(BlChildSyncView.class, CHILDREN_SERVER_OWNED);

        assertThat(meta.payloadProperties()).contains("feedingMethod");
        assertThat(meta.pullProperties()).contains("feedingMethod");
        // 服务端盖章列仍不得被客户端伪造，别因为加了新列就放宽旧红线
        assertThat(meta.payloadProperties()).doesNotContain("familyId", "ownerUserId", "userId");
    }

    @Test
    void entityDeclaresFeedingMethodAsString() throws Exception {
        // 枚举 name 字符串，不是序号；DB 列 feeding_method VARCHAR(16)
        assertThat(BlChild.class.getDeclaredField("feedingMethod").getType())
                .isEqualTo(String.class);
        assertThat(BlChildSyncView.class.getSuperclass()).isEqualTo(BlChild.class);
    }
}
