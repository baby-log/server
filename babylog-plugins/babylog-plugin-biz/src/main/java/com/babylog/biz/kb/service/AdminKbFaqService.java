package com.babylog.biz.kb.service;

import com.babylog.biz.api.enums.BabyLogErrorCode;
import com.babylog.biz.entity.BlKbFaq;
import com.babylog.biz.entity.BlKbFavorite;
import com.babylog.biz.kb.dto.KbFaqSaveCmd;
import com.babylog.biz.kb.vo.KbFaqVO;
import com.babylog.biz.mapper.BlKbFaqMapper;
import com.babylog.biz.mapper.BlKbFavoriteMapper;
import com.babylog.common.core.enums.error.GlobalErrorCode;
import com.babylog.common.core.exception.ServiceException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 育儿知识库 · 管理端 FAQ 维护（K6-S / M76）。
 *
 * <p><b>红线一「来源可追溯」</b>：{@code source} 必填（{@code 8465}），
 * 空来源一律拒绝保存；{@code content} 限 2000 字，禁把网络文章整篇搬运入库
 * （{@code 10} 号 §4.1）。服务端不做「自动抓取 / 自动摘要」，录入内容由运营人工摘录并标注来源。
 *
 * <p>本服务只写 {@code bl_kb_faq}（服务端专用表），不改 19 张同步表语义、
 * 不进 outbox、不占 /sync/ 开头的同步端点、不发 bl_notice。
 */
@Service
public class AdminKbFaqService {

    /** 摘录正文上限：超过即拒，避免整篇搬运（10 号 §4.1 红线一） */
    private static final int MAX_CONTENT = 2000;
    private static final int MAX_TITLE = 200;
    private static final int MAX_SOURCE = 500;
    private static final int MAX_KEYWORD = 100;
    private static final String CATEGORY_OTHER = "OTHER";

    private final BlKbFaqMapper faqMapper;
    private final BlKbFavoriteMapper favoriteMapper;

    public AdminKbFaqService(BlKbFaqMapper faqMapper, BlKbFavoriteMapper favoriteMapper) {
        this.faqMapper = faqMapper;
        this.favoriteMapper = favoriteMapper;
    }

    /** 管理端列表：含下架条目（与 app 端只看 enabled=1 不同） */
    public List<KbFaqVO> list(String category, String keyword) {
        String rawKw = keyword == null ? "" : keyword.trim();
        String kw = rawKw.length() > MAX_KEYWORD ? rawKw.substring(0, MAX_KEYWORD) : rawKw;
        String c = category == null ? "" : category.trim();
        LambdaQueryWrapper<BlKbFaq> wrapper = Wrappers.lambdaQuery();
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
        List<KbFaqVO> out = new ArrayList<>(rows.size());
        for (BlKbFaq row : rows) {
            out.add(toVO(row));
        }
        return out;
    }

    /** 新增 / 编辑；返回保存后的条目（管理端 favorited 恒 0） */
    public KbFaqVO save(KbFaqSaveCmd cmd) {
        if (cmd == null) {
            throw new ServiceException(GlobalErrorCode.PARAM_INVALID);
        }
        String category = trimOrEmpty(cmd.getCategory());
        String title = trimOrEmpty(cmd.getTitle());
        String content = trimOrEmpty(cmd.getContent());
        String source = trimOrEmpty(cmd.getSource());
        String sourceUrl = trimOrEmpty(cmd.getSourceUrl());
        if (category.isEmpty() || title.isEmpty() || content.isEmpty()) {
            throw new ServiceException(GlobalErrorCode.PARAM_INVALID);
        }
        if (source.isEmpty()) {
            throw new ServiceException(BabyLogErrorCode.KB_FAQ_SOURCE_REQUIRED);
        }
        if (title.length() > MAX_TITLE || content.length() > MAX_CONTENT || source.length() > MAX_SOURCE) {
            throw new ServiceException(GlobalErrorCode.PARAM_INVALID);
        }

        BlKbFaq row;
        if (cmd.getId() == null) {
            row = new BlKbFaq();
        } else {
            row = faqMapper.selectById(cmd.getId());
            if (row == null) {
                throw new ServiceException(BabyLogErrorCode.KB_FAQ_NOT_FOUND);
            }
        }
        row.setCategory(category.isEmpty() ? CATEGORY_OTHER : category);
        row.setTitle(title);
        row.setContent(content);
        row.setSource(source);
        row.setSourceUrl(sourceUrl.isEmpty() ? null : sourceUrl);
        row.setEnabled(cmd.getEnabled() == null || cmd.getEnabled() != 0 ? 1 : 0);
        row.setSortOrder(cmd.getSortOrder() == null ? 0 : cmd.getSortOrder());
        if (cmd.getId() == null) {
            faqMapper.insert(row);
        } else {
            faqMapper.updateById(row);
        }
        return toVO(row);
    }

    /** 逻辑删条目 + 物理清收藏关联（避免收藏列表残留指向已下架条目的行） */
    public void delete(long id) {
        BlKbFaq row = faqMapper.selectById(id);
        if (row == null) {
            throw new ServiceException(BabyLogErrorCode.KB_FAQ_NOT_FOUND);
        }
        faqMapper.deleteById(id);
        favoriteMapper.delete(Wrappers.<BlKbFavorite>lambdaQuery()
                .eq(BlKbFavorite::getFaqId, id));
    }

    // ------------------------------------------------------------------

    private static KbFaqVO toVO(BlKbFaq row) {
        KbFaqVO vo = new KbFaqVO();
        vo.setId(row.getId());
        vo.setCategory(row.getCategory());
        vo.setTitle(row.getTitle());
        vo.setContent(row.getContent());
        vo.setSource(row.getSource());
        vo.setSourceUrl(row.getSourceUrl());
        vo.setEnabled(row.getEnabled());
        vo.setSortOrder(row.getSortOrder());
        vo.setFavorited(0);
        return vo;
    }

    private static String trimOrEmpty(String s) {
        return s == null ? "" : s.trim();
    }
}
