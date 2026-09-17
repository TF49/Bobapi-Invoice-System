/**
 * 发票批量导入文件解析工具
 * 支持 CSV 和 XLSX 格式
 */

import * as XLSX from 'xlsx';

export const FIXED_INVOICE_TYPE = '技术服务费';
export const ALLOWED_INVOICE_TYPES = ['技术服务费', 'AI订阅服务费', '计算服务费', '研发和技术服务'] as const;

export interface ParsedInvoiceRow {
  rowNumber: number;       // 原始文件行号（从2开始，第1行是表头）
  companyName: string;     // 公司名称
  taxNumber: string;       // 税号
  amount: string;          // 开票金额（字符串形式避免精度问题）
  invoiceType: string;     // 开票类型
  invoiceCategory?: string; // 发票票种（普票/专票 或 NORMAL/VAT_SPECIAL）
  remark: string;          // 备注（可选）
  error?: string;          // 错误信息
}

export interface ParseResult {
  success: boolean;
  data: ParsedInvoiceRow[];
  error?: string;
}

// 表头映射
const HEADERS = {
  COMPANY_NAME: '公司名称',
  TAX_NUMBER: '税号',
  AMOUNT: '开票金额',
  INVOICE_TYPE: '开票类型',
  INVOICE_CATEGORY: '发票票种',
  REMARK: '备注'
};

export function normalizeInvoiceCategory(value?: string): 'NORMAL' | 'VAT_SPECIAL' {
  if (!value || !value.trim()) {
    return 'NORMAL';
  }
  const trimmed = value.trim().toUpperCase();
  if (trimmed === 'VAT_SPECIAL' || trimmed === '专票' || trimmed === '专用发票' || trimmed === '增值税专用发票') {
    return 'VAT_SPECIAL';
  }
  return 'NORMAL';
}

/**
 * 解析发票导入文件
 */
export async function parseInvoiceFile(file: File): Promise<ParseResult> {
  try {
    // 检查文件大小（限制 512KB）
    if (file.size > 512 * 1024) {
      return {
        success: false,
        data: [],
        error: '文件大小超过限制（最大 512KB）'
      };
    }

    const extension = file.name.split('.').pop()?.toLowerCase();
    
    if (extension === 'xlsx') {
      return parseExcelFile(file);
    } else if (extension === 'csv') {
      return parseCsvFile(file);
    } else {
      return {
        success: false,
        data: [],
        error: '不支持的文件格式，请使用 CSV 或 XLSX'
      };
    }
  } catch (error) {
    return {
      success: false,
      data: [],
      error: `文件解析失败: ${error instanceof Error ? error.message : '未知错误'}`
    };
  }
}

/**
 * 解析 Excel 文件
 */
async function parseExcelFile(file: File): Promise<ParseResult> {
  return new Promise((resolve) => {
    const reader = new FileReader();
    
    reader.onload = (e) => {
      try {
        const data = new Uint8Array(e.target?.result as ArrayBuffer);
        const workbook = XLSX.read(data, { type: 'array' });
        
        // 读取第一个工作表
        const firstSheetName = workbook.SheetNames[0];
        const worksheet = workbook.Sheets[firstSheetName];
        
        // 转换为 JSON（第一行为表头）
        const jsonData = XLSX.utils.sheet_to_json(worksheet, {
          header: 1,
          raw: false,
          defval: ''
        }) as any[][];
        
        const result = processSheetData(jsonData);
        resolve(result);
      } catch (error) {
        resolve({
          success: false,
          data: [],
          error: `Excel 解析失败: ${error instanceof Error ? error.message : '未知错误'}`
        });
      }
    };
    
    reader.onerror = () => {
      resolve({
        success: false,
        data: [],
        error: '文件读取失败'
      });
    };
    
    reader.readAsArrayBuffer(file);
  });
}

/**
 * 解析 CSV 文件
 */
async function parseCsvFile(file: File): Promise<ParseResult> {
  return new Promise((resolve) => {
    const reader = new FileReader();
    
    reader.onload = (e) => {
      try {
        const text = e.target?.result as string;
        
        // 使用 XLSX 解析 CSV（处理 UTF-8 BOM）
        const workbook = XLSX.read(text, { type: 'string' });
        const firstSheetName = workbook.SheetNames[0];
        const worksheet = workbook.Sheets[firstSheetName];
        
        const jsonData = XLSX.utils.sheet_to_json(worksheet, {
          header: 1,
          raw: false,
          defval: ''
        }) as any[][];
        
        const result = processSheetData(jsonData);
        resolve(result);
      } catch (error) {
        resolve({
          success: false,
          data: [],
          error: `CSV 解析失败: ${error instanceof Error ? error.message : '未知错误'}`
        });
      }
    };
    
    reader.onerror = () => {
      resolve({
        success: false,
        data: [],
        error: '文件读取失败'
      });
    };
    
    reader.readAsText(file, 'UTF-8');
  });
}

/**
 * 处理工作表数据
 */
function processSheetData(data: any[][]): ParseResult {
  if (!data || data.length < 2) {
    return {
      success: false,
      data: [],
      error: '文件为空或缺少数据行'
    };
  }

  // 检查表头
  const headers = data[0].map((h: any) => String(h ?? '').replace(/^\uFEFF/, '').trim());
  const requiredHeaders = [HEADERS.COMPANY_NAME, HEADERS.TAX_NUMBER, HEADERS.AMOUNT, HEADERS.INVOICE_TYPE];
  if (headers.length < requiredHeaders.length
      || requiredHeaders.some((header, index) => header !== headers[index])) {
    return {
      success: false,
      data: [],
      error: `表头不正确，必须按顺序包含：${HEADERS.COMPANY_NAME}、${HEADERS.TAX_NUMBER}、${HEADERS.AMOUNT}、${HEADERS.INVOICE_TYPE}（可加${HEADERS.INVOICE_CATEGORY}、${HEADERS.REMARK}）`
    };
  }

  const hasCategoryColumn = headers[4] === HEADERS.INVOICE_CATEGORY;
  const hasRemarkAtCol4 = headers[4] === HEADERS.REMARK;
  if (headers.length > 4 && !hasCategoryColumn && !hasRemarkAtCol4) {
    return {
      success: false,
      data: [],
      error: `第 5 列表头不正确，应为「${HEADERS.INVOICE_CATEGORY}」或「${HEADERS.REMARK}」`
    };
  }
  if (hasCategoryColumn && headers.length > 5 && headers[5] !== HEADERS.REMARK) {
    return {
      success: false,
      data: [],
      error: `第 6 列表头不正确，应为「${HEADERS.REMARK}」`
    };
  }

  // 解析数据行
  const rows: ParsedInvoiceRow[] = [];
  for (let i = 1; i < data.length; i++) {
    const row = data[i];
    const rowNumber = i + 1; // Excel 行号从2开始（第1行是表头）

    // 跳过空行
    if (!row || row.every((cell: any) => cell === undefined || cell === null || String(cell).trim() === '')) {
      continue;
    }

    const cellToString = (value: any) => String(value ?? '').trim();
    const companyName = cellToString(row[0]);
    const taxNumber = cellToString(row[1]);
    const amount = cellToString(row[2]);
    const invoiceType = cellToString(row[3]);
    let invoiceCategory = 'NORMAL';
    let remark = '';

    if (hasCategoryColumn) {
      invoiceCategory = cellToString(row[4]) || 'NORMAL';
      remark = cellToString(row[5]);
    } else if (hasRemarkAtCol4) {
      remark = cellToString(row[4]);
    }

    rows.push({
      rowNumber,
      companyName,
      taxNumber,
      amount,
      invoiceType,
      invoiceCategory,
      remark
    });
  }

  // 检查行数限制
  if (rows.length === 0) {
    return {
      success: false,
      data: [],
      error: '文件中没有有效数据行'
    };
  }

  if (rows.length > 100) {
    return {
      success: false,
      data: [],
      error: '单次批量申请最多支持 100 条记录'
    };
  }

  return {
    success: true,
    data: rows
  };
}

/**
 * 前端校验单行数据
 */
export function validateRow(row: ParsedInvoiceRow): string | null {
  // 公司名称校验
  if (!row.companyName) {
    return '公司名称不能为空';
  }
  if (row.companyName.length > 200) {
    return '公司名称不能超过 200 个字符';
  }

  // 税号校验（选填，最长 100 字符）
  if (row.taxNumber && row.taxNumber.trim().length > 100) {
    return '税号不能超过 100 个字符';
  }

  // 金额校验
  if (!row.amount) {
    return '开票金额不能为空';
  }
  const normalizedAmount = normalizeAmount(row.amount);
  if (!normalizedAmount) {
    return '开票金额格式不正确';
  }
  if (normalizedAmount === '0.00') {
    return '开票金额必须大于等于 0.01';
  }

  // 开票类型校验
  if (!row.invoiceType) {
    return '开票类型不能为空';
  }
  if (!ALLOWED_INVOICE_TYPES.includes(row.invoiceType as any)) {
    const formattedTypes = ALLOWED_INVOICE_TYPES.length <= 2
      ? ALLOWED_INVOICE_TYPES.join('或')
      : ALLOWED_INVOICE_TYPES.slice(0, -1).join('、') + '或' + ALLOWED_INVOICE_TYPES[ALLOWED_INVOICE_TYPES.length - 1];
    return `开票类型必须为${formattedTypes}`;
  }
  if (row.invoiceType.length > 100) {
    return '开票类型不能超过 100 个字符';
  }

  // 发票票种校验（选填，支持普票/专票/NORMAL/VAT_SPECIAL）
  if (row.invoiceCategory && row.invoiceCategory.trim()) {
    const rawCategory = row.invoiceCategory.trim().toUpperCase();
    const valid = ['NORMAL', 'VAT_SPECIAL', '普票', '专票', '普通发票', '专用发票', '增值税普通发票', '增值税专用发票'].includes(rawCategory);
    if (!valid) {
      return '发票票种必须为普票或专票（也可填 NORMAL 或 VAT_SPECIAL）';
    }
  }

  // 备注校验（可选）
  if (row.remark && row.remark.length > 500) {
    return '备注不能超过 500 个字符';
  }

  return null;
}

/**
 * 返回用于请求、hash 和重复行比较的两位小数金额；格式不合法时返回 null。
 */
export function normalizeAmount(value: string): string | null {
  const input = value.trim();
  if (!/^\d+(?:\.\d{1,2})?$/.test(input)) {
    return null;
  }

  const [integerPart, decimalPart = ''] = input.split('.');
  const normalizedIntegerPart = integerPart.replace(/^0+(?=\d)/, '');
  if (normalizedIntegerPart.length > 10) {
    return null;
  }

  const normalized = `${normalizedIntegerPart}.${decimalPart.padEnd(2, '0')}`;
  const [whole, cents] = normalized.split('.');
  return whole === '0' && cents === '00' ? '0.00' : normalized;
}

/**
 * 校验所有行并返回带有错误信息的结果
 */
export function validateAllRows(rows: ParsedInvoiceRow[]): ParsedInvoiceRow[] {
  return rows.map(row => {
    const error = validateRow(row);
    return {
      ...row,
      error: error || undefined
    };
  });
}

/**
 * 检查批次内完全重复行
 */
export function findDuplicateRows(rows: ParsedInvoiceRow[]): number[] {
  const seen = new Set<string>();
  const duplicates: number[] = [];

  for (const row of rows) {
    if (row.error) {
      continue;
    }
    const normalizedAmount = normalizeAmount(row.amount) || row.amount.trim();
    const normalizedTaxNumber = row.taxNumber ? row.taxNumber.trim().toUpperCase() : '';
    const normalizedCategory = normalizeInvoiceCategory(row.invoiceCategory);
    const key = `${row.companyName.trim()}|${normalizedTaxNumber}|${normalizedAmount}|${row.invoiceType.trim()}|${normalizedCategory}`;
    if (seen.has(key)) {
      duplicates.push(row.rowNumber);
    } else {
      seen.add(key);
    }
  }

  return duplicates;
}

/**
 * 生成标准导入模板（CSV 格式）
 */
export function generateTemplate(): string {
  const headers = [HEADERS.COMPANY_NAME, HEADERS.TAX_NUMBER, HEADERS.AMOUNT, HEADERS.INVOICE_TYPE, HEADERS.INVOICE_CATEGORY, HEADERS.REMARK];
  const sampleDataRow1 = [
    '示例公司A',
    '91500123456789012A',
    '1000.00',
    '技术服务费',
    '普票',
    '技术服务费普票申请示例'
  ];
  const sampleDataRow2 = [
    '示例公司B',
    '91500123456789012B',
    '500.00',
    'AI订阅服务费',
    '专票',
    'AI订阅服务费专票申请示例（按3倍扣除额度）'
  ];
  const sampleDataRow3 = [
    '示例公司C',
    '91500123456789012C',
    '800.00',
    '计算服务费',
    '普票',
    '计算服务费开票申请示例'
  ];
  const sampleDataRow4 = [
    '示例公司D',
    '91500123456789012D',
    '1200.00',
    '研发和技术服务',
    '普票',
    '研发和技术服务开票申请示例'
  ];

  return [
    headers.join(','),
    sampleDataRow1.join(','),
    sampleDataRow2.join(','),
    sampleDataRow3.join(','),
    sampleDataRow4.join(',')
  ].join('\n');
}

/**
 * 下载导入模板
 */
export function downloadTemplate(): void {
  const csv = generateTemplate();
  const blob = new Blob(['\ufeff' + csv], { type: 'text/csv;charset=utf-8;' });
  const link = document.createElement('a');
  link.href = URL.createObjectURL(blob);
  link.download = '发票导入模板.csv';
  link.click();
  URL.revokeObjectURL(link.href);
}
