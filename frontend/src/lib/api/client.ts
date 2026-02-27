import axios from 'axios'
import { appConfig } from '../config/app'

export const apiClient = axios.create({
  baseURL: appConfig.apiBaseUrl,
  timeout: 10_000,
  headers: {
    'Content-Type': 'application/json',
  },
})
