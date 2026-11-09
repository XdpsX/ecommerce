import { LanguageSwitcher } from './LanguageSwitcher'
import { PrimaryNavigation } from './PrimaryNavigation'
import { Button } from '@/shared/components/ui/button'
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetTitle,
  SheetTrigger,
} from '@/shared/components/ui/sheet'
import { Link } from '@tanstack/react-router'
import { Menu } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'

export function AppHeader() {
  const [menuOpen, setMenuOpen] = useState(false)
  const { t } = useTranslation('common')

  return (
    <header className="app-header flex h-[4.75rem] items-center border-b border-border bg-card px-6 md:px-[max(1.5rem,calc((100vw-74rem)/2))]">
      <Link className="brand" to="/" aria-label={t('app.brandLabel')}>
        <span className="brand-mark" aria-hidden="true">
          C
        </span>
        <span>Commerce</span>
        <span className="brand-divider" aria-hidden="true" />
        <span className="brand-section">{t('app.brandArea')}</span>
      </Link>
      <PrimaryNavigation
        className="desktop-navigation"
        onNavigate={() => setMenuOpen(false)}
      />
      <div className="header-tools">
        <LanguageSwitcher />
        <Sheet open={menuOpen} onOpenChange={setMenuOpen}>
          <SheetTrigger
            render={
              <Button
                aria-label={t(
                  menuOpen ? 'navigation.close' : 'navigation.open',
                )}
                className="mobile-menu-trigger"
                size="icon"
                variant="outline"
              />
            }
          >
            <Menu aria-hidden="true" />
          </SheetTrigger>
          <SheetContent
            aria-describedby="mobile-navigation-description"
            className="mobile-navigation-sheet"
            closeLabel={t('navigation.close')}
            side="right"
          >
            <SheetTitle className="sr-only">{t('navigation.label')}</SheetTitle>
            <SheetDescription
              className="sr-only"
              id="mobile-navigation-description"
            >
              {t('navigation.label')}
            </SheetDescription>
            <PrimaryNavigation
              className="mobile-navigation-list"
              onNavigate={() => setMenuOpen(false)}
            />
          </SheetContent>
        </Sheet>
      </div>
    </header>
  )
}
