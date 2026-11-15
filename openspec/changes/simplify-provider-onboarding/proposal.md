# 提案：简化 Provider 接入（启动编排内置 + 官方 Nacos 适配）

## 背景动因
1. **规格与实现的差距**：`contract-starter-access` 规格主流程已承诺「三步接入、自动装配开箱即用、零额外启用动作」，但当前实现中自动装配仅提供缺省件（ObjectMapper / MetaPublisher / 空注册表兜底 ContractRpcHandler）；「白名单加载 → 契约扫描 → 调用器生成 → 元数据发布 → 实例注册」这条启动编排链必须由每个业务应用手抄（demo-provider 的 `DemoProviderApplication` 即模板，约 60 行）。
2. **重复成本与抄错风险**：编排内含「先元数据后实例、任一失败中断启动」发布顺序不变量与优雅反注册——这是 CR 阻断级逻辑，散落在业务侧手抄极易抄错或遗漏（漏掉中断即产生「实例在而契约缺」中间态）。
3. **Nacos 桥接同样靠手抄**：`publish-mode=nacosConfig` 正式链路要求业务自行提供 `ConfigCarrierWriter` 与实例注册桥接（照抄 `DemoNacosBridge`），因框架刻意不强绑 nacos-client 版本，该适配一直没有官方承载。
4. **为什么现在做**：变更 `add-contract-gateway` 已全量交付并归档，首个真实业务（订单中心类应用）接入在即；每推迟一个接入方就多一份手抄编排的存量包袱。

## 当前现状
- 工程模块：四件套（bom / api / provider-starter / gateway-starter）+ it + samples（demo-provider / demo-gateway / demo-frontend），全仓 `mvn clean verify` 9 模块绿。
- `ApiContractAutoConfiguration`：总开关条件装配；三个缺省 Bean 全部 `@ConditionalOnMissingBean`（业务可整体替换）；注册中心/配置中心端口 optional，缺失降级 logOnly。
- demo-provider：手动编排（装配期白名单→扫描→调用器；ApplicationRunner 发布→注册；容器持有实例句柄，关闭时反注册）；`DemoNacosBridge` 手写 ConfigCarrierWriter + 原生 nacos-client 实例注册。
- 网关侧（gateway-starter）不涉及本变更：demo-gateway 已是「1 依赖 + 配置」形态。

## 目标与非目标
✅ 本次目标（可验证、本次必须落地）
- 业务应用接入 = 引 1 个 `api-contract-provider-starter` 依赖 + 三步业务代码（契约接口 / export.txt / 实现类）+ 配置，**零编排代码、零桥接代码**
- 启动编排（白名单→扫描→调用器→发布→注册）内置进 provider-starter 自动装配，保持既有不变量：先元数据后实例、任一失败中断启动、关闭时优雅反注册、自检四段日志
- 业务自定义编排仍可整体覆盖内置编排（`@ConditionalOnMissingBean` 语义），且**禁止双重发布**（业务定义编排组件时内置编排必须不装配）
- 新增官方 Nacos 适配模块：原生 nacos-client 自动装配，内置 ConfigCarrierWriter 与实例注册/反注册桥接，业务仅需配置 server-addr
- demo-provider 切换为内置编排后 `run-demo.sh` 端到端继续 PASS；手动编排保留为「业务可整体替换」示例
- 全仓 `mvn clean verify` 绿（含新增模块与测试）

❌ 非目标（明确本次不做，控制范围蔓延，后续迭代）
- 不改网关 starter（订阅、索引、调用链路均不变）
- 不改发布协议与元数据格式（dataId / group=CONTRACT / 极简实例标记均不变）
- 不引入 spring-cloud-alibaba（适配模块用原生 nacos-client，版本仍可由业务 dependencyManagement 覆盖）
- 不做鉴权、不做跨语言 SDK、不做内嵌网关形态
- 不做白名单热重扫（保持既有「重新扫描纳入发布流程」语义）

## 能力清单
### 新增能力
- `contract-nacos-adapter`：官方 Nacos 适配模块——以自动装配提供契约元数据发布写入器与实例注册/反注册桥接，业务配置地址即用，免手写桥接代码

### 修改已有能力
- `contract-starter-access`：补齐规格已承诺的「启动编排内置」——主流程从「应用侧手抄编排」收敛为「依赖 + 三步 + 配置，开箱即用」；新增业务自定义编排整体覆盖内置编排（防双发布）的分支语义；关联官方 Nacos 适配模块作为 nacosConfig 模式的推荐承载

### 下线移除能力
（无）

## 纳入范围 / 排除范围
### 纳入范围
- provider-starter：启动编排器（ApplicationRunner 发布 + 生命周期反注册 + 与缺省件协同的后填充机制）、`@ConditionalOnMissingBean` 覆盖语义
- 新模块 `api-contract-nacos-adapter`（自动装配 + 配置项 + 降级行为），挂入根 reactor 与 BOM
- samples：demo-provider 切换内置编排（含 Nacos 适配模块）并保留业务替换示例；`run-demo.sh` 回归
- 文档：samples README 三步接入章节、根 README 接入清单更新
- 测试：编排器（顺序不变量/失败中断/覆盖禁用/优雅反注册）与适配模块（缺地址降级/端到端）单元与集成测试

### 排除范围
- gateway-starter 任何改动
- 发布协议、元数据结构、错误码变更
- 既有手动编排 API 的删除（保持兼容，仅降级为示例）

## 依赖项
- 依赖既有四件套与 `add-contract-gateway` 交付的全部能力（已归档，specs 为基线）
- 依赖本机/环境 Nacos 2.x（集成测试外部依赖策略同 `api-contract-it`：优先本机 8848，缺失时相关 IT 跳过并明示）

## 待确认问题
无（编排时机沿用 demo 已验证的 ApplicationRunner 语义；适配模块版本覆盖策略沿用「业务 dependencyManagement 可覆盖」现状）。

## 初步风险预判
- 双重发布：业务自定义编排与内置编排同时生效（必须以覆盖语义+测试阻断）
- 编排后填充的并发安全（启动期一次性交换，需测试并发读不撕裂）
- 适配模块引入 nacos-client 后对业务依赖树的膨胀（compile 依赖但版本可覆盖；logOnly 用户可不引本模块）
- 兼容风险：已按 demo 手抄编排的业务升级后编排重复（升级指南需明示：定义编排组件即禁用内置）

## 验收信号
- 新业务接入模板：`1 个 starter 依赖（+可选 byte-buddy / nacos-adapter）+ 三步业务代码 + 配置`，无任何框架编排/桥接代码
- `run-demo.sh` 端到端 PASS（自检四段、`?mt=` 往返、404 断言、清理无残留）
- 编排器单测：发布顺序不变量、失败中断、业务覆盖禁用内置、关闭反注册全部有断言
- 全仓 `mvn clean verify` 绿
