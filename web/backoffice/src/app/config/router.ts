import {
  createBrowserHistory,
  createMemoryHistory,
} from '@tanstack/react-router'

export function createAppHistory(initialPath?: string) {
  return initialPath
    ? createMemoryHistory({ initialEntries: [initialPath] })
    : createBrowserHistory()
}
