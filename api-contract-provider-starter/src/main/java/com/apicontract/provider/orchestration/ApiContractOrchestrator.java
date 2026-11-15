package com.apicontract.provider.orchestration;

/**
 * 启动编排 SPI 标记接口（防双发布互斥的类型锚点）。
 *
 * <p>内置编排（{@link DefaultApiContractOrchestrator}）按本接口做
 * {@code @ConditionalOnMissingBean} 类型级互斥：业务（或旧手抄编排形态）
 * 在容器中声明任意 {@code ApiContractOrchestrator} Bean，内置编排即整体禁用，
 * 同一应用任一时刻仅一套编排生效。
 *
 * <p>接口不强制方法签名：业务编排组件以自有 Bean（如装配期
 * {@code ContractRpcHandler} 工厂 + 发布 Runner）表达编排，实现本接口仅用于
 * 显式声明「本应用自带编排，请勿内置编排」。
 */
public interface ApiContractOrchestrator {
}
