package com.invoice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.invoice.entity.InvoiceEvent;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 发票流转事件 Mapper
 */
@Mapper
public interface InvoiceEventMapper extends BaseMapper<InvoiceEvent> {

    @Insert({
            "<script>",
            "INSERT INTO invoice_event (invoice_id, user_id, event_type, operator_type, operator_id, quota_delta, ",
            "balance_after, quota_transaction_id, detail, created_at) VALUES ",
            "<foreach collection='events' item='e' separator=','>",
            "(#{e.invoiceId}, #{e.userId}, #{e.eventType}, #{e.operatorType}, #{e.operatorId}, #{e.quotaDelta}, ",
            "#{e.balanceAfter}, #{e.quotaTransactionId}, #{e.detail}, #{e.createdAt})",
            "</foreach>",
            "</script>"
    })
    int insertBatch(@Param("events") List<InvoiceEvent> events);
}
