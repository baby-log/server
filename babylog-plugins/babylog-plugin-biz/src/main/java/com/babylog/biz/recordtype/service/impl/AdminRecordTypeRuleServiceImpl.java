package com.babylog.biz.recordtype.service.impl;

import com.babylog.biz.api.enums.BabyLogErrorCode;
import com.babylog.biz.entity.BlRecordTypeRule;
import com.babylog.biz.mapper.BlRecordTypeRuleMapper;
import com.babylog.biz.recordtype.dto.RecordTypeRuleEditCmd;
import com.babylog.biz.recordtype.dto.RecordTypeRulePageQuery;
import com.babylog.biz.recordtype.service.AdminRecordTypeRuleService;
import com.babylog.biz.recordtype.vo.RecordTypeRuleVO;
import com.babylog.biz.support.PageUtils;
import com.babylog.common.core.exception.ServiceException;
import com.babylog.common.core.page.PageResult;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** 管理端记录类型规则维护实现（M41）。写路径见接口 Javadoc（游标列实体不声明，ON UPDATE 自动前进）。 */
@Service
public class AdminRecordTypeRuleServiceImpl implements AdminRecordTypeRuleService {

    private final BlRecordTypeRuleMapper ruleMapper;

    public AdminRecordTypeRuleServiceImpl(BlRecordTypeRuleMapper ruleMapper) {
        this.ruleMapper = ruleMapper;
    }

    @Override
    public PageResult<RecordTypeRuleVO> page(RecordTypeRulePageQuery query) {
        Page<BlRecordTypeRule> page = ruleMapper.selectPage(PageUtils.toMpPage(query),
                Wrappers.<BlRecordTypeRule>lambdaQuery()
                        .eq(StringUtils.hasText(query.getCategory()), BlRecordTypeRule::getCategory, query.getCategory())
                        .like(StringUtils.hasText(query.getRecordType()), BlRecordTypeRule::getRecordType, query.getRecordType())
                        .orderByAsc(BlRecordTypeRule::getCategory)
                        .orderByAsc(BlRecordTypeRule::getSortOrder)
                        .orderByAsc(BlRecordTypeRule::getId));
        return PageUtils.toPageResult(page, page.getRecords().stream().map(AdminRecordTypeRuleServiceImpl::toVo).toList());
    }

    @Override
    public RecordTypeRuleVO get(long id) {
        return toVo(requireRule(id));
    }

    @Override
    public void edit(long id, RecordTypeRuleEditCmd cmd) {
        BlRecordTypeRule row = requireRule(id);
        // 分组键/文案：空白串归一成 null（「不覆盖」语义），避免「空串 ≠ null」两种落法
        row.setEnabled(cmd.getEnabled());
        row.setStartAgeDays(cmd.getStartAgeDays());
        row.setEndAgeDays(cmd.getEndAgeDays());
        row.setSortOrder(cmd.getSortOrder() == null ? 0 : cmd.getSortOrder());
        row.setGroupKey(StringUtils.hasText(cmd.getGroupKey()) ? cmd.getGroupKey().trim() : null);
        row.setDisplayLabel(StringUtils.hasText(cmd.getDisplayLabel()) ? cmd.getDisplayLabel().trim() : null);
        // 游标列交给数据库 ON UPDATE 维护，实体不声明该字段（见接口 Javadoc）
        if (ruleMapper.updateById(row) == 0) {
            throw new ServiceException(BabyLogErrorCode.RECORD_TYPE_RULE_NOT_FOUND);
        }
    }

    private BlRecordTypeRule requireRule(long id) {
        BlRecordTypeRule row = ruleMapper.selectById(id);
        if (row == null) {
            throw new ServiceException(BabyLogErrorCode.RECORD_TYPE_RULE_NOT_FOUND);
        }
        return row;
    }

    private static RecordTypeRuleVO toVo(BlRecordTypeRule row) {
        RecordTypeRuleVO vo = new RecordTypeRuleVO();
        vo.setId(row.getId());
        vo.setRecordType(row.getRecordType());
        vo.setCategory(row.getCategory());
        vo.setEnabled(row.getEnabled());
        vo.setStartAgeDays(row.getStartAgeDays());
        vo.setEndAgeDays(row.getEndAgeDays());
        vo.setSortOrder(row.getSortOrder());
        vo.setGroupKey(row.getGroupKey());
        vo.setDisplayLabel(row.getDisplayLabel());
        return vo;
    }
}
