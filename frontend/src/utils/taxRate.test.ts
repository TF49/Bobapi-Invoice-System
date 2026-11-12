import { describe, expect, it } from 'vitest'
import { coerceTaxRateForSeller, getTaxRateOptions, isTaxRateAllowedForSeller } from './taxRate'

describe('tax rate by seller company', () => {
  it('offers only 免税 for 普通公司 and all rates for 科技公司', () => {
    expect(getTaxRateOptions('DEFAULT').map(option => option.value)).toEqual(['EXEMPT'])
    expect(getTaxRateOptions(undefined).map(option => option.value)).toEqual(['EXEMPT'])
    expect(getTaxRateOptions('TECH').map(option => option.value)).toEqual(['EXEMPT', '1%', '3%', '6%'])
  })

  it('rejects non-exempt rates for 普通公司', () => {
    expect(isTaxRateAllowedForSeller('EXEMPT', 'DEFAULT')).toBe(true)
    expect(isTaxRateAllowedForSeller('6%', 'DEFAULT')).toBe(false)
    expect(isTaxRateAllowedForSeller('1%', 'DEFAULT')).toBe(false)
    expect(isTaxRateAllowedForSeller('3%', 'TECH')).toBe(true)
  })

  it('falls back to 免税 when the current rate is not allowed for the seller', () => {
    expect(coerceTaxRateForSeller('6%', 'DEFAULT')).toBe('EXEMPT')
    expect(coerceTaxRateForSeller('6%', 'TECH')).toBe('6%')
    expect(coerceTaxRateForSeller(null, 'TECH')).toBe('EXEMPT')
  })
})
