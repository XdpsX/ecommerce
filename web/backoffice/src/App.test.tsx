import i18n from './app/i18n'
import { getPreferredLanguage, LANGUAGE_STORAGE_KEY } from './app/i18n'
import { AppProvider } from './app/providers/AppProvider'
import { cleanup, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'

function renderApp(initialPath = '/') {
  return render(<AppProvider initialPath={initialPath} />)
}

beforeEach(async () => {
  await i18n.changeLanguage('en')
  window.localStorage.clear()
  document.documentElement.lang = 'en'
})

afterEach(() => {
  cleanup()
  window.history.replaceState({}, '', '/')
})

describe('Backoffice foundation', () => {
  it('renders the temporary dashboard and informational sign-in entry', async () => {
    const user = userEvent.setup()

    renderApp()

    expect(
      await screen.findByRole('link', { name: 'Skip to content' }),
    ).toHaveAttribute('href', '#main-content')
    expect(
      screen.getByRole('navigation', { name: 'Primary navigation' }),
    ).toBeInTheDocument()
    expect(
      await screen.findByRole('heading', { name: 'Dashboard' }),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('heading', { name: 'Welcome to the backoffice.' }),
    ).toBeInTheDocument()

    await user.click(screen.getByRole('link', { name: 'Sign in' }))

    expect(
      await screen.findByRole('heading', { name: 'Sign in to your workspace' }),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('button', { name: 'Sign-in is not available yet' }),
    ).toBeDisabled()
  })

  it('provides an accessible navigation toggle', async () => {
    const user = userEvent.setup()

    renderApp()

    const toggle = await screen.findByRole('button', {
      name: 'Open navigation',
    })
    const navigation = screen.getByRole('navigation', {
      name: 'Primary navigation',
    })

    expect(toggle).toHaveAttribute('aria-expanded', 'false')
    await user.click(toggle)

    expect(
      screen.getByRole('button', { name: 'Close navigation' }),
    ).toHaveAttribute('aria-expanded', 'true')
    expect(navigation).toHaveClass('is-open')
    expect(
      within(navigation).getByRole('link', { name: 'Sign in' }),
    ).toBeInTheDocument()
  })

  it('switches language, stores the choice and updates the document language', async () => {
    const user = userEvent.setup()

    renderApp()

    const selector = await screen.findByRole('combobox', { name: 'Language' })
    await user.selectOptions(selector, 'vi')

    expect(
      await screen.findByRole('heading', { name: 'Dashboard' }),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('heading', {
        name: 'Chào mừng bạn đến với trang quản trị.',
      }),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('navigation', { name: 'Điều hướng chính' }),
    ).toBeInTheDocument()
    expect(document.documentElement.lang).toBe('vi')
    expect(window.localStorage.getItem(LANGUAGE_STORAGE_KEY)).toBe('vi')
  })

  it('restores a saved language and falls back to English for unsupported preferences', async () => {
    window.localStorage.setItem(LANGUAGE_STORAGE_KEY, 'vi')
    await i18n.changeLanguage(getPreferredLanguage(window.localStorage))

    const vietnameseApp = renderApp()
    expect(
      await screen.findByRole('heading', { name: 'Dashboard' }),
    ).toBeInTheDocument()
    vietnameseApp.unmount()

    window.localStorage.setItem(LANGUAGE_STORAGE_KEY, 'fr')
    const fallbackLanguage = getPreferredLanguage(window.localStorage)
    expect(fallbackLanguage).toBe('en')
    await i18n.changeLanguage(fallbackLanguage)

    renderApp()
    expect(
      await screen.findByRole('heading', { name: 'Dashboard' }),
    ).toBeInTheDocument()
    expect(document.documentElement.lang).toBe('en')
  })

  it('uses English resources when a Vietnamese translation is missing', () => {
    i18n.addResource('en', 'common', 'fallbackProbe', 'English fallback')

    expect(i18n.t('fallbackProbe', { lng: 'vi', ns: 'common' })).toBe(
      'English fallback',
    )
  })

  it('loads translations from their shared and feature-owned namespaces', () => {
    expect(i18n.options.ns).toEqual(['common', 'auth', 'home'])
    expect(i18n.t('navigation.dashboard', { ns: 'common' })).toBe('Dashboard')
    expect(i18n.t('login.title', { ns: 'auth' })).toBe(
      'Sign in to your workspace',
    )
    expect(i18n.t('dashboard.greeting', { ns: 'home' })).toBe(
      'Welcome to the backoffice.',
    )
  })

  it('translates login entry and not-found states', async () => {
    await i18n.changeLanguage('vi')
    const loginApp = renderApp('/login')

    expect(
      await screen.findByRole('heading', {
        name: 'Đăng nhập vào không gian làm việc',
      }),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('button', { name: 'Đăng nhập hiện chưa khả dụng' }),
    ).toBeDisabled()
    loginApp.unmount()

    renderApp('/missing')
    expect(
      await screen.findByRole('heading', {
        name: /Không tìm thấy trang này\./,
      }),
    ).toBeInTheDocument()
    expect(screen.getByText('404')).toBeInTheDocument()
    expect(
      screen.getByRole('link', { name: 'Quay lại bảng điều khiển' }),
    ).toBeInTheDocument()
    expect(i18n.t('routeError.title', { ns: 'common' })).toBe(
      'Không thể hiển thị trang này.',
    )
    expect(i18n.t('routeError.retry', { ns: 'common' })).toBe('Thử lại')
  })

  it('shows a deliberate not-found page for unknown routes in English', async () => {
    renderApp('/missing')

    expect(
      await screen.findByRole('heading', {
        name: /We could not find that page\./,
      }),
    ).toBeInTheDocument()
  })
})
