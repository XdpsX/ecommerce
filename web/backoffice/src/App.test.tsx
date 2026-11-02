import App from './App'
import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

describe('App', () => {
  it('renders the backoffice bootstrap page', () => {
    render(<App />)

    expect(
      screen.getByRole('heading', { level: 1, name: 'Ecommerce Backoffice' }),
    ).toBeInTheDocument()
    expect(screen.getByText('Bootstrap only.')).toBeInTheDocument()
  })
})
