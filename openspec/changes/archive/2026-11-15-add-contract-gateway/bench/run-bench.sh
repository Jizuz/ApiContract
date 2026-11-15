#!/usr/bin/env bash
# 任务26 压测验收脚本（可重复执行）：
#   基线组（反射 + 同步 HttpURLConnection）vs 优化组（字节码 + Reactor Netty
#   连接池）同 JVM 交替对照 + 索引刷新无尖刺场景，报告归档至本目录。
#
# 用法：
#   ./run-bench.sh                    # 默认参数（threads=8，3 轮）
#   THREADS=16 ROUNDS=3 ./run-bench.sh
#
# 参数说明（基线组短段 + 32s 段间冷却为 macOS loopback TIME_WAIT 端口预算
# 的物理约束，见报告"已知环境限制"）：
#   THREADS   压测并发线程数（默认 8）
#   ROUNDS    交替轮数（默认 3，best-of-N 口径）
#   BASE_MS   基线组每段测量时长 ms（默认 1500）
#   OPT_MS    优化组每段测量时长 ms（默认 3000）
#   COOLDOWN_MS 段间冷却 ms（默认 32000，须 ≥ TIME_WAIT 消退窗口）
set -euo pipefail

# bench → add-contract-gateway → changes → openspec → 仓库根（上溯 4 级）
cd "$(dirname "$0")/../../../.."
[ -f pom.xml ] || { echo "[run-bench] 未定位到仓库根（缺 pom.xml）: $(pwd)" >&2; exit 1; }

THREADS="${THREADS:-8}"
ROUNDS="${ROUNDS:-3}"
BASE_MS="${BASE_MS:-1200}"
OPT_MS="${OPT_MS:-3000}"
COOLDOWN_MS="${COOLDOWN_MS:-32000}"
REPORT_DIR="openspec/changes/add-contract-gateway/bench"
REPORT="$(pwd)/$REPORT_DIR/benchmark-report.md"
MVN="${MVN:-.tools/apache-maven-3.9.6/bin/mvn}"
[ -x "$MVN" ] || MVN="mvn"

echo "[run-bench] threads=$THREADS rounds=$ROUNDS base=${BASE_MS}ms opt=${OPT_MS}ms cooldown=${COOLDOWN_MS}ms"
echo "[run-bench] 报告归档: $REPORT_DIR/benchmark-report.md（绝对路径: ${REPORT}）"

"$MVN" -pl api-contract-it -am test \
  -Dtest=ContractBench \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dbench.threads="$THREADS" \
  -Dbench.rounds="$ROUNDS" \
  -Dbench.base.millis="$BASE_MS" \
  -Dbench.opt.millis="$OPT_MS" \
  -Dbench.cooldown.millis="$COOLDOWN_MS" \
  -Dbench.report="$REPORT"

echo "[run-bench] 完成，验收判定见报告末尾。"
# 清理历史版本相对路径在模块目录误生成的报告残留（仅限 api-contract-it/openspec）
[ -d api-contract-it/openspec ] && rm -rf api-contract-it/openspec
