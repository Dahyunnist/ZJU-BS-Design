import React, { useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { Form, Input, Button, Toast } from 'antd-mobile'
import { useAuth } from '../context/AuthContext'
import './Login.css'

function Login() {
  const navigate = useNavigate()
  const { login } = useAuth()
  const [loading, setLoading] = useState(false)

  const onFinish = async (values) => {
    setLoading(true)
    try {
      await login(values.username, values.password)
      Toast.show({ icon: 'success', content: '登录成功' })
      navigate('/')
    } catch (error) {
      const msg =
        error?.response?.data?.message ||
        error?.message ||
        '登录失败'
      Toast.show({ icon: 'fail', content: msg })
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="login-container">
      <div className="login-box">
        <h1>智能图片管理</h1>
        <Form onFinish={onFinish} layout="vertical">
          <Form.Item
            name="username"
            label="用户名"
            rules={[{ required: true, message: '请输入用户名' }]}
          >
            <Input placeholder="请输入用户名" />
          </Form.Item>
          <Form.Item
            name="password"
            label="密码"
            rules={[{ required: true, message: '请输入密码' }]}
          >
            <Input type="password" placeholder="请输入密码" />
          </Form.Item>
          <Form.Item>
            <Button
              block
              type="submit"
              color="primary"
              size="large"
              loading={loading}
            >
              登录
            </Button>
          </Form.Item>
        </Form>
        <div className="login-footer">
          <Link to="/register">还没有账号？立即注册</Link>
        </div>
      </div>
    </div>
  )
}

export default Login

