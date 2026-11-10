import { createQueryClient } from '@/app/config/query'
import { QueryClientProvider } from '@tanstack/react-query'
import { type ReactNode, useState } from 'react'

type QueryProviderProps = {
  children: ReactNode
}

export function QueryProvider({ children }: QueryProviderProps) {
  const [queryClient] = useState(createQueryClient)

  return (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  )
}
