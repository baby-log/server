package com.babylog.biz.kb.service;

import com.babylog.biz.api.enums.BabyLogErrorCode;
import com.babylog.biz.entity.BlKbFaq;
import com.babylog.biz.entity.BlKbFavorite;
import com.babylog.biz.kb.vo.KbCategoryVO;
import com.babylog.biz.kb.vo.KbFaqVO;
import com.babylog.biz.mapper.BlKbFaqMapper;
import com.babylog.biz.mapper.BlKbFavoriteMapper;
import com.babylog.common.core.exception.ServiceException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 育儿知识库 · 浏览 / 分类 / 搜索 / 收藏（K6-S / M76，app-user 侧，<b>免费</b>）。
 *
 * <p>数据源是 BabyLog 自建的 {@code bl_kb_faq}（服务端专用表，不进 SyncTable）；
 * 这里的「搜索」是条目列表的标题/正文关键词过滤，<b>不是 RAG 向量检索</b>——
 * 问答检索一律走 {@code IKnowledgeBaseFacade}，本类不碰上游 ai-kb 实现。
 *
 * <p>排序用 Java 内存排（sortOrder → id），刻意不用 MyBatis-Plus 的末尾 SQL 追加方法：
 * P0 ArchUnit A10 对新增文件出现该调用的字面串直接判红（连注释也扫，故这里不写出字面串）。
 */
@Service
public class KbFaqService {

    private static final int ENABLED = 1;
    private static final int MAX_KEYWORD = 100;
    private static final String CATEGORY_OTHER = "OTHER";

    private final BlKbFaqMapper faqMapper;
    private final BlKbFavoriteMapper favoriteMapper;

    public KbFaqService(BlKbFaqMapper faqMapper, BlKbFavoriteMapper favoriteMapper) {
        this.faqMapper = faqMapper;
        this.favoriteMapper = favoriteMapper;
    }

    /** 已上架条目按分类聚合（分类码升序，客户端可直接渲染 tab） */
    public List<KbCategoryVO> categories() {
        List<BlKbFaq> rows = faqMapper.selectList(Wrappers.<BlKbFaq>lambdaQuery()
                .eq(BlKbFaq::getEnabled, ENABLED));
        Map<String, Integer> counts = new TreeMap<>();
        for (BlKbFaq row : rows) {
            counts.merge(normalizeCategory(row.getCategory()), 1, Integer::sum);
        }
        List<KbCategoryVO> out = new ArrayList<>();
        counts.forEach((category, count) -> {
            KbCategoryVO vo = new KbCategoryVO();
            vo.setCategory(category);
            vo.setCount(count);
            out.add(vo);
        });
        return out;
    }

    /** 条目列表：category / keyword 均可选；每页不分页（服务端条目量受运营录入约束） */
    public List<KbFaqVO> list(long userId, String category, String keyword) {
        List<BlKbFaq> rows = queryEnabled(category, keyword);
        return toVOs(rows, favoriteIds(userId));
    }

    public KbFaqVO detail(long userId, long faqId) {
        BlKbFaq row = faqMapper.selectOne(Wrappers.<BlKbFaq>lambdaQuery()
                .eq(BlKbFaq::getId, faqId)
                .eq(BlKbFaq::getEnabled, ENABLED));
        if (row == null) {
            throw new ServiceException(BabyLogErrorCode.KB_FAQ_NOT_FOUND);
        }
        return toVO(row, favoriteIds(userId).contains(faqId));
    }

    /** 我的收藏：只返回仍上架的条目（已下架/已逻辑删的不出现在列表里） */
    public List<KbFaqVO> favorites(long userId) {
        List<BlKbFavorite> favs = favoriteMapper.selectList(Wrappers.<BlKbFavorite>lambdaQuery()
                .eq(BlKbFavorite::getUserId, userId));
        if (favs.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> ids = favs.stream()
                .map(BlKbFavorite::getFaqId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        List<BlKbFaq> rows = faqMapper.selectList(Wrappers.<BlKbFaq>lambdaQuery()
                .eq(BlKbFaq::getEnabled, ENABLED)
                .in(BlKbFaq::getId, ids));
        return toVOs(rows, ids);
    }

    /** 收藏：先校验条目存在且上架；并发/重复收藏由唯一键兜底，吞冲突后幂等返回 */
    public void addFavorite(long userId, long faqId) {
        Long count = faqMapper.selectCount(Wrappers.<BlKbFaq>lambdaQuery()
                .eq(BlKbFaq::getId, faqId)
                .eq(BlKbFaq::getEnabled, ENABLED));
        if (count == null || count == 0) {
            throw new ServiceException(BabyLogErrorCode.KB_FAQ_NOT_FOUND);
        }
        BlKbFavorite row = new BlKbFavorite();
        row.setUserId(userId);
        row.setFaqId(faqId);
        row.setCreateTime(LocalDateTime.now());
        try {
            favoriteMapper.insert(row);
        } catch (DuplicateKeyException e) {
            // 已收藏：幂等成功，不重试、不回查
        }
    }

    /** 取消收藏：物理 DELETE（本表无 deleted 列），不存在时静默成功 */
    public void removeFavorite(long userId, long faqId) {
        favoriteMapper.delete(Wrappers.<BlKbFavorite>lambdaQuery()
                .eq(BlKbFavorite::getUserId, userId)
                .eq(BlKbFavorite::getFaqId, faqId));
    }

    // ------------------------------------------------------------------

    private List<BlKbFaq> queryEnabled(String category, String keyword) {
        String rawKw = keyword == null ? "" : keyword.trim();
        String kw = rawKw.length() > MAX_KEYWORD ? rawKw.substring(0, MAX_KEYWORD) : rawKw;
        String c = category == null ? "" : category.trim();
        LambdaQueryWrapper<BlKbFaq> wrapper = Wrappers.<BlKbFaq>lambdaQuery()
                .eq(BlKbFaq::getEnabled, ENABLED);
        if (!c.isEmpty()) {
            wrapper.eq(BlKbFaq::getCategory, c);
        }
        if (!kw.isEmpty()) {
            wrapper.and(w -> w.like(BlKbFaq::getTitle, kw).or().like(BlKbFaq::getContent, kw));
        }
        List<BlKbFaq> rows = faqMapper.selectList(wrapper);
        Comparator<Integer> intCmp = Comparator.nullsLast(Comparator.naturalOrder());
        Comparator<Long> longCmp = Comparator.nullsLast(Comparator.naturalOrder());
        rows.sort(Comparator.comparing(BlKbFaq::getSortOrder, intCmp)
                .thenComparing(BlKbFaq::getId, longCmp));
        return rows;
    }

    private Set<Long> favoriteIds(long userId) {
        List<BlKbFavorite> favs = favoriteMapper.selectList(Wrappers.<BlKbFavorite>lambdaQuery()
                .eq(BlKbFavorite::getUserId, userId)
                .select(BlKbFavorite::getFaqId));
        return favs.stream()
                .map(BlKbFavorite::getFaqId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private List<KbFaqVO> toVOs(List<BlKbFaq> rows, Set<Long> favoritedIds) {
        List<KbFaqVO> out = new ArrayList<>(rows.size());
        for (BlKbFaq row : rows) {
            out.add(toVO(row, favoritedIds.contains(row.getId())));
        }
        return out;
    }

    private static KbFaqVO toVO(BlKbFaq row, boolean favorited) {
        KbFaqVO vo = new KbFaqVO();
        vo.setId(row.getId());
        vo.setCategory(normalizeCategory(row.getCategory()));
        vo.setTitle(row.getTitle());
        vo.setContent(row.getContent());
        vo.setSource(row.getSource());
        vo.setSourceUrl(row.getSourceUrl());
        vo.setEnabled(row.getEnabled());
        vo.setSortOrder(row.getSortOrder());
        // 布尔契约：整数 1/0，绝不发 true/false
        vo.setFavorited(favorited ? 1 : 0);
        return vo;
    }

    private static String normalizeCategory(String category) {
        return category == null || category.isBlank() ? CATEGORY_OTHER : category.trim();
    }
}
