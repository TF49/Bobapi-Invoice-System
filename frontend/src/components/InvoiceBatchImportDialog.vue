<template>
  <el-dialog
    v-model="visible"
    title="批量导入发票申请"
    width="900px"
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <div class="batch-import-container">
      <!-- 文件选择区域 -->
      <div class="file-section">
        <el-button type="primary" @click="downloadTemplate">
          <el-icon><Download /></el-icon>
          下载导入模板
        </el-button>
        
        <el-upload
          ref="uploadRef"
          :auto-upload="false"
          :show-file-list="false"
          :on-change="handleFileChange"
          accept=".csv,.xlsx,.xls"
          class="upload-area"
        >
          <el-button type="success">
            <el-icon><Upload /></el-icon>
            选择文件
          </el-button>
        </el-upload>
        
        <span v-if="selectedFile" class="file-name">{{ selectedFile.name }}</span>
      </div>

      <!-- 状态提示 -->
      <el-alert
        v-if="parseStatus === 'PARSING'"
        type="info"
        :closable="false"
        show-icon
      >
        正在解析文件...
      </el-alert>

      <el-alert
        v-if="parseError"
        type="error"
        :closable="false"
        show-icon
      >
        {{ parseError }}
      </el-alert>

      <!-- 预览表格 -->
      <div v-if="parsedData.length > 0" class="preview-section">
        <div class="preview-header">
          <span>共 {{ parsedData.length }} 条记录</span>
          <el-tag v-if="hasErrors" type="danger">存在 {{ errorCount }} 条错误</el-tag>
          <el-tag v-else type="success">全部通过校验</el-tag>
        </div>

        <el-table
          :data="parsedData"
          :max-height="400"
          border
          stripe
          class="preview-table"
        >
          <el-table-column prop="rowNumber" label="行号" width="80" />
          <el-table-column prop="companyName" label="公司名称" min-width="150" />
          <el-table-column prop="taxNumber" label="税号" width="150" />
          <el-table-column prop="amount" label="开票金额" width="120" />
          <el-table-column label="校验状态" width="120">
            <template #default="{ row }">
              <el-tag v-if="row.error" type="danger" size="small">错误</el-tag>
              <el-tag v-else type="success" size="small">通过</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="error" label="错误信息" min-width="200" />
        </el-table>
      </div>

      <!-- 提交结果 -->
      <div v-if="submitResult" class="result-section">
        <el-result
          :icon="submitResult.successCount === submitResult.total ? 'success' : 'warning'"
          :title="submitResult.successCount === submitResult.total ? '批量导入成功' : '部分导入失败'"
        >
          <template #sub-title>
            <div class="result-stats">
              <p>批次号: {{ submitResult.batchId }}</p>
              <p>总条数: {{ submitResult.total }}</p>
              <p>成功: {{ submitResult.successCount }}</p>
              <p>失败: {{ submitResult.failureCount }}</p>
              <p>总金额: ¥{{ submitResult.totalAmount }}</p>
            </div>
          </template>
        </el-result>
      </div>
    </div>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button
        v-if="parsedData.length > 0 && !submitResult"
        type="primary"
        :loading="submitting"
        :disabled="hasErrors"
        @click="handleSubmit"
      >
        确认导入
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';
import { ElMessage } from 'element-plus';
import { Download, Upload } from '@element-plus/icons-vue';
import {
  parseInvoiceFile,
  validateAllRows,
  findDuplicateRows,
  downloadTemplate as downloadTemplateUtil,
  type ParsedInvoiceRow
} from '../utils/invoiceImport';
import { invoiceApi } from '../api/invoice';

const visible = defineModel<boolean>({ required: true });

const emit = defineEmits<{
  success: [];
}>();

// 文件相关
const uploadRef = ref();
const selectedFile = ref<File | null>(null);
const parseStatus = ref<'IDLE' | 'PARSING' | 'PREVIEW' | 'ERROR'>('IDLE');
const parseError = ref('');
const parsedData = ref<ParsedInvoiceRow[]>([]);

// 提交相关
const submitting = ref(false);
const submitResult = ref<{
  batchId: number;
  total: number;
  successCount: number;
  failureCount: number;
  totalAmount: string;
} | null>(null);

// 批次幂等键
const idempotencyKey = ref('');

// 计算属性
const hasErrors = computed(() => parsedData.value.some(row => row.error));
const errorCount = computed(() => parsedData.value.filter(row => row.error).length);

// 下载模板
const downloadTemplate = () => {
  downloadTemplateUtil();
};

// 文件选择
const handleFileChange = async (file: File) => {
  selectedFile.value = file;
  parseStatus.value = 'PARSING';
  parseError.value = '';
  parsedData.value = [];
  submitResult.value = null;

  const result = await parseInvoiceFile(file);

  if (!result.success) {
    parseStatus.value = 'ERROR';
    parseError.value = result.error || '文件解析失败';
    return;
  }

  // 前端校验
  const validatedRows = validateAllRows(result.data);
  
  // 检查重复行
  const duplicateRows = findDuplicateRows(validatedRows);
  duplicateRows.forEach(rowNumber => {
    const row = validatedRows.find(r => r.rowNumber === rowNumber);
    if (row) {
      row.error = '该行与批次内其他行完全重复';
    }
  });

  parsedData.value = validatedRows;
  parseStatus.value = 'PREVIEW';

  // 生成批次幂等键
  if (!idempotencyKey.value) {
    idempotencyKey.value = generateIdempotencyKey();
  }
};

// 生成幂等键
const generateIdempotencyKey = (): string => {
  return `batch-${Date.now()}-${Math.random().toString(36).substring(2, 15)}`;
};

// 提交
const handleSubmit = async () => {
  if (hasErrors.value) {
    ElMessage.error('请先修正错误数据');
    return;
  }

  submitting.value = true;

  try {
    const items = parsedData.value.map(row => ({
      companyName: row.companyName,
      taxNumber: row.taxNumber.toUpperCase(),
      amount: row.amount
    }));

    const response = await invoiceApi.createInvoicesBatch(items, idempotencyKey.value);

    submitResult.value = {
      batchId: response.batchId,
      total: response.total,
      successCount: response.successCount,
      failureCount: response.failureCount,
      totalAmount: response.totalAmount
    };

    ElMessage.success('批量导入成功');
    emit('success');
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '批量导入失败');
  } finally {
    submitting.value = false;
  }
};

// 关闭
const handleClose = () => {
  if (submitting.value) {
    ElMessage.warning('正在提交中，请稍候');
    return;
  }
  
  visible.value = false;
  
  // 延迟重置状态
  setTimeout(() => {
    selectedFile.value = null;
    parseStatus.value = 'IDLE';
    parseError.value = '';
    parsedData.value = [];
    submitResult.value = null;
    uploadRef.value?.clearFiles();
  }, 300);
};
</script>

<style scoped>
.batch-import-container {
  padding: 20px 0;
}

.file-section {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
}

.upload-area {
  display: inline-block;
}

.file-name {
  color: #606266;
  font-size: 14px;
}

.preview-section {
  margin-top: 20px;
}

.preview-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
  font-weight: 500;
}

.preview-table {
  margin-top: 12px;
}

.result-section {
  margin-top: 20px;
}

.result-stats {
  text-align: left;
  margin-top: 16px;
}

.result-stats p {
  margin: 8px 0;
  color: #606266;
}
</style>