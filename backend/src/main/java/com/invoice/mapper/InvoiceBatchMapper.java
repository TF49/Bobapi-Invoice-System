package com.invoice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.invoice.entity.InvoiceBatch;
import org.apache.ibatis.annotations.Mapper;

/**
 * 发票批次 Mapper
 */
@Mapper
public interface InvoiceBatchMapper extends BaseMapper<InvoiceBatch> {
}