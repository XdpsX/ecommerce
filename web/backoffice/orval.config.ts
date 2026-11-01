import { defineConfig } from 'orval'

import normalizeOpenApi from './scripts/normalize-openapi.js'

const openApiTarget =
  process.env.OPENAPI_URL ?? 'http://localhost:8080/v3/api-docs/backoffice'

export default defineConfig({
  backoffice: {
    input: {
      target: openApiTarget,
      override: {
        transformer: normalizeOpenApi,
      },
    },
    output: {
      mode: 'tags-split',
      target: 'src/api/generated/endpoints',
      schemas: 'src/api/generated/model',
      client: 'react-query',
      httpClient: 'axios',
      clean: false,
      override: {
        mutator: {
          path: 'src/api/client.ts',
          name: 'apiClient',
        },
      },
    },
  },
})
