function numberOrNull(value) {
  return typeof value === 'number' && Number.isFinite(value) ? value : null
}

function metricValue(version, key) {
  return numberOrNull(version && version.metrics && version.metrics[key] && version.metrics[key].percentage)
}

function comparisonValue(before, after) {
  return { before, after, delta: before === null || after === null ? null : after - before }
}

function warningKey(warning) {
  return JSON.stringify([
    warning.code ?? null,
    warning.severity ?? null,
    warning.date ?? null,
    warning.itemIndex ?? null,
    warning.poiId ?? null,
    warning.message ?? null,
    warning.knownValue ?? null,
    warning.requiredAdjustment ?? null
  ])
}

function multisetDifference(source, comparedWith) {
  const remaining = new Map()
  comparedWith.forEach(item => {
    const key = warningKey(item)
    remaining.set(key, (remaining.get(key) || 0) + 1)
  })
  return source.filter(item => {
    const key = warningKey(item)
    const count = remaining.get(key) || 0
    if (!count) return true
    remaining.set(key, count - 1)
    return false
  })
}

function describeWarning(warning = {}) {
  const parts = [warning.severity || '未知级别', warning.code || 'UNKNOWN']
  if (warning.date) parts.push(`日期：${warning.date}`)
  if (warning.itemIndex != null) parts.push(`活动序号：${warning.itemIndex}`)
  if (warning.poiId) parts.push(`POI：${warning.poiId}`)
  if (warning.message) parts.push(`说明：${warning.message}`)
  if (warning.knownValue) parts.push(`已知值：${warning.knownValue}`)
  if (warning.requiredAdjustment) parts.push(`需要调整：${warning.requiredAdjustment}`)
  return parts.join(' · ')
}

function comparePlanVersions(before = {}, after = {}, currentVersionNumber) {
  const beforeWarnings = Array.isArray(before.warnings) ? before.warnings : []
  const afterWarnings = Array.isArray(after.warnings) ? after.warnings : []
  return {
    before: { ...before, isCurrent: before.versionNumber === currentVersionNumber },
    after: { ...after, isCurrent: after.versionNumber === currentVersionNumber },
    cost: comparisonValue(numberOrNull(before.estimatedTotalCost), numberOrNull(after.estimatedTotalCost)),
    metrics: {
      sourceCoverage: comparisonValue(metricValue(before, 'sourceCoverage'), metricValue(after, 'sourceCoverage')),
      routeCoverage: comparisonValue(metricValue(before, 'routeCoverage'), metricValue(after, 'routeCoverage')),
      budgetCompleteness: comparisonValue(metricValue(before, 'budgetCompleteness'), metricValue(after, 'budgetCompleteness')),
      rulePassRate: comparisonValue(metricValue(before, 'rulePassRate'), metricValue(after, 'rulePassRate'))
    },
    warnings: {
      beforeCount: beforeWarnings.length,
      afterCount: afterWarnings.length,
      added: multisetDifference(afterWarnings, beforeWarnings),
      removed: multisetDifference(beforeWarnings, afterWarnings)
    }
  }
}

module.exports = { comparePlanVersions, describeWarning }
