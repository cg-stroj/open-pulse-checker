import { type ReactNode } from 'react'

interface Column<T> {
  key: keyof T
  header: string
  render?: (value: T[keyof T], row: T) => ReactNode
}

interface DataTableProps<T> {
  data: T[]
  columns: Column<T>[]
}

export function DataTable<T extends object>({ data, columns }: DataTableProps<T>) {
  return (
    <div className="overflow-hidden rounded-lg border border-surface-border bg-bg-elevated">
      <table className="min-w-full divide-y divide-surface-border text-sm">
        <thead className="bg-bg-panel text-left text-text-muted">
          <tr>
            {columns.map((column) => (
              <th key={String(column.key)} className="px-4 py-3 font-medium">
                {column.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-surface-border text-text-secondary">
          {data.map((row, index) => (
            <tr key={index} className="hover:bg-bg-panel/40">
              {columns.map((column) => {
                const value = row[column.key]
                return (
                  <td key={String(column.key)} className="px-4 py-3">
                    {column.render ? column.render(value, row) : String(value)}
                  </td>
                )
              })}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
