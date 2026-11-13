package com.babylog.biz.summary.service;

import com.babylog.biz.summary.vo.TodaySummaryVO;

/** 今日汇总聚合（V2.2 小程序第一批，只读，不做任何写路径） */
public interface TodaySummaryService {

    /**
     * 当前用户的今日汇总。
     *
     * <p>可见范围与读权限复用同步通道同一裁决（{@code SyncAccessScopeResolver}）：
     * 无家庭 = 自己的数据（V2.0 回归锁）；家庭成员 = 家庭全部 ACTIVE 成员的数据，
     * 分类读权限被显式减窄时对应聚合段返回 null。
     */
    TodaySummaryVO today(long userId);
}
