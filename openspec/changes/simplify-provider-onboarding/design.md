# 技术方案：简化 Provider 接入（启动编排内置 + 官方 Nacos 适配）

## 上下文
- 关联提案：`simplify-provider-onboarding/proposal.md`
- 关联 spec：`contract-starter-access`（修改基线）、`contract-nacos-adapter`（新增）
- 依赖现状：四件套（bom/api/provider-starter/gateway-starter）+ it + samples 已全量交付（变更 `add-contract-gateway` 归档）；`ApiContractAutoConfiguration` 仅提供缺省件（ObjectMapper / MetaPublishers.select 结果 / 空注册表兜底 `ContractRpcHandler`），编排由 demo-provider 手抄（`DemoProviderApplication` 装配期扫描 + `DemoPublishRunner` 发布注册 + 容器关闭反注册；`DemoNacosBridge` 手写 ConfigCarrierWriter 与原生 nacos-client 实例注册）
- 架构约束（延续归档决策）：api 模块零第三方依赖（enforcer 强制）；provider/gateway 双 starter 禁互依；BOM 版本锁定；注册中心客户端不强绑 starter（optional）；发布顺序不变量「先元数据后实例、任一失败中断启动」

## 目标 & 非目标
✅ 技术目标
- 业务接入 = 1 依赖 + 三步 + 配置；编排与 Nacos 桥接零代码
- 内置编排与业务自定义编排互斥（防双发布），且旧形态（手抄编排）升级后行为不变
- 发布顺序不变量、优雅反注册、自检四段日志语义与 demo 手抄形态完全一致
- 全仓 `mvn clean verify` 绿（新增 adapter 模块并入 reactor 与 BOM）

❌ 本次不做的技术改造
- gateway-starter 任何改动；发布协议/元数据格式变更
- 引入 spring-cloud-alibaba；白名单热重扫；多注册中心抽象（仅 Nacos）

## 方案选型与对比
- 方案A：编排全部收进 ApplicationRunner（runner 内扫描→生成→发布→注册）
  优点：实现最简；缺点：扫描与调用器生成放运行期太晚，泛化入口在 runner 前就绪窗口内空转，且白名单快速失败语义推迟到运行期
- 方案B：SmartInitializingSingleton 完成扫描与调用器生成（容器单例就绪后、Runner 前），ApplicationRunner 完成发布与注册（Web 端口已定）
  优点：与 demo 已验证时序完全一致（装配期产物、Runner 期发布）；白名单解析失败仍在启动早期中断；`-parameters` 告警时机不变
- 方案C：编排 Bean 化为多个独立 @Bean（扫描器/发布器分体）
  缺点：分体 Bean 间隐式时序约束难表达，业务替换面变大易错
- 最终选型：**方案B**。时机语义与 demo 逐点对齐，风险最低。

Nacos 承载选型：原生 nacos-client 直接装配（不用 spring-cloud-alibaba）。理由：延续「客户端版本业务统一管理」决策，adapter 的 nacos-client 依赖版本可被业务 dependencyManagement 覆盖；引入 spring-cloud-alibaba 会破坏 provider-starter 的依赖极简承诺。

## 架构设计
```mermaid
flowchart LR
  subgraph provider-starter
    AC[ApiContractAutoConfiguration<br/>缺省件: ObjectMapper/MetaPublisher/兜底Handler]
    ORCH[内置编排 ApiContractOrchestrator<br/>@ConditionalOnMissingBean]
    IR[InstanceRegistrar 接口<br/>register/deregister]
    AC --> ORCH
    ORCH -.optional.-> IR
  end
  subgraph adapter1[nacos-adapter 新模块]
    NAC[ApiContractNacosAutoConfiguration<br/>@ConditionalOnClass+server-addr]
    CCW[ConfigCarrierWriter<br/>dataId={svc}-contract-meta group=CONTRACT]
    NIR[NacosInstanceRegistrar<br/>NamingService 注册/反注册]
    NAC --> CCW
    NAC --> NIR
  end
  APP[业务应用<br/>三步接入+配置] --> provider-starter
  APP -->|optional| adapter1
  NIR -.实现.-> IR
  CCW -.实现.-> starter 发布端口
```
- **内置编排器** `ApiContractOrchestrator`（provider-starter，公共类型接口）：
  - 装配条件：总开关开启 且 `@ConditionalOnMissingBean(ApiContractOrchestrator.class)`——业务（或旧形态 demo 手抄编排，改造为实现该接口）定义即整体禁用内置编排，防双发布
  - `SmartInitializingSingleton#afterSingletonsInstantiated`：白名单加载（快速失败）→ 容器扫描契约实现 Bean → 调用器生成（bytecode 优先缺反射）→ 构建 registry 与契约元数据
  - `ApplicationRunner#run`（Web 端口已定）：发布元数据（复用 `MetaPublishers.select` 产物）→ 成功后实例注册（`ObjectProvider<InstanceRegistrar>`，缺省跳过）→ 自检四段日志；任一步失败抛异常中断启动
  - `DisposableBean#destroy`：实例反注册（幂等，句柄由编排器持有）
  - 后填充：编排完成后对容器内 `ContractRpcHandler` 执行一次性 `reset(registry, metas)`；handler 以单一 volatile 引用持有注册表与元数据快照（启动期单写、运行期并发读不撕裂）——handler 现有构造签名与行为保持兼容
  - **实例注册抽象** `InstanceRegistrar`（沿用 provider-starter 既有接口，补 `default deregister()`：starter 不感知 Nacos，adapter 提供实现）；`InstanceMetadataWriter`（实例元数据发布模式）语义不变
  - **Nacos 适配模块** `api-contract-nacos-adapter`：
    - 依赖：provider-starter（compile，传递 api）+ nacos-client（compile，版本经 property 定义、业务可覆盖）+ spring-boot-autoconfigure；自身加入根 reactor 与 BOM
    - `ApiContractNacosAutoConfiguration`：`@ConditionalOnClass(NacosConfigService)` + `@ConditionalOnProperty("api-contract.nacos.server-addr")`；未配地址不装配、不阻断（回退 logOnly 由 starter 缺省件承担）并输出告警日志
    - 装配 `ConfigCarrierWriter`（NacosConfigService.publishConfig，dataId=`{服务名}-contract-meta`、group=`CONTRACT`，服务名取 `spring.application.name`）与 `NacosInstanceRegistrar`（NamingService.registerInstance/deregisterInstance，metadata 极简：契约版本+契约数）
  - **demo-provider 切换**：删除主路径手抄编排，改依赖内置编排 + adapter；原手抄编排类保留为实现 `ApiContractOrchestrator` 的「业务整体替换」示例（`manual` profile 隔离，默认不生效，用于验证互斥语义与向后兼容）

## 存储设计
### MySQL表变更
- 无（本变更无数据库）
### Redis缓存设计
- 无
### ES/其他存储（按需）
- Nacos 配置：沿用既有布局（`CONTRACT` 分组 / `{服务名}-contract-meta`），内容格式不变；实例元数据仅契约版本与契约数量两项标记

## 接口契约
- 新增公共类型：`com.apicontract.provider.orchestration.ApiContractOrchestrator`（接口）、`com.apicontract.provider.publish.InstanceRegistrar`（接口）
- `ContractRpcHandler`：新增 `reset(...)` 公共方法；既有构造与行为不变（可替换语义不变）
- 配置新增：`api-contract.orchestration.enabled`（默认 true，关内置编排）、`api-contract.nacos.server-addr / namespace / group`（默认公共命名空间 / `CONTRACT`）
- HTTP 契约（泛化入口、错误码、元数据结构）：零变更
- 兼容策略：旧手抄编排（含 demo 手抄形态）无需修改即可继续运行——定义了编排组件的应用自动禁用内置编排

## 异步流程设计
- 无新增异步流程；发布/注册仍为启动期同步一次性动作（顺序不变量）

## 分布式事务 & 一致性策略
- 延续发布顺序不变量（先元数据后实例、失败中断、关闭反注册）；多实例并发发布沿用既有防覆盖比较规则，本变更不改

## 限流、熔断、降级（Sentinel）
- 不涉及（网关侧既有配置不变）

## 数据迁移 & 灰度策略
- 无数据迁移；Nacos 无存量格式变更
- 灰度路径：内置编排默认开启即新形态；存量手抄编排应用按「业务定义编排组件→内置自动禁用」自然兼容，无需开关切换
- 回滚路径：业务设置 `api-contract.orchestration.enabled=false` 或声明自定义编排即回到手抄形态；adapter 不引入即回 logOnly

## 风险点、降级、回滚方案
| 风险 | 影响等级 | 降级方案 | 回滚方式 |
| ---- | -------- | -------- | -------- |
| 双重发布（业务编排与内置编排同时生效） | 🔴 高 | 类型级互斥：业务定义 `ApiContractOrchestrator` Bean 即禁用内置；单测+IT 断言互斥 | `orchestration.enabled=false` 关内置编排 |
| handler 后填充并发撕裂 | 中 | volatile 字段一次性交换；启动完成后只读；并发读单测 | 回退手抄编排形态 |
| adapter 引入 nacos-client 使 logOnly 用户依赖膨胀 | 低 | adapter 为独立可选模块，不引入即不受影响 | 移除 adapter 依赖 |
| nacos-client 版本被业务覆盖后不兼容 | 低 | 版本经 BOM property 声明；覆盖矩阵在 it 模块回归 | 恢复 BOM 版本 |
| 旧 demo/业务手抄编排升级后行为漂移 | 中 | 手抄编排实现接口后互斥生效，行为逐点一致（IT 对比） | 保留 `manual` profile 形态作对照 |

## 监控、日志、告警
- 自检四段日志不变（export loaded / contract ready / meta published / registry joined）
- 新增明示日志：内置编排被业务覆盖（互斥生效）、adapter 未配地址回退 logOnly、反注册完成
- Nacos 客户端连接异常沿用客户端日志与既有告警策略

## 测试方案
- 单元测试（provider-starter）：
  - 编排器：白名单→扫描→生成→reset 全链路产物正确；元数据失败/实例注册失败各自中断启动（ApplicationContextRunner 断言启动失败）；业务定义编排 Bean 时内置不装配（无双发布）；destroy 反注册且幂等；空白名单正常启动不发布
  - handler reset：填充后并发读不撕裂；未 reset 时兜底空注册表行为不变
- 单元测试（adapter）：配地址装配 CCW/Registrar、缺地址不装配且 logOnly 兜底；dataId/group/极简 metadata 断言
- 集成测试：沿用 it 模块策略（本机 8848 可达则跑端到端：零编排代码发布→网关订阅→`?mt=` 往返→优雅下线无残留；不可达则跳过并明示）
- 回归：`run-demo.sh` 端到端 PASS；全仓 `mvn clean verify` 绿
- 压测：不重复既有压测（调用链路未变），仅抽字节码调用器模式冒烟

## CR评审检查清单（blocker / 建议项，直接用于PR评审）
🔴 阻断项（不满足不能合并）
- 双发布互斥有测试断言（业务编排存在→内置编排 Bean 不存在）
- 发布顺序不变量有测试断言（元数据失败不注册实例；注册失败中断启动）
- 反注册幂等且容器关闭必达（destroy 链路测试）
- handler 后填充 volatile/一次性语义正确（并发读测试）
- adapter 缺地址不得阻断启动（logOnly 回退测试）
- 发布协议/元数据格式零变更（对归档基线的兼容断言）

🟡 优化建议项（不阻塞合并，登记issue后续迭代）
- 编排器分步耗时日志（扫描/生成/发布/注册分段耗时）
- adapter 支持 metrics 上报
- 多注册中心抽象（除 Nacos 外）

## 上线步骤
1. 代码合入（starter 编排器 + adapter 新模块 + demo 切换 + 测试），全仓 verify 绿
2. 内部 Nexus 发布四件套 + adapter（五件套），BOM 收录 adapter
3. 试点：订单中心类首个业务按新形态接入（1 依赖 + 三步 + 配置）验证零编排代码
4. 观测窗口：自检四段日志、Nacos dataId/实例残留巡检；demo 手抄形态（manual profile）作为行为对照保留一个版本周期

## 方案备注
- `InstanceRegistrar` 接口命名与归属（provider-starter `publish` 包）在实现时如与现有 `InstanceMetadataWriter` 职责重叠，以最小新增面为准调整
- adapter 模块坐标：`com.apicontract:api-contract-nacos-adapter`，版本随 BOM 统一
- 排期建议：任务串行约 2~3 个 PR（编排器 / adapter / demo+文档），任务清单见 tasks.md
