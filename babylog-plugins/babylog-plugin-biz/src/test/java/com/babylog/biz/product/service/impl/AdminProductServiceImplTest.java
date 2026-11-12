package com.babylog.biz.product.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.babylog.biz.api.enums.BabyLogErrorCode;
import com.babylog.biz.entity.BlProduct;
import com.babylog.biz.mapper.BlProductMapper;
import com.babylog.biz.product.dto.ProductEditCmd;
import com.babylog.biz.product.vo.ProductVO;
import com.babylog.common.core.exception.ServiceException;
import com.babylog.common.core.page.PageResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 管理端内置产品维护单测。
 *
 * <p>最核心的两条契约：
 * ① 编辑必须置空 serverUpdatedAt（否则 MP 把旧游标 SET 回去，base 通道增量失效）；
 * ② 非内置行（is_builtin=0）一律 8349——App 通道不被管理端绕过。
 */
@ExtendWith(MockitoExtension.class)
class AdminProductServiceImplTest {

    private static final int BUILTIN = 1;

    @Mock
    private BlProductMapper productMapper;

    @InjectMocks
    private AdminProductServiceImpl service;

    private static BlProduct builtinRow(long id) {
        BlProduct row = new BlProduct();
        row.setId(id);
        row.setCategory("FORMULA");
        row.setBrand("伊可新");
        row.setName("维生素 AD 滴剂");
        row.setIsBuiltin(BUILTIN);
        row.setEnabled(1);
        row.setVersion(7L);
        row.setServerUpdatedAt(LocalDateTime.of(2026, 11, 12, 10, 0));
        return row;
    }

    private static ProductEditCmd editCmd() {
        ProductEditCmd cmd = new ProductEditCmd();
        cmd.setCategory("SUPPLEMENT");
        cmd.setBrand("新品牌");
        cmd.setName("新品名");
        cmd.setSpec("15ml");
        cmd.setDefaultDose("每日 1 粒");
        cmd.setNote("备注");
        cmd.setIconPath("file-123");
        cmd.setEnabled(0);
        return cmd;
    }

    @Test
    void page_mapsRowsToVo() {
        BlProduct row = builtinRow(10L);
        Page<BlProduct> page = new Page<>(1, 10);
        page.setRecords(List.of(row));
        page.setTotal(1);
        when(productMapper.selectPage(any(), any())).thenReturn(page);

        PageResult<ProductVO> result = service.page(new com.babylog.biz.product.dto.ProductPageQuery());

        assertThat(result.getTotal()).isEqualTo(1);
        ProductVO vo = result.getList().get(0);
        assertThat(vo.getId()).isEqualTo(10L);
        assertThat(vo.getVersion()).isEqualTo(7L);
        assertThat(vo.getServerUpdatedAt()).isNotNull();
    }

    @Test
    void edit_appliesFieldsAndNullsServerUpdatedAt() {
        BlProduct row = builtinRow(10L);
        when(productMapper.selectById(10L)).thenReturn(row);
        when(productMapper.updateById(any(BlProduct.class))).thenReturn(1);

        service.edit(10L, editCmd());

        ArgumentCaptor<BlProduct> captor = ArgumentCaptor.forClass(BlProduct.class);
        verify(productMapper).updateById(captor.capture());
        BlProduct saved = captor.getValue();
        assertThat(saved.getCategory()).isEqualTo("SUPPLEMENT");
        assertThat(saved.getBrand()).isEqualTo("新品牌");
        assertThat(saved.getName()).isEqualTo("新品名");
        assertThat(saved.getSpec()).isEqualTo("15ml");
        assertThat(saved.getDefaultDose()).isEqualTo("每日 1 粒");
        assertThat(saved.getNote()).isEqualTo("备注");
        assertThat(saved.getIconPath()).isEqualTo("file-123");
        assertThat(saved.getEnabled()).isZero();
        // 关键契约：游标列置空交给数据库 ON UPDATE；version 字段保持加载值（乐观锁拦截器负责 +1）
        assertThat(saved.getServerUpdatedAt()).isNull();
        assertThat(saved.getVersion()).isEqualTo(7L);
        assertThat(saved.getUpdatedAt()).isNotNull();
        // 通道隔离字段不可改
        assertThat(saved.getIsBuiltin()).isEqualTo(BUILTIN);
    }

    @Test
    void edit_optimisticLockMissTreatedAsNotFound() {
        BlProduct row = builtinRow(10L);
        when(productMapper.selectById(10L)).thenReturn(row);
        when(productMapper.updateById(any(BlProduct.class))).thenReturn(0);

        assertThatThrownBy(() -> service.edit(10L, editCmd()))
                .isInstanceOf(ServiceException.class)
                .extracting(e -> ((ServiceException) e).getCode())
                .isEqualTo(BabyLogErrorCode.PRODUCT_NOT_FOUND.getCode());
    }

    @Test
    void edit_rejectsUserCreatedProduct() {
        // App 通道隔离红线：用户自建行（is_builtin=0）管理端不得触碰
        BlProduct userRow = builtinRow(900000000000000001L);
        userRow.setIsBuiltin(0);
        when(productMapper.selectById(900000000000000001L)).thenReturn(userRow);

        assertThatThrownBy(() -> service.edit(900000000000000001L, editCmd()))
                .isInstanceOf(ServiceException.class)
                .extracting(e -> ((ServiceException) e).getCode())
                .isEqualTo(BabyLogErrorCode.PRODUCT_NOT_BUILTIN.getCode());
        verify(productMapper, never()).updateById(any(BlProduct.class));
    }

    @Test
    void edit_rejectsMissingRow() {
        when(productMapper.selectById(404L)).thenReturn(null);

        assertThatThrownBy(() -> service.edit(404L, editCmd()))
                .isInstanceOf(ServiceException.class)
                .extracting(e -> ((ServiceException) e).getCode())
                .isEqualTo(BabyLogErrorCode.PRODUCT_NOT_FOUND.getCode());
    }

    @Test
    void remove_softDeletesBuiltinRow() {
        when(productMapper.selectById(91L)).thenReturn(builtinRow(91L));

        service.remove(91L);

        // @TableLogic 逻辑删除：墓碑经 base 通道下发，App 端隐藏该产品
        verify(productMapper).deleteById(91L);
    }

    @Test
    void remove_rejectsUserCreatedAndMissingRows() {
        BlProduct userRow = builtinRow(900000000000000002L);
        userRow.setIsBuiltin(0);
        when(productMapper.selectById(900000000000000002L)).thenReturn(userRow);
        when(productMapper.selectById(404L)).thenReturn(null);

        assertThatThrownBy(() -> service.remove(900000000000000002L))
                .isInstanceOf(ServiceException.class)
                .extracting(e -> ((ServiceException) e).getCode())
                .isEqualTo(BabyLogErrorCode.PRODUCT_NOT_BUILTIN.getCode());
        assertThatThrownBy(() -> service.remove(404L))
                .isInstanceOf(ServiceException.class)
                .extracting(e -> ((ServiceException) e).getCode())
                .isEqualTo(BabyLogErrorCode.PRODUCT_NOT_FOUND.getCode());
        verify(productMapper, never()).deleteById(anyLong());
    }
}
