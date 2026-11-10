import { createAppRouter } from '@/app/router/router'
import { ReactQueryDevtools } from '@tanstack/react-query-devtools'
import { RouterProvider as TanStackRouterProvider } from '@tanstack/react-router'
import { TanStackRouterDevtools } from '@tanstack/react-router-devtools'
import { useState } from 'react'

type RouterProviderProps = {
  initialPath?: string
}

export function RouterProvider({ initialPath }: RouterProviderProps) {
  const [router] = useState(() => createAppRouter(initialPath))

  return (
    <>
      <TanStackRouterProvider router={router} />
      {import.meta.env.DEV && import.meta.env.MODE !== 'test' && (
        <>
          <ReactQueryDevtools initialIsOpen={false} />
          <TanStackRouterDevtools router={router} position="bottom-right" />
        </>
      )}
    </>
  )
}
