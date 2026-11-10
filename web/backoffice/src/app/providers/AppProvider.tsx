import '@/app/i18n'
import { QueryProvider } from './QueryProvider'
import { RouterProvider } from './RouterProvider'
import { SessionProvider } from '@/features/auth/SessionProvider'

type AppProviderProps = {
  initialPath?: string
}

export function AppProvider({ initialPath }: AppProviderProps) {
  return (
    <QueryProvider>
      <SessionProvider>
        <RouterProvider {...(initialPath ? { initialPath } : {})} />
      </SessionProvider>
    </QueryProvider>
  )
}
