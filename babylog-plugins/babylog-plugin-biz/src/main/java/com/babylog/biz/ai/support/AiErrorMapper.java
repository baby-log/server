package com.babylog.biz.ai.support;

import com.babylog.ai.api.enums.AiErrorCode;
import com.babylog.common.core.exception.ServiceException;

/**
 * AI 降级映射（K5 / M73，范围 C）：把 {@code plugin-ai} 的失败翻译成对 App 端的结构化 5xxx。
 *
 * <p><b>为什么需要这一层</b>：{@code IAiFacade} 是跨插件契约，它抛的是 ai 域自己的错误码
 * （5020 未配置 / 5022 无可用 Key / 5003 调用失败）。产品侧要的是「用户能看懂、客户端能分支」的
 * 两类语义，所以在这里做一次收敛，<b>不新造错误码、不改 ai 域语义</b>
 * （K5 红线：5020 复用既有码，禁止新造、禁止改语义）。
 *
 * <p><b>映射口径（三类，可单测枚举）</b>：
 * <ul>
 *   <li>{@code 5020} / {@code 5022} → {@code 5020}「AI 模型未配置」：根本没配好（无供应商 / 无可用 Key），
 *       客户端应提示「暂不可用」而不是「再试一次」；</li>
 *   <li>{@code 5003} → {@code 5022}「该供应商暂无可用 API Key」：配置在、但这次调用打不通
 *       （Key 失效 / 余额不足 / base-url 不可达 / 限流），客户端可提示「稍后再试」；</li>
 *   <li>其他业务码（如 8460 额度超限）→ 原样向上抛，不包装。</li>
 * </ul>
 *
 * <p>关键纪律：<b>任何一条都不允许返回空草稿或伪造 AI 内容</b>——拿不到就明说拿不到。
 */
public final class AiErrorMapper {

    private AiErrorMapper() {
    }

    /** 把 ai 域异常翻译成产品侧结构化异常（非 ai 域异常原样返回，保持调用方语义） */
    public static ServiceException translate(ServiceException e) {
        int code = e.getCode();
        if (code == AiErrorCode.AI_NOT_CONFIGURED.getCode()
                || code == AiErrorCode.NO_AVAILABLE_KEY.getCode()) {
            return new ServiceException(AiErrorCode.AI_NOT_CONFIGURED);
        }
        if (code == AiErrorCode.CHAT_FAILED.getCode()) {
            return new ServiceException(AiErrorCode.NO_AVAILABLE_KEY);
        }
        return e;
    }
}
