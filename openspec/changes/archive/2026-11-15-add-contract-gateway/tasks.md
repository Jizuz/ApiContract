# 开发任务清单
<!-- 基于Proposal、Spec、Design评审完成后生成；
任务粒度适合单PR，尽量小PR，避免大PR造成CR卡点；
每个任务尽量独立、可自测、可合并；
标记负责人、优先级、预估工时、关联spec
-->

> **TDD 强制约束（来自项目测试规范）**：每个任务必须按"红 → 绿 → 重构"执行——先写一个**真实断言**的失败测试并确认失败原因正确（实现不存在），再写最少实现让其通过，最后重构。禁止占位测试（`assert True` / 空测试体）。每完成一个任务在下方"任务细分说明"勾选对应项。任务顺序即依赖顺序，禁止跳过测试任务直接实现。

## 任务列表
| 序号 | 任务描述 | 关联spec | 优先级 | 预估工时 | 交付物 | 依赖任务 |
| ---- | -------- | -------- | ------ | -------- | ------ | -------- |
| 1 | 工程骨架与构建基线：四模块（api/provider-starter/gateway-starter/bom）、JDK17、Spring Boot 3、`-parameters` 构建期校验、依赖隔离 | contract-starter-access | P0 | 1d | 可构建的四模块工程 + 构建校验测试 | - |
| 2 | 契约注解与元数据模型：`@Contract`、ContractMeta、命名规范校验（先写注解约束失败测试） | contract-definition | P0 | 1d | api 模块注解/模型 + 校验器 + 测试 | 1 |
| 3 | 导出白名单加载器：export.txt 解析（注释/空行/非接口/类不存在 fail-fast）（先写解析分支失败测试） | contract-definition | P0 | 1d | 白名单加载器 + 测试 | 2 |
| 4 | 契约扫描器：Bean 遍历、真实类型解析（AOP 代理）、白名单匹配、@Contract 提取、签名一致性、多实现冲突、重载拒绝（先写扫描场景矩阵失败测试） | contract-definition, contract-provider-invoke | P0 | 2d | 扫描器 + 场景矩阵测试 | 3 |
| 5 | 调用器抽象与反射实现：ContractInvoker / ContractInvokerGenerator SPI、内置 reflection、异常剥离包装（先写调用器行为失败测试） | contract-provider-invoke | P0 | 1d | 调用器 SPI + 反射实现 + 测试 | 4 |
| 6 | 字节码调用器：ByteBuddy 生成、与反射实现共用同一契约测试套件、单契约失败降级、模式切换（先复用契约套件确认失败） | contract-provider-invoke | P0 | 2d | bytecode 生成器 + 降级策略 + 测试 | 5 |
| 7 | Provider 泛化入口：`/_contract/rpc`、参数反序列化、类型转换（日期/枚举/金额/集合/DTO）、个数校验、JSR303 可选、null 保持、四类错误码、堆栈脱敏（先写入口 12 场景失败测试） | contract-provider-invoke | P0 | 2d | 泛化入口 + 场景测试 | 6 |
| 8 | 元数据发布 SPI 与三种实现：ContractMetaPublisher（logOnly/nacosMetadata/nacosConfig）、发布顺序不变量（先写顺序破坏失败测试：发布失败断言不注册实例） | contract-metadata | P0 | 2d | 发布 SPI + 三实现 + 顺序不变量测试 | 4 |
| 9 | 发布自检与判重：自检日志四段（export loaded/contract ready/meta published/registry joined）、聚合索引冲突校验中断启动（先写自检断言失败测试） | contract-metadata, contract-definition | P0 | 1d | 自检日志 + 判重校验 + 测试 | 8 |
| 10 | 网关寻址 MVP：统一根路径、mt 解析、内存契约索引、参数提取（GET/POST 对象/数组/无参）、错误码（MT_MISSING/CONTRACT_NOT_FOUND/ARGUMENT_INVALID）、轮询 LB、同步调用（先写核心链路与错误码失败测试） | contract-gateway-routing | P0 | 2d | 网关核心链路 + 测试 | 2 |
| 11 | Starter 自动装配：双 Starter 条件装配、@ConditionalOnMissingBean、17 项配置项绑定、总开关、注册中心依赖 optional（先写装配条件失败测试） | contract-starter-access | P0 | 1d | 自动装配 + 配置绑定 + 测试 | 7,8,10 |
| 12 | 网关订阅与三级索引：订阅 group=CONTRACT、原子替换、清理已删契约、双索引分离、空实例 1s 缓存、刷新无尖刺（先写并发刷新失败测试） | contract-metadata | P0 | 2d | 三级索引订阅 + 并发刷新测试 | 11 |
| 13 | 实例订阅与地址列表：Discovery 订阅、10s 兜底校准、刷新失败沿用快照不清空、极简标记（先写快照兜底失败测试） | contract-metadata, contract-gateway-routing | P0 | 1d | 实例索引 + 兜底测试 | 12 |
| 14 | 多版本共存：版本后缀 dataId、mt+version 寻址、默认版本回退（先写多版本场景失败测试） | contract-metadata | P1 | 1d | 多版本支持 + 测试 | 12 |
| 15 | 并发发布防覆盖：版本号/内容哈希乐观比较（先写旧覆盖新失败测试） | contract-metadata | P1 | 1d | 乐观比较 + 测试 | 8 |
| 16 | 序列化 SPI 与 JSON 实现：ContractSerializer encode/decode、SERIALIZE_ERROR（先写编解码失败测试） | contract-serialization | P0 | 1d | 序列化 SPI + json 实现 + 测试 | 7 |
| 17 | Protobuf 实现与 schema 管理：schemaRef、字段编号规则校验、deprecated 保留（先写编解码往返与废弃字段兼容失败测试） | contract-serialization | P1 | 2d | protobuf 实现 + schema 校验 + 测试 | 16 |
| 18 | 契约级序列化切换与回退：三级优先级（契约>服务>全局）、两侧一致性启动校验、前端固定 JSON 形态转换（先写切换/回退/不一致失败测试） | contract-serialization | P1 | 1d | 切换策略 + 一致性校验 + 测试 | 17 |
| 19 | 异步客户端与连接池：Reactor Netty 可选依赖、有界等待队列、acquire-timeout 快速失败、三级超时、无依赖降级同步并告警（先写池耗尽/超时/降级失败测试） | contract-async-stability | P0 | 2d | 异步链路 + 连接池 + 测试 | 11,16 |
| 20 | 集群策略与重试：failover/failfast/failsafe、仅框架级异常重试、重试不绕过路由链（先写顺序断言失败测试） | contract-async-stability, contract-gateway-routing | P0 | 1d | 集群策略 + 顺序测试 | 19 |
| 21 | 熔断与限流：契约+实例维度失败率熔断（半开恢复）、契约级令牌桶/漏桶限流、429/503（先写熔断恢复与限流失败测试） | contract-async-stability | P1 | 2d | 熔断器 + 限流器 + 测试 | 19 |
| 22 | 过滤器链与 traceId 透传：ContractInvokeFilter（trace/log/metrics 链式按 order）、鉴权仅预留扩展点无内置实现（先写链序与透传失败测试） | contract-async-stability, contract-spi-extension | P1 | 1d | 过滤器链 + 透传测试 | 11 |
| 23 | SPI 机制统一装载：@Spi/SpiLoader、13 扩展点接口与内置实现注册、业务覆盖 + 启动覆盖提示、非法实现启动失败（先写装载/覆盖失败测试） | contract-spi-extension | P0 | 1d | SpiLoader + 扩展点注册 + 测试 | 5,8,16,19,20,21,22 |
| 24 | 可观测落地：契约维度 QPS/RT/错误率/熔断/重试/连接池指标、索引刷新耗时、告警规则（先写指标注册失败测试） | contract-async-stability | P1 | 1d | 指标 + 告警配置 + 测试 | 21,22 |
| 25 | 集成测试全链路：Testcontainers Nacos——发布→订阅→调用、12 错误码、降级矩阵、发布顺序破坏、多版本、单机调试模式（先写端到端失败测试） | 全部 spec | P0 | 2d | 集成测试套件 | 11-23 |
| 26 | 全链路压测与性能验收：基线（反射+JSON+同步）vs 优化（字节码+双序列化+异步）对照、QPS≥1.5×、P99 超时率<0.1%、连接复用、索引无尖刺 | contract-async-stability | P1 | 2d | 压测报告 + 验收结论 | 25 |
| 27 | 示例工程与接入文档：demo provider + 独立网关 + 前端模拟、三步接入验证、接入指南 | contract-starter-access | P2 | 1d | demo 工程 + 文档 | 25 |

### 任务细分说明
<!-- 每个任务内部细节、实现要点、需要注意的约束 -->

- [x] 任务1（工程骨架）：
  - 要点：`api-contract-bom` 统一版本；api 模块零 RPC 依赖；enforcer 校验 `-parameters` 编译参数与模块依赖方向；CI 绿色基线
  - 自测要点：构建校验测试先失败（无 enforcer 规则时）→ 加规则后通过；`mvn build` 四模块全绿
- [x] 任务2（注解与模型）：
  - 要点：`@Contract` 仅 METHOD 目标；命名规范 `业务域.动作` 全小写点分层；ContractMeta 12 字段与 design 接口契约一致
  - 自测要点：非法命名（大写/中文/空格）校验测试；注解目标约束测试
- [x] 任务3（白名单加载）：
  - 要点：只读本应用 classpath 一份、不跨 jar 合并；空清单不发布不注册入口；`export.fail-fast` 默认 true
  - 自测要点：注释/空行/非接口/类不存在四分支测试；fail-fast 开关两态测试
- [x] 任务4（契约扫描器）：
  - 要点：AopUtils 真实类型解析；白名单接口仅导出标注方法；签名不一致跳过并告警；多实现未指定唯一实现启动失败；重载拒绝
  - 自测要点：扫描场景矩阵（白名单×注解×实现 正交）先红后绿
- [x] 任务5（调用器 SPI 与反射实现）：
  - 要点：`ContractInvoker#invoke(Object[])`；异常剥离 InvocationTargetException 取真实异常；`mt → Invoker` 缓存
  - 自测要点：参数传递、返回值、业务异常剥离包装断言
- [x] 任务6（字节码调用器）：
  - 要点：ByteBuddy 启动期生成、绑定应用类加载器；单契约失败仅降级该契约；`invoker.mode` 一键切换；运行期不生成新类
  - 自测要点：与反射实现共用同一契约测试套件（先确认 bytecode 未实现时套件红）
- [x] 任务7（泛化入口）：
  - 要点：路径可配置避让业务路径；调用器缺失直接 CONTRACT_NOT_FOUND 无兜底；空值保持 null；错误响应无堆栈仅 code/message/traceId
  - 自测要点：12 场景（4 错误码 × 触发条件 + 各类型参数转换 + 无参 + null）先红后绿
- [x] 任务8（元数据发布 SPI）：
  - 要点：三种 publish-mode；**顺序不变量：发布成功才注册实例，失败中断启动**（CR 阻断项）
  - 自测要点：模拟发布失败断言不注册实例（先红）；三模式发布内容断言
- [x] 任务9（自检与判重）：
  - 要点：四段结构化日志作为上线验收依据；聚合索引跨服务判重、冲突中断启动
  - 自测要点：日志内容断言测试；重复 mt 发布中断测试
- [x] 任务10（网关 MVP）：
  - 要点：统一根路径 + `mt` query；三种参数形态提取；个数不符不发起远程调用；错误结构 `code/message/traceId/data`
  - 自测要点：核心链路测试 + MT_MISSING/CONTRACT_NOT_FOUND/ARGUMENT_INVALID 矩阵先红后绿
- [x] 任务11（Starter 装配）：
  - 要点：Spring Boot 3 自动配置导入清单；全部 Bean @ConditionalOnMissingBean；总开关关闭不装配；注册中心客户端 optional
  - 自测要点：ApplicationContextRunner 装配条件测试（开关两态、Bean 替换生效）
- [x] 任务12（三级索引）：
  - 要点：配置推送→按服务原子替换（清理已删契约）；契约索引与地址列表分离；空实例 1s 短缓存；条目上限监控
  - 自测要点：并发刷新无错误尖刺测试、已删契约不可达测试（先红）
- [x] 任务13（实例订阅）：
  - 要点：推送刷新 + 10s 定时兜底；刷新失败沿用最近快照不清空
  - 自测要点：快照兜底测试（模拟 Discovery 异常，先红）
- [x] 任务14（多版本共存）：
  - 要点：`{service}-contract-meta-{version}` 后缀；mt+version 精确寻址、无版本默认最新稳定
  - 自测要点：多版本共存寻址矩阵（先红）
- [x] 任务15（防覆盖）：
  - 要点：发布前读旧内容比较版本号/内容哈希；旧不覆盖新
  - 自测要点：旧版本迟到发布不覆盖新版本测试（先红）
- [x] 任务16（序列化 SPI 与 JSON）：
  - 要点：`ContractSerializer#encode/decode`；json 为默认实现；失败返回 SERIALIZE_ERROR 不静默
  - 自测要点：各参数类型编解码往返测试（先红）
- [x] 任务17（Protobuf）：
  - 要点：schemaRef 定位；字段编号只增不改不删；deprecated 保留；接口与 DTO 不变
  - 自测要点：编解码往返 + 废弃字段兼容 + 编号违规校验测试（先红）
- [x] 任务18（切换与回退）：
  - 要点：优先级 契约>服务>全局；两侧不一致启动校验失败；**前端↔网关恒 JSON（决策③），protobuf 仅网关↔Provider**
  - 自测要点：三级优先级取值、按契约回退、不一致启动失败测试（先红）
- [x] 任务19（异步与连接池）：
  - 要点：Reactor Netty 声明 optional；池参数（max-connections=500/每主机上限/max-idle=30s/acquire-timeout=1000ms）；**等待队列有界快速失败**；三级超时默认 3000ms 契约级覆盖；回调禁阻塞；未引入依赖降级同步+告警
  - 自测要点：池耗尽快速失败、整体超时、无依赖降级三测试（先红）
- [x] 任务20（集群策略）：
  - 要点：failover 默认重试 2 换实例；仅框架级异常重试；**调用链顺序固定：路由→限流→LB→调用→容错，重试不绕过路由**
  - 自测要点：顺序断言测试（重试请求仍过限流，先红）；failfast/failsafe 行为测试
- [x] 任务21（熔断限流）：
  - 要点：契约+实例维度失败率（0.5/最小 20）；半开探测恢复；契约级令牌桶/漏桶；429 RATE_LIMITED/503 CIRCUIT_OPEN；不同契约独立
  - 自测要点：熔断打开→半开→恢复全周期测试；限流拒绝测试（先红）
- [x] 任务22（过滤器链）：
  - 要点：SPI ContractInvokeFilter 按 order 链式；trace/log/metrics 三个内置；**鉴权仅预留扩展点，本期无内置实现（决策④）**
  - 自测要点：链序测试 + traceId 网关→Provider 透传断言（先红）
- [x] 任务23（SPI 统一装载）：
  - 要点：@Spi/SpiLoader；13 个扩展点接口清单与内置实现注册（见 contract-spi-extension spec）；业务实现覆盖内置 + 启动提示；非法实现启动失败
  - 自测要点：META-INF 装载、覆盖优先级、非法实现失败三组测试（先红）
- [x] 任务24（可观测）：
  - 要点：契约维度 QPS/RT/错误率/熔断/重试/连接池使用率、索引刷新耗时（秒级目标）；告警规则文件
  - 自测要点：指标注册与计数断言测试（先红）
- [x] 任务25（集成测试）：
  - 要点：Testcontainers Nacos 全链路；12 类错误码逐场景；降级矩阵逐项；发布顺序破坏断言不注册实例；多版本共存；单机调试模式（进程内直连）
  - 自测要点：端到端先红（Provider 发布→网关订阅→泛化调用成功）后逐项转绿
  - 完成记录：api-contract-it 模块（外部优先策略：探测本机 8848 → 回落 Testcontainers 容器）；EndToEndContractIT 6 用例（含 12 类错误码逐场景、ARGUMENT_INVALID 数组形式）、DegradationMatrixIT（fetcher 抛异常=注册中心不可达→沿用最近快照）、PublishOrderGuardIT（死地址桥 127.0.0.1:1 → 不注册实例）、MultiVersionStandaloneIT（v1/v2 共存、默认路由最新稳定版、精确版本寻址）；11/11 全绿，全仓 mvn clean verify BUILD SUCCESS；跨次运行残留由 removeContractConfig 清理保证幂等（防覆盖守卫会拒绝同版本不同内容）
- [x] 任务26（压测验收）：
  - 要点：基线组 vs 优化组对照；验收 QPS≥1.5×、P99 超时率<0.1%、连接复用稳定、索引刷新无尖刺；**阶段三/四之间的压测检查点**
  - 自测要点：压测脚本可重复执行、报告归档至变更目录
  - 完成记录：api-contract-it bench 包（ContractBench @Tag("benchmark") 默认不进套件，-Dtest=ContractBench 显式触发；同 JVM 双 Provider 进程交替对照：基线=反射+SyncHttpRemoteCaller、优化=字节码+ReactorNettyRemoteCaller 连接池）；BenchStats/AcceptanceVerdict 先红后绿 11 用例（统计口径与 1.5×/0.1% 红线固化）；run-bench.sh 可重复执行 + benchmark-report.md 归档本目录。验收结果：QPS 6.29×（5369→33768，红线 1.5×）、P99 超时率 0%、连接复用稳定（优化组 0 错误 P99 0.58ms）、刷新无尖刺（0/99481 失败、P99 劣化 1.20×≤2×）双判定通过。环境口径如实标注：序列化链路恒 JSON（决策③，protobuf 组件级未接 HTTP 入口）；macOS loopback TIME_WAIT 端口预算 → 基线短段+32s 冷却+best-of-N 过滤脏轮（生产 client/server 分离无此约束）
- [x] 任务27（示例与文档）：
  - 要点：demo provider + 独立网关 + 前端模拟（拼 `?mt=`）；三步接入指南；配置项表
  - 自测要点：按文档从零接入一遍可跑通
  - 完成记录：api-contract-samples 模块（demo-provider / demo-gateway / demo-frontend + README）。三步接入=①契约接口（@Contract("demo.user.get")，mt 命名全小写）+②白名单（META-INF/export.txt，含未导出反例 InternalContractApi 架构不可达）+③实现类（普通 @Service）；DemoProviderApplication 装配期编排白名单→扫描→字节码调用器，发布 Runner 先元数据后实例（logOnly 零依赖/nacosConfig 正式链路双模式）+自检四段日志；DemoGatewayApplication 装配三级索引订阅（group=CONTRACT）+实例注册表（10s 兜底）+ReactorNetty+统一根路径入口。TDD 先红后绿：DemoThreeStepIT 2 用例（泛化入口往返、白名单一票否决）、DemoGatewayWiringIT 2 用例（装配 Bean、空索引 404 CONTRACT_NOT_FOUND），红=应用类不存在，绿=4/4。run-demo.sh 一键验收 PASS（本机 Nacos：清残留→provider 自检四段→网关订阅→GET/POST ?mt= code=OK 数据往返→mt 拼错 404→进程清理与实例反注册无残留）。环境注记：demo 端口 18081/18090（8080/8081 被本机 Docker 占用，脚本预检友好报错）；bytecode 模式需显式引入 byte-buddy（starter optional，不引入可切 reflection，README 已标注）。全仓 mvn clean verify 9 模块 BUILD SUCCESS

## 单元测试要求
- 必须覆盖的场景：正向、分支、异常、幂等、边界
  - 正向：契约声明→扫描→发布→寻址→调用全链路成功路径
  - 分支：三种参数形态（GET query/POST 对象/POST 数组）、无参契约、null 值、多版本寻址
  - 异常：全部 12 类错误码逐条触发；降级矩阵逐项验证
  - 幂等：并发发布防覆盖、重复订阅推送、重试请求不产生副作用、索引重复刷新
  - 边界：空白名单、空实例列表（1s 缓存）、连接池耗尽、超时边界、单服务契约数上限
- **TDD 红线**：每个测试必须含真实断言，禁止 `assert True`/空测试体/跳过测试；先红后绿过程须可复现
- Mock 依赖方式：Nacos Config/Discovery 以 SPI 接口 + 内存 Fake 实现替换（集成测试用 Testcontainers 真实 Nacos）；HTTP 客户端用 MockWebServer；字节码调用器与反射实现共用契约测试套件

## 预检查清单（提CR前自检）
- [ ] CI流水线全部绿色通过
- [ ] 单元测试补充完成（含先红后绿记录）
- [ ] 日志、埋点、异常处理补齐（发布自检四段、调用器生成、索引刷新日志）
- [ ] 配置、限流、告警配置同步
- [ ] 数据库变更脚本单独评审（本变更无库表，不适用）
- [ ] 对照 design.md "CR 评审检查清单" 14 条 🔴 阻断项逐条自检

## 上线准备清单
- [ ] 数据库脚本评审（不适用：本变更无库表）
- [ ] 配置变更提交（Nacos `group=CONTRACT` 分组/命名空间；网关集群部署清单）
- [ ] 回滚脚本准备（五级开关回退演练：总开关/发布模式/调用器/序列化/异步）
- [ ] 告警规则配置（发布失败、索引刷新失败、熔断打开、连接池耗尽、超时率>0.1%）
- [ ] 测试回归完成（集成测试套件 + 压测验收报告归档）

## 任务备注
<!-- 其他协同事项、排期约束、外部依赖 -->
- **串行开发约束**：按序号顺序执行，任务 N 依赖任务全部完成后才可开始；每个任务一个 PR，避免大 CR
- **工时合计**：约 40 人日（P0 ≈ 27d / P1 ≈ 11d / P2 ≈ 1d，1d 内任务可合并为小 PR 拆分提交）
- **四阶段映射**：任务1-11=阶段一（契约基座）；12-15=阶段二（元数据重构）；16-18=阶段三（调用与序列化优化）；19-27=阶段四（异步与稳定性+验收）；**任务26 是阶段三/四之间压测检查点的前移落位，须在阶段四代码全量合入后执行**
- **决策硬约束**：独立网关为默认形态（任务10/27）；前端固定 JSON（任务18）；本期不做鉴权仅预留（任务22）；全量四阶段纳入（本清单完整覆盖）
- 协同方：前端（零改造确认）、业务团队（试点服务选择）、运维（Nacos 分组与网关部署）、制品库（BOM/api 发布）
- 冲突处理：实现中发现与 proposal/spec/design 分歧，立即停止并退回对应上游工件对齐，禁止在代码层私自抹平
