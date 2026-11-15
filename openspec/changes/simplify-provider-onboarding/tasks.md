# 开发任务清单
<!-- 基于Proposal、Spec、Design评审完成后生成；
  任务粒度适合单PR，尽量小PR，避免大PR造成CR卡点；
  每个任务尽量独立、可自测、可合并；
  标记负责人、优先级、预估工时、关联spec
  排序遵循 TDD：每个任务单元内【先写失败测试→确认红→再实现→转绿】，实现任务永远排在对应测试任务之后
-->

## 任务列表
| 序号 | 任务描述 | 关联spec | 优先级 | 预估工时 | 交付物 | 依赖任务 |
| ---- | -------- | -------- | ------ | -------- | ------ | -------- |
| 1 | 【测试先行】`ContractRpcHandler` 后填充契约测试：`reset` 后元数据/调用器生效、未 reset 时空注册表行为不变、并发读不撕裂 | contract-starter-access | P0 | 0.5d | provider-starter 测试类（先红） | - |
| 2 | 【实现】`ContractRpcHandler.reset(...)` 与 volatile 字段改造（既有构造与行为不变），使任务1转绿 | contract-starter-access | P0 | 0.5d | handler 改造 | 1 |
| 3 | 【测试先行】内置编排器测试：白名单→扫描→调用器→reset 全链路产物；元数据失败不注册实例；实例注册失败中断启动；空白名单正常启动不发布（ApplicationContextRunner） | contract-starter-access | P0 | 1d | 编排器测试类（先红） | 2 |
| 4 | 【实现】`ApiContractOrchestrator` 接口 + 内置编排器（SmartInitializingSingleton 扫描生成 / ApplicationRunner 发布注册 / DisposableBean 反注册），使任务3转绿 | contract-starter-access | P0 | 1.5d | 编排器实现 | 3 |
| 5 | 【测试先行】编排互斥测试：业务定义 `ApiContractOrchestrator` Bean 时内置编排不装配（无双发布）；`orchestration.enabled=false` 时内置编排不执行；反注册幂等 | contract-starter-access | P0 | 0.5d | 互斥测试（先红） | 4 |
| 6 | 【实现】编排器装配条件（`@ConditionalOnMissingBean` + `orchestration.enabled`）与「被业务覆盖」明示日志，使任务5转绿 | contract-starter-access | P0 | 0.5d | 装配条件 | 5 |
| 7 | 【测试先行】adapter 装配测试：配 server-addr 装配 CCW 与 Registrar（dataId/group/极简 metadata 断言）；缺地址不装配不阻断、starter 回退 logOnly | contract-nacos-adapter | P0 | 1d | adapter 测试类（先红） | 4 |
| 8 | 【实现】新模块 `api-contract-nacos-adapter`：自动装配 + ConfigCarrierWriter + NacosInstanceRegistrar + 配置项，挂入根 reactor 与 BOM，使任务7转绿 | contract-nacos-adapter | P0 | 1.5d | 新模块 | 7 |
| 9 | 【测试先行】IT：零编排代码端到端（发布→网关订阅→`?mt=` 往返→优雅下线无残留；本机 8848 不可达则跳过并明示）；手抄编排（manual profile）与内置编排行为对照 | contract-starter-access, contract-nacos-adapter | P1 | 1d | it 用例（先红） | 6, 8 |
| 10 | 【实现】demo-provider 切换内置编排 + adapter（手抄编排改造为 `ApiContractOrchestrator` 实现并以 `manual` profile 隔离），使任务9转绿 | contract-starter-access | P1 | 1d | demo 改造 | 9 |
| 11 | 文档：samples README 三步接入（新形态）、根 README 接入清单与角色对照更新、旧形态升级指南（定义编排组件即禁用内置） | contract-starter-access | P1 | 0.5d | 文档更新 | 10 |
| 12 | 全仓回归：`mvn clean verify` 全绿 + `run-demo.sh` 端到端 PASS（自检四段、`?mt=` 往返、404 断言、清理无残留） | contract-starter-access, contract-nacos-adapter | P0 | 0.5d | 回归记录 | 10, 11 |

### 任务细分说明
- 任务1/2：reset 语义为「启动期单写、运行期只读」；并发测试用多线程读 handler 断言无撕裂
- 任务3/4：编排时序必须与 design 方案B 一致（扫描在 SmartInitializingSingleton、发布在 ApplicationRunner、Web 端口已定）；失败中断用 ApplicationContextRunner 断言启动失败而非吞异常
- 任务5/6：互斥语义是 CR 阻断项——业务 Bean 存在时内置编排 Bean 必须不存在，测试直接断言容器内 Bean
- 任务7/8：nacos-client 版本经 property 声明并验证业务可覆盖；缺地址回退不得阻断启动
- 任务9/10：demo 手抄编排保留价值 = 向后兼容证明 + 「业务可整体替换」示例；默认 profile 走内置编排
- 任务12：完成前禁止勾选；回归记录注明模块数（10 模块）与脚本输出摘要

## 单元测试要求
- 必须覆盖的场景：正向（全链路装配/发布）、分支（空白名单/manual profile/缺地址降级/编排开关关闭）、异常（元数据失败、注册失败、Nacos 不可达）、幂等（反注册重复调用）、边界（并发读 handler）
- Mock依赖方式：MetaPublisher/InstanceRegistrar 用测试替身注入；ApplicationContextRunner 驱动条件装配断言；Nacos 相关 IT 遵循「本机 8848 可达才跑」

## 预检查清单（提CR前自检）
- [ ] CI流水线全部绿色通过（mvn clean verify 全模块）
- [ ] 单元测试补充完成（含 CR 阻断项六条断言全部落地）
- [ ] 日志、埋点、异常处理补齐（互斥明示/降级告警/反注册完成）
- [ ] 配置、限流、告警配置同步（BOM 收录 adapter、配置项文档）
- [ ] 无数据库变更脚本（本变更无 DB）

## 上线准备清单
- [ ] BOM/五件套发布脚本评审（内部 Nexus）
- [ ] 配置变更提交（adapter 配置项默认值确认）
- [ ] 回滚方案可用（orchestration.enabled=false / 移除 adapter 依赖 / manual profile 对照）
- [ ] 告警规则配置（Nacos 连接异常、发布失败）
- [ ] 测试回归完成（run-demo.sh + it 端到端）

## 任务备注
- 串行节奏建议 3 个 PR：PR1=任务1~6（starter 编排器）、PR2=任务7~8（adapter）、PR3=任务9~12（demo/IT/文档/回归）
- 每完成一个任务单元（测试任务+对应实现任务成对转绿）才勾选两项
