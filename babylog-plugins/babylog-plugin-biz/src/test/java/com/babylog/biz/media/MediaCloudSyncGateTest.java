package com.babylog.biz.media;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.babylog.biz.media.controller.BabyLogMediaController;
import com.babylog.biz.media.dto.MediaConfirmCmd;
import com.babylog.biz.media.dto.MediaUploadCmd;
import com.babylog.biz.sync.support.SyncTable;
import com.babylog.starter.auth.account.StpAppUtil;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Field;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * K8 / M79 画面云同步开关解锁 · 服务端门禁证明性单测。
 *
 * <p>本节点 <b>零业务端点改动</b>：端点逐字对齐 M69 文档，已开云同步用户主动选择的静态画面
 * 经 K2 底座上传。服务端「已开云同步」的唯一等价判据 = 有效 app-user token（类级登录注解），
 * <b>不自建第二套开关状态</b>；四端总开关关闭时须清凭证 / 停同步，使 token 失效。
 * 本测试把这条等价性、user_id 盖章、两表不进同步通道与 7 条端点路径锁死为可回归断言。
 *
 * <p>说明：本类只做反射断言，不启动 Spring 上下文；上传行为（画面帧走底座、video 拒收、
 * 两步删、配额淘汰）由同包既有的 MediaFileServiceTest / MediaRetentionServiceTest 覆盖。
 */
class MediaCloudSyncGateTest {

    @Test
    void mediaControllerRequiresAppUserLoginForAllEndpoints() {
        SaCheckLogin login = BabyLogMediaController.class.getAnnotation(SaCheckLogin.class);
        assertThat(login).as("媒体端点类级登录注解").isNotNull();
        assertThat(login.type()).isEqualTo(StpAppUtil.TYPE);
    }

    @Test
    void uploadCmdNeverAcceptsClientSuppliedUserId() {
        assertThat(Arrays.stream(MediaUploadCmd.class.getDeclaredFields())
                .map(Field::getName))
                .doesNotContain("userId");
    }

    @Test
    void mediaTablesStayOutOfSyncChannel() {
        assertThat(Arrays.stream(SyncTable.values()).map(SyncTable::tableName))
                .doesNotContain("bl_media_file", "bl_media_quota");
        assertThat(SyncTable.values()).hasSize(19);
    }

    @Test
    void appMediaEndpointPathsStayAlignedWithM69() throws Exception {
        assertThat(BabyLogMediaController.class.getAnnotation(RequestMapping.class).value())
                .containsExactly("/app/babylog/media");

        assertThat(BabyLogMediaController.class
                .getDeclaredMethod("upload", MultipartFile.class, MediaUploadCmd.class)
                .getAnnotation(PostMapping.class).value()).containsExactly("/upload");
        assertThat(BabyLogMediaController.class.getDeclaredMethod("get", Long.class)
                .getAnnotation(GetMapping.class).value()).containsExactly("/file/{id}");
        assertThat(BabyLogMediaController.class.getDeclaredMethod("list", Long.class)
                .getAnnotation(GetMapping.class).value()).containsExactly("/files");
        assertThat(BabyLogMediaController.class.getDeclaredMethod("content", Long.class)
                .getAnnotation(GetMapping.class).value()).containsExactly("/file/{id}/content");
        assertThat(BabyLogMediaController.class.getDeclaredMethod("delete", Long.class)
                .getAnnotation(DeleteMapping.class).value()).containsExactly("/file/{id}");
        assertThat(BabyLogMediaController.class
                .getDeclaredMethod("confirm", Long.class, MediaConfirmCmd.class)
                .getAnnotation(PostMapping.class).value()).containsExactly("/file/{id}/confirm");
        assertThat(BabyLogMediaController.class.getDeclaredMethod("quota", Long.class)
                .getAnnotation(GetMapping.class).value()).containsExactly("/quota");
    }
}
