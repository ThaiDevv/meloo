import { useLocation } from 'react-router-dom'

/** Test stand-in for a screen the pages lead to: says which route it got and whether state came along. */
export function Where({ label }: { label: string }) {
  const location = useLocation()
  const carried: unknown = location.state
  return (
    <p data-testid="where">
      {label}:{location.pathname}:{carried === null || carried === undefined ? 'bare' : 'state'}
    </p>
  )
}
