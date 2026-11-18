import { useTranslation } from 'react-i18next'
import { useId, type ReactNode } from 'react'
import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'

export interface ChartSeries {
  key: string
  label: string
  color: string
}

interface DailyChartProps {
  title: string
  /** Read by screen readers instead of the drawing. */
  summary: string
  description?: string
  kind: 'line' | 'bar'
  data: Record<string, number | string | null>[]
  series: ChartSeries[]
  formatDay: (day: string) => string
  formatValue?: (value: number) => string
  /** Upper bound of the y axis, e.g. 100 for percentages. */
  max?: number
}

const AXIS = { stroke: 'var(--muted-foreground)', fontSize: 12 }
const TOOLTIP = {
  contentStyle: {
    background: 'var(--surface)',
    border: '1px solid var(--border)',
    borderRadius: 12,
    color: 'var(--foreground)',
  },
}

export function DailyChart({
  title,
  summary,
  description,
  kind,
  data,
  series,
  formatDay,
  formatValue = String,
  max,
}: DailyChartProps) {
  const { t } = useTranslation('admin')
  const titleId = useId()
  const axes: ReactNode[] = [
    <CartesianGrid key="grid" stroke="var(--border)" vertical={false} />,
    <XAxis key="x" dataKey="day" tickFormatter={formatDay} {...AXIS} minTickGap={16} />,
    <YAxis
      key="y"
      allowDecimals={false}
      domain={[0, max ?? 'auto']}
      tickFormatter={formatValue}
      width={44}
      {...AXIS}
    />,
    <Tooltip
      key="tooltip"
      labelFormatter={(day) => (typeof day === 'string' ? formatDay(day) : '')}
      formatter={(value) => (typeof value === 'number' ? formatValue(value) : '–')}
      {...TOOLTIP}
    />,
    ...(series.length > 1 ? [<Legend key="legend" wrapperStyle={{ fontSize: 12 }} />] : []),
  ]
  return (
    <figure
      aria-labelledby={titleId}
      className="min-w-0 space-y-3 rounded-2xl border border-border bg-surface p-4"
    >
      <figcaption id={titleId} className="text-base font-semibold">
        {title}
      </figcaption>
      {description ? <p className="text-xs text-muted-foreground">{description}</p> : null}
      <p className="sr-only">{summary}</p>
      <div className="h-52" aria-hidden="true">
        <ResponsiveContainer width="100%" height="100%">
          {kind === 'bar' ? (
            <BarChart data={data}>
              {axes}
              {series.map((s) => (
                <Bar
                  key={s.key}
                  dataKey={s.key}
                  name={s.label}
                  fill={s.color}
                  radius={4}
                  isAnimationActive={false}
                />
              ))}
            </BarChart>
          ) : (
            <LineChart data={data}>
              {axes}
              {series.map((s) => (
                <Line
                  key={s.key}
                  type="linear"
                  dataKey={s.key}
                  name={s.label}
                  stroke={s.color}
                  strokeWidth={2}
                  dot={false}
                  connectNulls={false}
                  isAnimationActive={false}
                />
              ))}
            </LineChart>
          )}
        </ResponsiveContainer>
      </div>
      <details className="text-sm">
        <summary className="cursor-pointer text-muted-foreground">
          {t('dashboard.table.show')}
        </summary>
        <div className="relative mt-2 overflow-x-auto">
          <table className="w-full text-left text-xs">
            <caption className="sr-only">{t('dashboard.table.caption', { title })}</caption>
            <thead>
              <tr>
                <th scope="col" className="py-2 font-medium">
                  {t('dashboard.table.day')}
                </th>
                {series.map((s) => (
                  <th key={s.key} scope="col" className="p-2 text-right font-medium">
                    {s.label}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {data.map((row) => (
                <tr key={String(row.day)} className="border-t border-border">
                  <th scope="row" className="py-2 font-normal">
                    {row.day}
                  </th>
                  {series.map((s) => {
                    const value = row[s.key]
                    return (
                      <td key={s.key} className="p-2 text-right tabular-nums">
                        {typeof value === 'number'
                          ? formatValue(value)
                          : t('dashboard.notAvailable')}
                      </td>
                    )
                  })}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </details>
    </figure>
  )
}
