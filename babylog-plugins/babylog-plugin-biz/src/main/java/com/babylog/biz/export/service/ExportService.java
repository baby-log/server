package com.babylog.biz.export.service;

import java.io.IOException;
import java.io.OutputStream;

/** 家庭数据全量导出：每次调用都重新解析登录账号的可见范围与读权限。 */
public interface ExportService {

    /** Android JsonImporter 兼容 JSON：全字段、含软删、epoch millis。 */
    void writeJson(long userId, boolean includeReminders, OutputStream out) throws IOException;

    /** Android 会话 G 同规格 CSV ZIP：有效行、中文表头、按宝宝拆文件。 */
    void writeCsvZip(long userId, boolean includeReminders, OutputStream out) throws IOException;
}
