export function getSafeReturnPath(value: unknown): string {
  if (
    typeof value !== 'string' ||
    !value.startsWith('/') ||
    value.startsWith('//')
  ) {
    return '/'
  }

  try {
    const destination = new URL(value, window.location.origin)

    if (destination.origin !== window.location.origin) {
      return '/'
    }

    const returnPath = `${destination.pathname}${destination.search}${destination.hash}`

    if (
      !destination.pathname.startsWith('/') ||
      destination.pathname.startsWith('//')
    ) {
      return '/'
    }

    return returnPath
  } catch {
    return '/'
  }
}
