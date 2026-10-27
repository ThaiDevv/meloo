// Contract paths already start with /api/v1, so the base URL is the backend origin.
// Never put /api here: openapi-fetch would double it.
const configured = import.meta.env.VITE_API_BASE_URL

/**
 * Unset means same origin. It is spelled out as an absolute URL because `new Request()` rejects a
 * relative one outside a real browser (jsdom tests); in a browser the two behave the same.
 */
export const DEFAULT_BASE_URL: string = configured
  ? configured
  : typeof location === 'undefined'
    ? ''
    : location.origin
