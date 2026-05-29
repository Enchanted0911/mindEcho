/**
 * 统一请求封装
 */
import {useUserStore} from '../store/user'

/** API 基础地址（开发环境 localhost；生产环境替换为实际域名） */
export const BASE_URL = 'http://localhost:8080/api'

interface RequestOptions {
  url: string
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE'
  data?: any
  header?: Record<string, string>
}

interface ApiResponse<T = any> {
  code: number
  message: string
  data: T
}

function getToken(): string {
  return uni.getStorageSync('token') || ''
}

export function request<T = any>(options: RequestOptions): Promise<T> {
  return new Promise((resolve, reject) => {
    const token = getToken()
    const header: Record<string, string> = {
      'Content-Type': 'application/json',
      ...options.header
    }

    if (token) {
      header['Authorization'] = `Bearer ${token}`
    }

    uni.request({
      url: BASE_URL + options.url,
      method: options.method || 'GET',
      data: options.data,
      header,
      success: (res) => {
        const response = res.data as ApiResponse<T>

        if (res.statusCode === 401) {
          // Token 过期，同时清除 Pinia store 内存状态和 storage，再跳转登录页
          // 注意：useUserStore() 在 Pinia 初始化完成后才可调用（uni-app 中页面加载后可用）
          try {
            const userStore = useUserStore()
            userStore.logout()
          } catch (_) {
            // Pinia 尚未初始化（极少数情况），降级手动清除 storage
            uni.removeStorageSync('token')
            uni.removeStorageSync('userInfo')
            uni.reLaunch({ url: '/pages/login/index' })
          }
          reject(new Error('未授权，请重新登录'))
          return
        }

        if (response.code === 0) {
          resolve(response.data)
        } else {
          uni.showToast({
            title: response.message || '请求失败',
            icon: 'none',
            duration: 2000
          })
          reject(new Error(response.message))
        }
      },
      fail: (err) => {
        uni.showToast({
          title: '网络请求失败',
          icon: 'none',
          duration: 2000
        })
        reject(err)
      }
    })
  })
}

export function get<T = any>(url: string, data?: any): Promise<T> {
  return request<T>({ url, method: 'GET', data })
}

export function post<T = any>(url: string, data?: any): Promise<T> {
  return request<T>({ url, method: 'POST', data })
}

export function put<T = any>(url: string, data?: any): Promise<T> {
  return request<T>({ url, method: 'PUT', data })
}

export function del<T = any>(url: string): Promise<T> {
  return request<T>({ url, method: 'DELETE' })
}

