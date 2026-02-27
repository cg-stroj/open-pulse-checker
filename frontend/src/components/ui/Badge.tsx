import { type ReactNode } from 'react'
import { cn } from '../../lib/utils/cn'

type BadgeTone = 'neutral' | 'success' | 'warning' | 'critical'

interface BadgeProps {
  children: ReactNode
  tone?: BadgeTone
}

const toneMap: Record<BadgeTone, string> = {
  neutral: 'bg-bg-panel text-text-secondary',
  success: 'bg-emerald-500/20 text-emerald-300',
  warning: 'bg-amber-500/20 text-amber-300',
  critical: 'bg-red-500/20 text-red-300',
}

export function Badge({ children, tone = 'neutral' }: BadgeProps) {
  return <span className={cn('inline-flex rounded-full px-2 py-1 text-xs font-semibold', toneMap[tone])}>{children}</span>
}
