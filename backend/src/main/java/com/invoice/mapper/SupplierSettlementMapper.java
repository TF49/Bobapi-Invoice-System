package com.invoice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.invoice.entity.SupplierSettlement;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

/**
 * 供应商结算记录 Mapper
 */
@Mapper
public interface SupplierSettlementMapper extends BaseMapper<SupplierSettlement> {

    /**
     * 查询总已结算金额
     */
    @Select("SELECT COALESCE(SUM(settlement_amount), 0) FROM supplier_settlement WHERE deleted = 0")
    BigDecimal selectTotalSettledAmount();
}
