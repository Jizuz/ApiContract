package com.apicontract.provider.scan;

import com.apicontract.api.meta.ContractMeta;
import java.lang.reflect.Method;

/**
 * 契约绑定：扫描产物，供调用器生成（任务5）使用。
 *
 * @param meta 契约条目
 * @param targetBean 实现Bean（可能是代理，调用经接口方法反射穿透）
 * @param method 契约接口方法（分发与取参依据）
 */
public record ContractBinding(ContractMeta meta, Object targetBean, Method method) {
}
