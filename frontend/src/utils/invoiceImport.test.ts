// @vitest-environment happy-dom

import { describe, expect, it } from 'vitest'
import * as XLSX from 'xlsx'
import {
  findDuplicateRows,
  normalizeAmount,
  normalizeInvoiceCategory,
  parseInvoiceFile,
  validateAllRows,
  validateRow,
  type ParsedInvoiceRow
} from './invoiceImport'

const row = (overrides: Partial<ParsedInvoiceRow> = {}): ParsedInvoiceRow => ({
  rowNumber: 2,
  companyName: '示例公司',
  taxNumber: '91500123456789012A',
  amount: '100.00',
  invoiceType: '技术服务费',
  remark: '',
  ...overrides
})

describe('invoice batch import validation', () => {
  it('parses BOM CSV and preserves row numbers across empty lines', async () => {
    const file = new File([
      '\uFEFF公司名称,税号,开票金额,开票类型\n示例公司A,91500123456789012A,100.00,技术服务费\n,,,\n示例公司B,91500123456789013B,20.50,AI订阅服务费'
    ], 'invoices.csv', { type: 'text/csv' })

    const result = await parseInvoiceFile(file)

    expect(result.success).toBe(true)
    expect(result.data.map(item => item.rowNumber)).toEqual([2, 4])
  })

  it('rejects reordered headers, unknown columns and legacy xls files', async () => {
    const reordered = new File([
      '税号,公司名称,开票金额,开票类型\n91500123456789012A,示例公司,100.00,技术服务费'
    ], 'reordered.csv', { type: 'text/csv' })
    const unknownColumn = new File([
      '未知表头,税号,开票金额,开票类型\n示例公司,91500123456789012A,100.00,技术服务费'
    ], 'unknown.csv', { type: 'text/csv' })
    const legacy = new File(['legacy'], 'legacy.xls')

    await expect(parseInvoiceFile(reordered)).resolves.toMatchObject({ success: false })
    await expect(parseInvoiceFile(unknownColumn)).resolves.toMatchObject({ success: false })
    await expect(parseInvoiceFile(legacy)).resolves.toMatchObject({
      success: false,
      error: '不支持的文件格式，请使用 CSV 或 XLSX'
    })
  })

  it('parses the first XLSX worksheet with the strict template', async () => {
    const workbook = XLSX.utils.book_new()
    XLSX.utils.book_append_sheet(workbook, XLSX.utils.aoa_to_sheet([
      ['公司名称', '税号', '开票金额', '开票类型'],
      ['示例公司', '91500123456789012A', '100.00', 'AI订阅服务费']
    ]), '导入模板')
    const bytes = XLSX.write(workbook, { type: 'array', bookType: 'xlsx' })
    const file = new File([bytes], 'invoices.xlsx')

    const result = await parseInvoiceFile(file)

    expect(result).toMatchObject({
      success: true,
      data: [{ rowNumber: 2, companyName: '示例公司', amount: '100.00', invoiceType: 'AI订阅服务费' }]
    })
  })

  it('normalizes valid decimal strings without floating point arithmetic', () => {
    expect(normalizeAmount('001.2')).toBe('1.20')
    expect(normalizeAmount('0.01')).toBe('0.01')
    expect(normalizeAmount('100abc')).toBeNull()
    expect(normalizeAmount('1e3')).toBeNull()
    expect(normalizeAmount('-1.00')).toBeNull()
    expect(normalizeAmount('1.001')).toBeNull()
  })

  it('enforces the amount lower and upper bounds', () => {
    expect(validateRow(row({ amount: '0' }))).toBe('开票金额必须大于等于 0.01')
    expect(validateRow(row({ amount: '12345678901.00' }))).toBe('开票金额格式不正确')
    expect(validateRow(row({ amount: '12.345' }))).toBe('开票金额格式不正确')
  })

  it('accepts allowed invoice types and rejects unsupported invoice types', () => {
    expect(validateRow(row({ invoiceType: '技术服务费' }))).toBeNull()
    expect(validateRow(row({ invoiceType: 'AI订阅服务费' }))).toBeNull()
    expect(validateRow(row({ invoiceType: '计算服务费' }))).toBeNull()
    expect(validateRow(row({ invoiceType: '研发和技术服务' }))).toBeNull()
    expect(validateRow(row({ invoiceType: '咨询服务费' }))).toBe('开票类型必须为技术服务费、AI订阅服务费、计算服务费或研发和技术服务')
  })

  it('compares duplicate rows after normalization', () => {
    const rows = [
      row({ rowNumber: 2, taxNumber: 'abcde12345678901', amount: '100' }),
      row({ rowNumber: 4, companyName: ' 示例公司 ', taxNumber: 'ABCDE12345678901', amount: '100.00' })
    ]
    expect(findDuplicateRows(rows)).toEqual([4])
  })

  it('keeps valid rows and attaches row-level errors', () => {
    const result = validateAllRows([
      row({ rowNumber: 2, taxNumber: '' }),
      row({ rowNumber: 5, taxNumber: 'A'.repeat(101) })
    ])
    expect(result[0].error).toBeUndefined()
    expect(result[1].error).toBe('税号不能超过 100 个字符')
    expect(result[1].rowNumber).toBe(5)
  })

  it('normalizes invoice category correctly', () => {
    expect(normalizeInvoiceCategory('专票')).toBe('VAT_SPECIAL')
    expect(normalizeInvoiceCategory('专用发票')).toBe('VAT_SPECIAL')
    expect(normalizeInvoiceCategory('增值税专用发票')).toBe('VAT_SPECIAL')
    expect(normalizeInvoiceCategory('VAT_SPECIAL')).toBe('VAT_SPECIAL')
    expect(normalizeInvoiceCategory('vat_special')).toBe('VAT_SPECIAL')
    expect(normalizeInvoiceCategory('普票')).toBe('NORMAL')
    expect(normalizeInvoiceCategory('普通发票')).toBe('NORMAL')
    expect(normalizeInvoiceCategory('增值税普通发票')).toBe('NORMAL')
    expect(normalizeInvoiceCategory('NORMAL')).toBe('NORMAL')
    expect(normalizeInvoiceCategory('')).toBe('NORMAL')
    expect(normalizeInvoiceCategory(undefined)).toBe('NORMAL')
    expect(normalizeInvoiceCategory('未知')).toBe('NORMAL')
  })

  it('validates invoice category in validateRow', () => {
    expect(validateRow(row({ invoiceCategory: 'NORMAL' }))).toBeNull()
    expect(validateRow(row({ invoiceCategory: 'VAT_SPECIAL' }))).toBeNull()
    expect(validateRow(row({ invoiceCategory: '专票' }))).toBeNull()
    expect(validateRow(row({ invoiceCategory: '普票' }))).toBeNull()
    expect(validateRow(row({ invoiceCategory: '电子专票' }))).toBe('发票票种必须为普票或专票（也可填 NORMAL 或 VAT_SPECIAL）')
  })

  it('differentiates duplicate rows by category', () => {
    const rows = [
      row({ rowNumber: 2, amount: '100.00', invoiceCategory: 'NORMAL' }),
      row({ rowNumber: 3, amount: '100.00', invoiceCategory: 'VAT_SPECIAL' })
    ]
    expect(findDuplicateRows(rows)).toEqual([])

    const dupRows = [
      row({ rowNumber: 2, amount: '100.00', invoiceCategory: 'NORMAL' }),
      row({ rowNumber: 3, amount: '100.00', invoiceCategory: '普票' })
    ]
    expect(findDuplicateRows(dupRows)).toEqual([3])
  })

  it('parses CSV with 6 columns including 发票票种 and 备注', async () => {
    const csv = '\uFEFF公司名称,税号,开票金额,开票类型,发票票种,备注\n' +
      '公司A,91500123456789012A,100.00,技术服务费,专票,加急\n' +
      '公司B,91500123456789013B,200.00,AI订阅服务费,普票,常规'
    const file = new File([csv], 'invoices_6col.csv', { type: 'text/csv' })
    const result = await parseInvoiceFile(file)
    expect(result.success).toBe(true)
    expect(result.data).toHaveLength(2)
    expect(result.data[0].invoiceCategory).toBe('专票')
    expect(result.data[0].remark).toBe('加急')
    expect(result.data[1].invoiceCategory).toBe('普票')
    expect(result.data[1].remark).toBe('常规')
  })
})

