package com.invoice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.invoice.entity.InvoiceBatch;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 发票批次 Mapper
 */
@Mapper
public interface InvoiceBatchMapper extends BaseMapper<InvoiceBatch> {

    @Select("SELECT * FROM invoice_batch " +
            "WHERE user_id = #{userId} AND idempotency_key = #{idempotencyKey} AND deleted = 0 " +
            "LIMIT 1 FOR UPDATE")
    InvoiceBatch selectByIdempotencyKeyForUpdate(@Param("userId") Long userId,
                                                  @Param("idempotencyKey") String idempotencyKey);
}
