import { type InputHTMLAttributes, type ReactNode, type SelectHTMLAttributes, type TextareaHTMLAttributes } from 'react'
import { cn } from '../../lib/utils/cn'

interface FieldProps {
  label: string
  children: ReactNode
}

export function Field({ label, children }: FieldProps) {
  return (
    <label className="grid gap-2 text-sm text-text-secondary">
      <span>{label}</span>
      {children}
    </label>
  )
}

export function TextInput(props: InputHTMLAttributes<HTMLInputElement>) {
  return (
    <input
      className={cn(
        'w-full rounded-md border border-surface-border bg-bg-panel px-3 py-2 text-sm text-text-primary outline-none transition focus:border-accent focus-visible:ring-2 focus-visible:ring-accent/70',
      )}
      {...props}
    />
  )
}

export function SelectInput(props: SelectHTMLAttributes<HTMLSelectElement>) {
  return (
    <select
      className={cn(
        'w-full rounded-md border border-surface-border bg-bg-panel px-3 py-2 text-sm text-text-primary outline-none transition focus:border-accent focus-visible:ring-2 focus-visible:ring-accent/70',
      )}
      {...props}
    />
  )
}

export function TextAreaInput(props: TextareaHTMLAttributes<HTMLTextAreaElement>) {
  return (
    <textarea
      className={cn(
        'w-full rounded-md border border-surface-border bg-bg-panel px-3 py-2 text-sm text-text-primary outline-none transition focus:border-accent focus-visible:ring-2 focus-visible:ring-accent/70',
      )}
      {...props}
    />
  )
}
