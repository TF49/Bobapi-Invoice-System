package com.invoice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.invoice.entity.Invoice;
import org.apache.ibatis.annotations.Mapper;

/**
 * 发票数据访问层
 */
@Mapper
public interface InvoiceMapper extends BaseMapper<Invoice> {
}