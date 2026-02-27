export interface Monitor {
  id: string
  name: string
  target: string
  enabled: boolean
  intervalSeconds: number
  createdAt: string
}
