package com.invoice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.invoice.entity.SealDocument;
import org.apache.ibatis.annotations.Mapper;

/**
 * 盖章文件管理 Mapper
 */
@Mapper
public interface SealDocumentMapper extends BaseMapper<SealDocument> {
}
