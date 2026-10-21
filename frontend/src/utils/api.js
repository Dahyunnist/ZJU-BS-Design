import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  // 0 表示不启用 axios 超时限制，避免大图在网络较慢时触发 30s 超时
  timeout: 0,
  headers: {
    'Content-Type': 'application/json'
  }
})

// 请求拦截器
api.interceptors.request.use(
  config => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  error => {
    return Promise.reject(error)
  }
)

// 响应拦截器
api.interceptors.response.use(
  response => {
    return response
  },
  error => {
    // 401未授权或403禁止访问，都可能是token问题
    if (error.response?.status === 401 || error.response?.status === 403) {
      console.warn('认证失败，清除token并跳转到登录页', error.response?.status)
      localStorage.removeItem('token')
      // 避免重复跳转
      if (window.location.pathname !== '/login' && window.location.pathname !== '/register') {
        window.location.href = '/login'
      }
    }
    return Promise.reject(error)
  }
)

export default api

export async function fetchAuthenticatedImage(url) {
  const response = await api.get(url, { responseType: 'blob' })
  return URL.createObjectURL(response.data)
}

