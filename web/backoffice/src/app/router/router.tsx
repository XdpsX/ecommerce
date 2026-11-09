import { NotFound, RouteError } from './RouteStates'
import { createAppHistory } from '@/app/config/router'
import { RequireAuth } from '@/app/guards/RequireAuth'
import { RequireGuest } from '@/app/guards/RequireGuest'
import { AppShell } from '@/app/layout/AppShell'
import { LoginPage } from '@/features/auth/pages/LoginPage'
import { HomePage } from '@/features/home/pages/HomePage'
import { getSafeReturnPath } from '@/shared/lib/returnPath'
import {
  createRootRoute,
  createRoute,
  createRouter,
} from '@tanstack/react-router'

const rootRoute = createRootRoute({
  component: AppShell,
  errorComponent: RouteError,
  notFoundComponent: NotFound,
})

const indexRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/',
  component: () => (
    <RequireAuth>
      <HomePage />
    </RequireAuth>
  ),
})

const loginRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/login',
  validateSearch: (
    search: Record<string, unknown>,
  ): { returnTo?: string; error?: string } => {
    const returnTo = getSafeReturnPath(search.returnTo)

    return {
      ...(returnTo !== '/' ? { returnTo } : {}),
      ...(search.error === 'authentication' ? { error: 'authentication' } : {}),
    }
  },
  component: () => (
    <RequireGuest>
      <LoginPage />
    </RequireGuest>
  ),
})

const routeTree = rootRoute.addChildren([indexRoute, loginRoute])

export function createAppRouter(initialPath?: string) {
  return createRouter({ routeTree, history: createAppHistory(initialPath) })
}

declare module '@tanstack/react-router' {
  interface Register {
    router: ReturnType<typeof createAppRouter>
  }
}
