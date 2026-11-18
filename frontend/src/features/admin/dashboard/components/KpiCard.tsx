interface KpiCardProps {
  label: string
  value: string
  hint?: string
}

/** One number with its label; the value is the first thing read. */
export function KpiCard({ label, value, hint }: KpiCardProps) {
  return (
    <div className="space-y-1 rounded-2xl border border-border bg-surface p-4">
      <p className="text-sm text-muted-foreground">{label}</p>
      <p className="font-display text-2xl font-bold tabular-nums">{value}</p>
      {hint ? <p className="text-xs text-faint">{hint}</p> : null}
    </div>
  )
}
