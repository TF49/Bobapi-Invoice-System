/**
 * 发票批量导入文件解析工具
 * 支持 CSV 和 XLSX 格式
 */

import * as XLSX from 'xlsx';

export interface ParsedInvoiceRow {
  rowNumber: number;       // 原始文件行号（从2开始，第1行是表头）
  companyName: string;     // 公司名称
  taxNumber: string;       // 税号
  amount: string;          // 开票金额（字符串形式避免精度问题）
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
  AMOUNT: '开票金额'
};

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
    
    if (extension === 'xlsx' || extension === 'xls') {
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
        const jsonData = XLSX.utils.sheet_to_json(worksheet, { header: 1 }) as any[][];
        
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
        
        const jsonData = XLSX.utils.sheet_to_json(worksheet, { header: 1 }) as any[][];
        
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
  const headers = data[0].map((h: any) => String(h).trim());
  const headerMap = {
    companyName: headers.findIndex(h => h === HEADERS.COMPANY_NAME),
    taxNumber: headers.findIndex(h => h === HEADERS.TAX_NUMBER),
    amount: headers.findIndex(h => h === HEADERS.AMOUNT)
  };

  if (headerMap.companyName === -1 || headerMap.taxNumber === -1 || headerMap.amount === -1) {
    return {
      success: false,
      data: [],
      error: `表头不正确，必须包含：${HEADERS.COMPANY_NAME}、${HEADERS.TAX_NUMBER}、${HEADERS.AMOUNT}`
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

    const companyName = String(row[headerMap.companyName] || '').trim();
    const taxNumber = String(row[headerMap.taxNumber] || '').trim();
    const amount = String(row[headerMap.amount] || '').trim();

    rows.push({
      rowNumber,
      companyName,
      taxNumber,
      amount
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

  // 税号校验
  if (!row.taxNumber) {
    return '税号不能为空';
  }
  const normalizedTaxNumber = row.taxNumber.toUpperCase();
  if (!/^[A-Z0-9]{15,20}$/.test(normalizedTaxNumber)) {
    return '税号格式不正确，应为 15-20 位字母或数字';
  }

  // 金额校验
  if (!row.amount) {
    return '开票金额不能为空';
  }
  const amountNum = parseFloat(row.amount);
  if (isNaN(amountNum)) {
    return '开票金额格式不正确';
  }
  if (amountNum < 0.01) {
    return '开票金额必须大于等于 0.01';
  }
  // 检查小数位数
  if (row.amount.includes('.')) {
    const decimalPart = row.amount.split('.')[1];
    if (decimalPart && decimalPart.length > 2) {
      return '开票金额最多 2 位小数';
    }
  }
  // 检查整数位数
  const integerPart = row.amount.split('.')[0];
  if (integerPart.length > 10) {
    return '开票金额最多 10 位整数';
  }

  return null;
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
    const key = `${row.companyName}|${row.taxNumber.toUpperCase()}|${row.amount}`;
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
  const headers = [HEADERS.COMPANY_NAME, HEADERS.TAX_NUMBER, HEADERS.AMOUNT];
  const sampleData = [
    '示例公司A',
    '91500123456789012A',
    '1000.00'
  ];

  return [
    headers.join(','),
    sampleData.join(',')
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