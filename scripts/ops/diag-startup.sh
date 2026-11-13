#!/usr/bin/env bash
# BabyLog 容器启动失败「一键定位」（只读脚本：不改容器状态、不打印任何口令）
#
# 为什么需要它：prod / test 两个 profile 下 logback-spring.xml 刻意不挂 ConsoleAppender
# （见 logback-spring.xml 顶部注释），所以 `docker logs` 永远只有 Spring 的 banner 一行，
# 真正的崩溃栈全在容器内 ${BABYLOG_LOG_DIR}/babylog-admin-server-error.log。
# 不知道这一点就会一直对着 banner 猜。
#
# 用法（在部署机宿主机上跑，需要 docker 权限）：
#   bash diag-startup.sh              # 容器名默认 babylog
#   bash diag-startup.sh babylog-test
#   bash diag-startup.sh babylog /opt/babylog/logs   # 第三个参数：宿主机已挂载的日志目录（有则直接用）
set -uo pipefail

C="${1:-babylog}"
HOST_LOG_DIR="${2:-}"
TMP="/tmp/bl-diag-$$"

mask() { sed -E 's/((PASSWORD|PASSWD|PASS|SECRET|KEY|TOKEN|ACCESS_TOKEN)[^=]*)=.*/\1=***/I'; }

echo "=================================================================="
echo " BabyLog 启动失败定位 · 容器=$C"
echo "=================================================================="

if ! docker inspect "$C" >/dev/null 2>&1; then
    echo "✗ 找不到容器 '$C'。先执行：docker ps -a --format '{{.Names}}\t{{.Image}}\t{{.Status}}'"
    exit 1
fi

echo
echo "---- ① 容器状态 ----------------------------------------------------"
docker inspect -f 'status={{.State.Status}} exit={{.State.ExitCode}} oomKilled={{.State.OOMKilled}} restarts={{.RestartCount}}
started={{.State.StartedAt}}  finished={{.State.FinishedAt}}' "$C"

echo
echo "---- ② 启动参数（口令已掩码）---------------------------------------"
docker inspect -f '{{range .Config.Env}}{{println .}}{{end}}' "$C" | grep -Ei 'SPRING|BABYLOG|SERVER_PORT|JAVA_|PROFILE|TZ' | mask | sed 's/^/  /'
echo "  ---"
docker inspect -f '  entrypoint={{.Config.Entrypoint}}
  cmd={{.Config.Cmd}}
  workdir={{.Config.WorkingDir}}
  image={{.Config.Image}}' "$C"

PROFILE_LINE=$(docker inspect -f '{{range .Config.Env}}{{println .}}{{end}}' "$C" | grep -Ei '^SPRING_PROFILES_ACTIVE=' || true)
echo
if [ -z "$PROFILE_LINE" ]; then
    echo "  ⚠ 没有 SPRING_PROFILES_ACTIVE —— 若启动命令也没带 --spring.profiles.active，"
    echo "    应用会用默认 profile（application.yml + dev），连的是开发库，不是你配的 prod。"
else
    echo "  生效 profile：${PROFILE_LINE#*=}"
    case "$PROFILE_LINE" in
        *prod*) ;;
        *test*) echo "  ⚠ 这是 test profile，不是 prod —— 先确认要部署的到底是哪套。" ;;
    esac
fi

# 字段加密键：名对了才生效，这里先判一次，省得启动后再从 error.log 里猜
KEYENV=$(docker inspect -f '{{range .Config.Env}}{{println .}}{{end}}' "$C" | grep -Ei 'FIELDENCRYPT|FIELD_ENCRYPT' || true)
if [ -n "$KEYENV" ]; then
    echo "  字段加密键：已注入 ${KEYENV%%=*}"
    case "$KEYENV" in
        BABYLOG_FIELD_ENCRYPT_KEY=*)
            echo "  ⚠ 名字不对：BABYLOG_FIELD_ENCRYPT_KEY 只能绑到 babylog.field-encrypt-key，"
            echo "    少一层 mybatis，实际不生效。改成 BABYLOG_MYBATIS_FIELDENCRYPTKEY=。" ;;
    esac
else
    echo "  字段加密键：未注入 —— prod 形态会解析到 application.yml 的 babylog-dev-key! 并拒绝启动"
fi

echo
echo "---- ③ 取日志 ------------------------------------------------------"
mkdir -p "$TMP"
GOT=0
if [ -n "$HOST_LOG_DIR" ] && [ -d "$HOST_LOG_DIR" ]; then
    cp -a "$HOST_LOG_DIR/." "$TMP/" 2>/dev/null && GOT=1 && echo "  来源：宿主机挂载目录 $HOST_LOG_DIR"
else
    for p in /app/logs /opt/babylog/logs /logs; do
        for try in 1 2 3 4 5; do
            # 容器正在重启循环时 docker cp 会偶发失败，重试几次足够穿过重启窗口
            if docker cp "$C:$p/." "$TMP/" 2>/dev/null; then
                GOT=1; echo "  来源：容器内 $p"; break
            fi
            sleep 2
        done
        [ "$GOT" = 1 ] && break
    done
fi

if [ "$GOT" = 0 ]; then
    echo "  ✗ 没取到日志文件。可能是日志目录不在上面三个候选路径（被 BABYLOG_LOG_DIR 改过），"
    echo "    或在重启循环里一直错过容器存活窗口。两种做法："
    echo "      a) docker inspect -f '{{range .Mounts}}{{.Source}} -> {{.Destination}}{{println}}{{end}}' $C   # 看挂了哪些目录"
    echo "      b) docker update --restart=no $C && docker start $C; sleep 8; bash $0 $C   # 先止血再取"
    echo "  另外：如果 /app/logs 整个不存在，说明容器 WORKDIR 不是 /app 且 yml 没配绝对路径。"
    exit 3
fi

ERR=$(ls -t "$TMP"/*error*.log 2>/dev/null | head -1)
MAIN=$(ls -t "$TMP"/*.log 2>/dev/null | grep -v 'error' | head -1)
echo
echo "  文件清单："; ls -la "$TMP" | sed 's/^/    /'

echo
echo "---- ④ 错误日志尾部（最后 60 行）------------------------------------"
if [ -n "$ERR" ]; then
    echo "  >> $(basename "$ERR")"
    tail -n 60 "$ERR" | sed 's/^/    /'
else
    echo "  ⚠ 没有 -error.log。若主文件里也没有 ERROR，多半是进程被杀（OOM）或没走到 Spring。"
    [ -n "$MAIN" ] && { echo "  >> $(basename "$MAIN")"; tail -n 40 "$MAIN" | sed 's/^/    /'; }
fi

echo
echo "---- ⑤ 已知故障模式匹配 -------------------------------------------"
ALL="$TMP/all.log"; cat "$TMP"/*.log > "$ALL" 2>/dev/null || true
HIT=0
check() { # $1=正则 $2=故障名 $3=处置
    if grep -Eqi -- "$1" "$ALL" 2>/dev/null; then
        HIT=$((HIT+1))
        echo "  [$HIT] ✗ $2"
        echo "      → $3"
        grep -Ei -- "$1" "$ALL" | tail -n 3 | cut -c1-220 | sed 's/^/        /'
        echo
    fi
}
check '字段加密密钥校验失败|field-encrypt-key' \
      'prod 缺字段加密密钥（FieldEncryptKeyGuard 拒绝启动）' \
      '容器环境变量名要用 BABYLOG_MYBATIS_FIELDENCRYPTKEY（= babylog.mybatis.field-encrypt-key 的宽松绑定）。
      注意 application.yml 里写的是字面量 babylog-dev-key!，不是 ${BABYLOG_FIELD_ENCRYPT_KEY:...} 占位，
      所以报错信息和 start-prod.sh 里那个短名 BABYLOG_FIELD_ENCRYPT_KEY 其实不生效，别照它填。
      值取 32 位：openssl rand -hex 16；一次定终身（写进数据后换密钥 = 老数据解不开）；
      密钥禁止写进 application-prod.yml —— server 的 GitHub 仓库是公开 fork。'
check '数据源与期望库不一致|期望库=' \
      '连到了和 expected-database 不一致的库（DataSourceUrlGuard 拒绝启动）' \
      '核对 application-prod.yml 的 spring.datasource.dynamic.datasource.master.url 库名与 babylog.mybatis.expected-database 是否一致；两者必须同名'
check 'Unknown database' \
      'JDBC URL 里的库在 MySQL 上不存在' \
      'CREATE DATABASE <库名> DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;（URL 带 createDatabaseIfNotExist=true 时才免这一步）'
check 'Access denied for user' \
      'MySQL 账号/口令/来源主机不对' \
      '用同一个账号在同一张网里手工连一次：docker exec -it <mysql> mysql -u<user> -p -h<host>；注意 MySQL 的 user@host 授权是按来源 IP 的'
check 'Communications link failure|Connection refused|UnknownHostException|connect timed out' \
      'MySQL 网络不通（地址/端口/防火墙/容器网络）' \
      '在应用容器里测：docker exec <app> sh -c "nc -vz <mysql-host> 3306"（无 nc 用 /dev/tcp）'
check 'NOAUTH|WRONGPASS|invalid password|Unable to connect to Redis|RedisConnectionFailure' \
      'Redis 口令或地址不对（test 环境第一次 exit 1 重启循环就是这条）' \
      '核对 1Panel 里 Redis 容器的 requirepass 与 prod yml 的 spring.data.redis.password；改完必须重启应用容器'
check 'Found more than one migration with version|Validate failed|Migration checksum mismatch|FlywayValidateException' \
      'Flyway 版本号重复或校验和不符' \
      '本地已跑 scripts/ops/check_migration_version.py（当前 PASS）；校验和不符多半是历史迁移文件被改过，需 flyway repair 或还原文件，别改 history 表硬来'
check "Unknown column 'tenant_id'|Unknown column .tenant_id." \
      '多租户开关被打开了（bl_ 表没有 tenant_id）' \
      'application-prod.yml 里 babylog.tenant.enabled 必须 false'
check 'Address already in use|BindException' \
      '容器内端口被占（或 SERVER__PORT 之类的环境变量把 server.port 顶掉了）' \
      '看 ② 里的 SERVER_PORT/SERVER__PORT；Spring 宽松绑定会把 SERVER__PORT 当 server.port'
check 'OutOfMemoryError|Killed' \
      'JVM 内存超过容器限额被杀' \
      '给 JAVA_OPTS 加 -XX:MaxRAMPercentage=75（镜像内默认不一定有），或抬高容器内存限额；exit=137 且 oomKilled=true 即命中'
check 'xxl-job|registry fail' \
      '调度中心不可达（通常只是 WARN，不致命）' \
      '先确认它没把启动一起带崩：看同一时间点是否有其它 ERROR；prod 若没部署 admin，把 babylog.job.enabled 关掉'
check 'Tesseract|tessdata|Native library' \
      'OCR 原生库缺失（历史上是红鲱鱼，不影响起服）' \
      '忽略；真要 OCR 再装 tesseract-ocr + 配 TESSDATA_PREFIX'

if [ "$HIT" = 0 ]; then
    echo "  没有命中预置模式 —— 把 ④ 的尾部 60 行原样贴出来再判。"
fi

echo
echo "---- ⑥ 结尾 --------------------------------------------------------"
echo "  完整日志副本留在：$TMP"
echo "  提醒：容器若被 1Panel 重建，容器内 /app/logs 会跟着消失 —— 排查期先给日志目录挂一个宿主机卷。"
