package com.babylog.starter.tenant.it;

/**
 * 集成测试的中间件地址常量（配置口径：全部内联 yml，不再走环境变量注入）。
 *
 * <p>历史：此前 CI 的 test job 会起自己的 MySQL / Redis 容器并注入
 * {@code BABYLOG_TEST_*} 环境变量，本类按「env 优先、字面量兜底」解析，
 * 并由 {@code scripts/ops/check_ci_wiring.py} 守护与 yml 的一致性。
 * 2026-11-12 起项目废除 .env / 环境变量注入，测试配置全部内联在各模块
 * {@code src/test/resources/application.yml}；本类只保留直连核对数据落位
 * 需要的主机/端口常量——改 yml 时同步改这里。
 */
final class TestDbDefaults {

    /** dev 中间件主机（与各测试 yml 数据源一致）。 */
    static final String MYSQL_HOST = "175.24.176.176";

    /** dev 中间件端口（与各测试 yml 数据源一致）。 */
    static final String MYSQL_PORT = "3306";

    private TestDbDefaults() {
    }

    /** 测试中间件主机。 */
    static String mysqlHost() {
        return MYSQL_HOST;
    }

    /** 测试中间件端口。 */
    static String mysqlPort() {
        return MYSQL_PORT;
    }

    /**
     * 拼出完整的 JDBC URL。
     *
     * @param database 目标库名
     * @param createDatabaseIfNotExist 是否带 {@code createDatabaseIfNotExist=true}：
     *        数据源由 dynamic-datasource 创建时需要（容器里库还没建），
     *        测试自己用裸 JDBC 核对数据落位时**不要**带——多一个参数会导致 URL 字符串
     *        与连接池里的不匹配，进而连到另一个库上去。
     */
    static String jdbcUrl(String database, boolean createDatabaseIfNotExist) {
        String base = "jdbc:mysql://" + mysqlHost() + ":" + mysqlPort() + "/" + database
                + "?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai"
                + "&useSSL=false&allowPublicKeyRetrieval=true";
        return createDatabaseIfNotExist ? base + "&createDatabaseIfNotExist=true" : base;
    }
}
