import { defineTransformer } from 'orval'

const invalidSecuritySchemeName = 'Bearer Authorization'
const normalizedSecuritySchemeName = 'bearerAuth'

function renameSecuritySchemeKey(value: unknown): void {
  if (Array.isArray(value)) {
    value.forEach(renameSecuritySchemeKey)

    return
  }

  if (value === null || typeof value !== 'object') {
    return
  }

  const record = value as Record<string, unknown>

  if (Object.hasOwn(record, invalidSecuritySchemeName)) {
    if (Object.hasOwn(record, normalizedSecuritySchemeName)) {
      throw new Error(
        `Cannot normalize OpenAPI security scheme: both "${invalidSecuritySchemeName}" and "${normalizedSecuritySchemeName}" exist.`,
      )
    }

    record[normalizedSecuritySchemeName] = record[invalidSecuritySchemeName]
    delete record[invalidSecuritySchemeName]
  }

  Object.values(record).forEach(renameSecuritySchemeKey)
}

export default defineTransformer((spec) => {
  const normalizedSpec = structuredClone(spec)
  renameSecuritySchemeKey(normalizedSpec)

  return normalizedSpec
})
