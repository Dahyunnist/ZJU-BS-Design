import React, { useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { Form, Input, Button, Toast } from 'antd-mobile'
import { useAuth } from '../context/AuthContext'
import './Register.css'

function Register() {
  const navigate = useNavigate()
  const { register } = useAuth()
  const [loading, setLoading] = useState(false)

  const onFinish = async (values) => {
    if (values.password !== values.confirmPassword) {
      Toast.show({ icon: 'fail', content: '两次输入的密码不一致' })
      return
    }

    setLoading(true)
    try {
      await register(values.username, values.password, values.email)
      Toast.show({ icon: 'success', content: '注册成功' })
      navigate('/')
    } catch (error) {
      const msg =
        error?.response?.data?.message ||
        error?.message ||
        '注册失败'
      Toast.show({ icon: 'fail', content: msg })
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="register-container">
      <div className="register-box">
        <h1>用户注册</h1>
        <Form onFinish={onFinish} layout="vertical">
          <Form.Item
            name="username"
            label="用户名"
            rules={[
              { required: true, message: '请输入用户名' },
              { min: 6, message: '用户名至少6个字符' }
            ]}
          >
            <Input placeholder="请输入用户名（至少6个字符）" />
          </Form.Item>
          <Form.Item
            name="email"
            label="邮箱"
            rules={[
              { required: true, message: '请输入邮箱' },
              { type: 'email', message: '邮箱格式不正确' }
            ]}
          >
            <Input type="email" placeholder="请输入邮箱" />
          </Form.Item>
          <Form.Item
            name="password"
            label="密码"
            rules={[
              { required: true, message: '请输入密码' },
              { min: 6, message: '密码至少6个字符' }
            ]}
          >
            <Input type="password" placeholder="请输入密码（至少6个字符）" />
          </Form.Item>
          <Form.Item
            name="confirmPassword"
            label="确认密码"
            rules={[{ required: true, message: '请确认密码' }]}
          >
            <Input type="password" placeholder="请再次输入密码" />
          </Form.Item>
          <Form.Item>
            <Button
              block
              type="submit"
              color="primary"
              size="large"
              loading={loading}
            >
              注册
            </Button>
          </Form.Item>
        </Form>
        <div className="register-footer">
          <Link to="/login">已有账号？立即登录</Link>
        </div>
      </div>
    </div>
  )
}

export default Register

