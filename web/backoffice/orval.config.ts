import { defineConfig } from 'orval'

export default defineConfig({
  backoffice: {
    hooks: {
      afterAllFilesWrite:
        'node node_modules/prettier/bin/prettier.cjs --write src/shared/api/generated',
    },
    input: {
      target: '../../contracts/openapi/backoffice-bff.yaml',
      parserOptions: { externalRefs: { allow: ['common/problem.yaml'] } },
    },
    output: {
      mode: 'single',
      target: 'src/shared/api/generated/backoffice.ts',
      schemas: 'src/shared/api/generated/model',
      client: 'react-query',
      httpClient: 'axios',
      override: {
        mutator: { path: 'src/shared/api/client.ts', name: 'apiClient' },
      },
    },
  },
})
