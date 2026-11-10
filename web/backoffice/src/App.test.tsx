import i18n, { getPreferredLanguage, LANGUAGE_STORAGE_KEY } from './app/i18n'
import { AppProvider } from './app/providers/AppProvider'
import { server } from './test/server'
import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'

let authenticated = true
let logoutCalls = 0
let sessionCalls = 0
const subject = '11111111-1111-4111-8111-111111111111'
function renderApp(initialPath = '/') {
  return render(<AppProvider initialPath={initialPath} />)
}
beforeEach(async () => {
  await i18n.changeLanguage('en')
  localStorage.clear()
  authenticated = true
  logoutCalls = 0
  sessionCalls = 0
  server.use(
    http.get('*/bff/session', () => {
      sessionCalls++

      return authenticated
        ? HttpResponse.json({ subject })
        : new HttpResponse(null, { status: 401 })
    }),
    http.get('*/bff/csrf', () =>
      HttpResponse.json({ token: 'csrf-test', headerName: 'X-CSRF-TOKEN' }),
    ),
    http.post('*/bff/logout', ({ request }) => {
      logoutCalls++
      expect(request.headers.get('X-CSRF-TOKEN')).toBe('csrf-test')
      authenticated = false

      return new HttpResponse(null, { status: 204 })
    }),
  )
})
afterEach(() => {
  cleanup()
  window.history.replaceState({}, '', '/')
})
describe('Backoffice authentication foundation', () => {
  it('opens the authenticated dashboard and signs out with CSRF', async () => {
    const user = userEvent.setup()
    renderApp()
    expect(
      await screen.findByRole('heading', { name: 'Dashboard' }),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('link', { name: 'Skip to content' }),
    ).toHaveAttribute('href', '#main-content')
    await user.click(screen.getByRole('button', { name: 'Sign out' }))
    expect(
      await screen.findByRole('heading', { name: 'Sign in to your workspace' }),
    ).toBeInTheDocument()
    expect(logoutCalls).toBe(1)
    expect(
      screen.getByText(/Identity SSO may remain active/),
    ).toBeInTheDocument()
  })
  it('protects the dashboard and omits the default return path', async () => {
    authenticated = false
    renderApp()
    const action = await screen.findByRole('heading', {
      name: 'Sign in to your workspace',
    })
    expect(action).toBeInTheDocument()
    expect(
      screen.queryByRole('heading', { name: 'Dashboard' }),
    ).not.toBeInTheDocument()
    expect(
      screen
        .getAllByRole('link', { name: 'Sign in' })
        .find((link) => link.getAttribute('href')?.startsWith('/oauth2/')),
    ).toHaveAttribute('href', '/oauth2/authorization/identity?ui_locales=en')
  })
  it('revalidates the session when entering the protected dashboard again', async () => {
    authenticated = false
    renderApp('/login')

    expect(
      await screen.findByRole('heading', {
        name: 'Sign in to your workspace',
      }),
    ).toBeInTheDocument()
    expect(sessionCalls).toBe(1)

    authenticated = true
    fireEvent.click(
      screen.getByRole('link', { name: 'Commerce Backoffice home' }),
    )

    expect(
      await screen.findByRole('heading', { name: 'Dashboard' }),
    ).toBeInTheDocument()
    expect(sessionCalls).toBeGreaterThan(1)
  })
  it('keeps authenticated users out of guest-only login', async () => {
    renderApp('/login')
    expect(
      await screen.findByRole('heading', { name: 'Dashboard' }),
    ).toBeInTheDocument()
    expect(
      screen.queryByRole('heading', { name: 'Sign in to your workspace' }),
    ).not.toBeInTheDocument()
  })
  it('rejects an external return destination', async () => {
    authenticated = false
    renderApp('/login?returnTo=https%3A%2F%2Fevil.example')
    await screen.findByRole('heading', { name: 'Sign in to your workspace' })
    expect(
      screen
        .getAllByRole('link', { name: 'Sign in' })
        .find((link) => link.getAttribute('href')?.startsWith('/oauth2/')),
    ).toHaveAttribute('href', '/oauth2/authorization/identity?ui_locales=en')
  })
  it('forwards the selected locale and retains only a non-default return path', async () => {
    authenticated = false
    const user = userEvent.setup()
    renderApp('/login?returnTo=%2Forders%3Fstate%3Dpending%23today')
    await screen.findByRole('heading', { name: 'Sign in to your workspace' })

    const signIn = screen
      .getAllByRole('link', { name: 'Sign in' })
      .find((link) => link.getAttribute('href')?.startsWith('/oauth2/'))!
    const englishAuthorization = new URL(
      signIn.getAttribute('href')!,
      window.location.origin,
    )
    expect(englishAuthorization.searchParams.get('ui_locales')).toBe('en')
    expect(englishAuthorization.searchParams.get('returnTo')).toBe(
      '/orders?state=pending#today',
    )

    await user.click(screen.getByRole('combobox', { name: 'Language' }))
    await waitFor(() =>
      expect(screen.getByRole('option', { name: 'English' })).toHaveFocus(),
    )
    await user.keyboard('{ArrowDown}')
    await waitFor(() =>
      expect(screen.getByRole('option', { name: 'Vietnamese' })).toHaveFocus(),
    )
    await user.keyboard('{Enter}')
    const vietnameseAuthorization = new URL(
      screen
        .getAllByRole('link', { name: 'Đăng nhập' })
        .find((link) => link.getAttribute('href')?.startsWith('/oauth2/'))!
        .getAttribute('href')!,
      window.location.origin,
    )
    expect(vietnameseAuthorization.searchParams.get('ui_locales')).toBe('vi')
    expect(vietnameseAuthorization.searchParams.get('returnTo')).toBe(
      '/orders?state=pending#today',
    )
  })
  it('accepts an old explicit root return path but emits a clean OAuth URL', async () => {
    authenticated = false
    renderApp('/login?returnTo=%2F')
    await screen.findByRole('heading', { name: 'Sign in to your workspace' })

    const authorization = new URL(
      screen
        .getAllByRole('link', { name: 'Sign in' })
        .find((link) => link.getAttribute('href')?.startsWith('/oauth2/'))!
        .getAttribute('href')!,
      window.location.origin,
    )
    expect(authorization.searchParams.get('ui_locales')).toBe('en')
    expect(authorization.searchParams.has('returnTo')).toBe(false)
  })
  it('hides private content until a pending session is resolved', async () => {
    let release: (() => void) | undefined
    const gate = new Promise<void>((resolve) => {
      release = resolve
    })
    server.use(
      http.get('*/bff/session', async () => {
        await gate

        return new HttpResponse(null, { status: 401 })
      }),
    )
    renderApp()
    expect(await screen.findByRole('status')).toHaveTextContent(
      'Checking your session…',
    )
    expect(
      screen.queryByRole('heading', { name: 'Dashboard' }),
    ).not.toBeInTheDocument()
    release?.()
    expect(
      await screen.findByRole('heading', { name: 'Sign in to your workspace' }),
    ).toBeInTheDocument()
  })
  it('offers retry on infrastructure failure without pretending logout', async () => {
    const user = userEvent.setup()
    server.use(
      http.get('*/bff/session', () => new HttpResponse(null, { status: 503 })),
    )
    renderApp()
    expect(
      await screen.findByRole('heading', {
        name: 'Your session could not be checked.',
      }),
    ).toBeInTheDocument()
    expect(
      screen.queryByRole('heading', { name: 'Sign in to your workspace' }),
    ).not.toBeInTheDocument()
    server.use(http.get('*/bff/session', () => HttpResponse.json({ subject })))
    await user.click(screen.getByRole('button', { name: 'Try again' }))
    expect(
      await screen.findByRole('heading', { name: 'Dashboard' }),
    ).toBeInTheDocument()
  })
  it('shows failed logout and reconciles the still-authenticated session without replay', async () => {
    const user = userEvent.setup()
    server.use(
      http.post('*/bff/logout', () => {
        logoutCalls++

        return new HttpResponse(null, { status: 503 })
      }),
    )
    renderApp()
    await user.click(await screen.findByRole('button', { name: 'Sign out' }))
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Sign-out was not confirmed',
    )
    expect(
      await screen.findByRole('heading', { name: 'Dashboard' }),
    ).toBeInTheDocument()
    expect(logoutCalls).toBe(1)
  })
  it('reconciles an uncertain logout which actually invalidated the server session', async () => {
    const user = userEvent.setup()
    server.use(
      http.post('*/bff/logout', () => {
        authenticated = false
        logoutCalls++

        return HttpResponse.error()
      }),
    )
    renderApp()
    await user.click(await screen.findByRole('button', { name: 'Sign out' }))
    expect(
      await screen.findByRole('heading', { name: 'Sign in to your workspace' }),
    ).toBeInTheDocument()
    expect(screen.getByRole('alert')).toBeInTheDocument()
    expect(logoutCalls).toBe(1)
  })
  it('supports accessible mobile navigation and English/Vietnamese persistence', async () => {
    const user = userEvent.setup()
    renderApp()
    await screen.findByRole('heading', { name: 'Dashboard' })
    const toggle = screen.getByRole('button', { name: 'Open navigation' })
    await user.click(toggle)
    const dialog = await screen.findByRole('dialog')
    expect(dialog).toBeInTheDocument()
    expect(
      within(dialog).getByRole('navigation', {
        name: 'Primary navigation',
      }),
    ).toBeInTheDocument()
    await user.keyboard('{Escape}')
    await waitFor(() =>
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument(),
    )
    expect(toggle).toHaveFocus()
    await user.click(toggle)
    await user.click(
      within(await screen.findByRole('dialog')).getByRole('link', {
        name: 'Dashboard',
      }),
    )
    await waitFor(() =>
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument(),
    )
    expect(toggle).toHaveFocus()
    await user.click(screen.getByRole('combobox', { name: 'Language' }))
    await waitFor(() =>
      expect(screen.getByRole('option', { name: 'English' })).toHaveFocus(),
    )
    await user.keyboard('{ArrowDown}')
    await waitFor(() =>
      expect(screen.getByRole('option', { name: 'Vietnamese' })).toHaveFocus(),
    )
    await user.keyboard('{Enter}')
    expect(
      await screen.findByRole('heading', {
        name: 'Chào mừng bạn đến với trang quản trị.',
      }),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('combobox', { name: 'Ngôn ngữ' }),
    ).toHaveTextContent('Tiếng Việt')
    expect(document.documentElement.lang).toBe('vi')
    expect(localStorage.getItem(LANGUAGE_STORAGE_KEY)).toBe('vi')
    expect(getPreferredLanguage(localStorage)).toBe('vi')
    await user.click(screen.getByRole('button', { name: 'Mở điều hướng' }))
    await user.click(
      within(await screen.findByRole('dialog')).getByRole('button', {
        name: 'Đăng xuất',
      }),
    )
    await waitFor(() =>
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument(),
    )
    expect(
      await screen.findByRole('heading', {
        name: 'Đăng nhập vào không gian làm việc',
      }),
    ).toBeInTheDocument()
    expect(logoutCalls).toBe(1)
    localStorage.setItem(LANGUAGE_STORAGE_KEY, 'fr')
    expect(getPreferredLanguage(localStorage)).toBe('en')
  })
  it('falls back to English resources and translates not-found states', async () => {
    i18n.addResource('en', 'common', 'fallbackProbe', 'English fallback')
    expect(i18n.t('fallbackProbe', { lng: 'vi', ns: 'common' })).toBe(
      'English fallback',
    )
    await i18n.changeLanguage('vi')
    renderApp('/missing')
    expect(
      await screen.findByRole('heading', { name: /Không tìm thấy trang này/ }),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('link', { name: /bảng điều khiển/i }),
    ).toHaveAttribute('href', '/')
    expect(i18n.t('routeError.title', { ns: 'common' })).toBe(
      'Không thể hiển thị trang này.',
    )
  })
  it('rejects a malformed session payload instead of authenticating it', async () => {
    server.use(
      http.get('*/bff/session', () => HttpResponse.json({ subject: null })),
    )
    renderApp()
    expect(
      await screen.findByRole('heading', {
        name: 'Your session could not be checked.',
      }),
    ).toBeInTheDocument()
    await waitFor(() =>
      expect(
        screen.queryByRole('heading', { name: 'Dashboard' }),
      ).not.toBeInTheDocument(),
    )
  })
})
