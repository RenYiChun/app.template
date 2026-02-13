import axios from 'axios'
import { ElMessage } from 'element-plus'
import type { AxiosInstance, AxiosError, AxiosResponse, InternalAxiosRequestConfig } from 'axios'

// 创建 axios 实例
const request: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 10000,
  withCredentials: true, // 携带 Cookie，供验证码 Session 与登录态使用
  headers: {
    'Content-Type': 'application/json'
  }
})

// 请求拦截器
request.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    // 可以在这里添加 token
    // const token = localStorage.getItem('token')
    // if (token) {
    //   config.headers.Authorization = `Bearer ${token}`
    // }
    return config
  },
  (error: AxiosError) => {
    console.error('Request error:', error)
    return Promise.reject(error)
  }
)

// 响应拦截器
request.interceptors.response.use(
  (response: AxiosResponse) => {
    return response
  },
  (error: AxiosError) => {
    let message = '请求失败'
    const data = (error.response?.data as { message?: string } | undefined)
    const serverMessage = data?.message

    if (error.response) {
      const status = error.response.status
      if (serverMessage) {
        message = serverMessage
      } else {
        switch (status) {
          case 400:
            message = '请求参数错误'
            break
          case 401:
            message = '未授权，请重新登录'
            break
          case 403:
            message = '拒绝访问'
            break
          case 404:
            message = '请求的资源不存在'
            break
          case 500:
            message = '服务器内部错误'
            break
          default:
            message = `请求失败: ${status}`
        }
      }
    } else if (error.request) {
      message = '网络错误，请检查连接'
    }

    ElMessage.error(message)
    return Promise.reject(error)
  }
)

export default request
