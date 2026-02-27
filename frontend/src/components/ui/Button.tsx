import { type ButtonHTMLAttributes } from 'react'
import { cn } from '../../lib/utils/cn'

type ButtonVariant = 'primary' | 'secondary' | 'ghost'

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant
}

const variantMap: Record<ButtonVariant, string> = {
  primary: 'bg-accent text-bg-base hover:bg-accent-strong',
  secondary: 'bg-bg-panel text-text-primary hover:bg-surface-border',
  ghost: 'bg-transparent text-text-secondary hover:bg-bg-panel',
}

export function Button({ className, variant = 'primary', type = 'button', ...props }: ButtonProps) {
  return (
    <button
      type={type}
      className={cn(
        'inline-flex items-center justify-center rounded-md px-4 py-2 text-sm font-medium transition focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-offset-2 focus-visible:ring-offset-bg-elevated disabled:cursor-not-allowed disabled:opacity-50',
        variantMap[variant],
        className,
      )}
      {...props}
    />
  )
}
