// @vitest-environment happy-dom

import { describe, expect, it } from 'vitest'
import * as XLSX from 'xlsx'
import {
  findDuplicateRows,
  normalizeAmount,
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
  ...overrides
})

describe('invoice batch import validation', () => {
  it('parses BOM CSV and preserves row numbers across empty lines', async () => {
    const file = new File([
      '\uFEFF公司名称,税号,开票金额\n示例公司A,91500123456789012A,100.00\n,,\n示例公司B,91500123456789013B,20.50'
    ], 'invoices.csv', { type: 'text/csv' })

    const result = await parseInvoiceFile(file)

    expect(result.success).toBe(true)
    expect(result.data.map(item => item.rowNumber)).toEqual([2, 4])
  })

  it('rejects reordered headers, unknown columns and legacy xls files', async () => {
    const reordered = new File([
      '税号,公司名称,开票金额\n91500123456789012A,示例公司,100.00'
    ], 'reordered.csv', { type: 'text/csv' })
    const unknownColumn = new File([
      '公司名称,税号,开票金额,备注\n示例公司,91500123456789012A,100.00,测试'
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
      ['公司名称', '税号', '开票金额'],
      ['示例公司', '91500123456789012A', '100.00']
    ]), '导入模板')
    const bytes = XLSX.write(workbook, { type: 'array', bookType: 'xlsx' })
    const file = new File([bytes], 'invoices.xlsx')

    const result = await parseInvoiceFile(file)

    expect(result).toMatchObject({
      success: true,
      data: [{ rowNumber: 2, companyName: '示例公司', amount: '100.00' }]
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

  it('compares duplicate rows after normalization', () => {
    const rows = [
      row({ rowNumber: 2, taxNumber: 'abcde12345678901', amount: '100' }),
      row({ rowNumber: 4, companyName: ' 示例公司 ', taxNumber: 'ABCDE12345678901', amount: '100.00' })
    ]
    expect(findDuplicateRows(rows)).toEqual([4])
  })

  it('keeps valid rows and attaches row-level errors', () => {
    const result = validateAllRows([
      row({ rowNumber: 2 }),
      row({ rowNumber: 5, taxNumber: 'bad' })
    ])
    expect(result[0].error).toBeUndefined()
    expect(result[1].error).toBe('税号格式不正确，应为 15-20 位字母或数字')
    expect(result[1].rowNumber).toBe(5)
  })
})
