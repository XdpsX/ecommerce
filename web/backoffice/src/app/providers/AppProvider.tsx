import '@/app/i18n'
import { QueryProvider } from './QueryProvider'
import { RouterProvider } from './RouterProvider'

type AppProviderProps = {
  initialPath?: string
}

export function AppProvider({ initialPath }: AppProviderProps) {
  return (
    <QueryProvider>
      <RouterProvider {...(initialPath ? { initialPath } : {})} />
    </QueryProvider>
  )
}
