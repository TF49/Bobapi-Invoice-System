/**
 * 税率 / 是否免税：EXEMPT-免税（默认），1%、3%、6%
 * 与后端 InvoiceService.ALLOWED_TAX_RATES 保持一致。
 */
export type TaxRate = 'EXEMPT' | '1%' | '3%' | '6%'

export const DEFAULT_TAX_RATE: TaxRate = 'EXEMPT'

export const TAX_RATE_OPTIONS: ReadonlyArray<{ label: string; value: TaxRate }> = [
  { label: '免税', value: 'EXEMPT' },
  { label: '1%', value: '1%' },
  { label: '3%', value: '3%' },
  { label: '6%', value: '6%' }
]

/** 展示用：EXEMPT / 空值显示为「免税」，其余原样显示 */
export function formatTaxRate(taxRate?: string | null): string {
  if (!taxRate || taxRate === 'EXEMPT') return '免税'
  return taxRate
}

/**
 * 标准化税率：空值默认 EXEMPT（免税），支持 免税 / 1 / 0.01 / 1% / 全角％ 等写法；无法识别时返回 null
 */
export function normalizeTaxRate(value?: string | null): TaxRate | null {
  if (!value || !value.trim()) {
    return DEFAULT_TAX_RATE
  }
  const trimmed = value.trim().replace(/％/g, '%').replace(/\s+/g, '').toUpperCase()
  if (trimmed === '免税' || trimmed === 'EXEMPT') return 'EXEMPT'
  if (['1%', '1', '0.01'].includes(trimmed)) return '1%'
  if (['3%', '3', '0.03'].includes(trimmed)) return '3%'
  if (['6%', '6', '0.06'].includes(trimmed)) return '6%'
  return null
}
