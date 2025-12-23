import React from 'react'
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom'
import { ConfigProvider } from 'antd-mobile'
import zhCN from 'antd-mobile/es/locales/zh-CN'
import Login from './pages/Login'
import Register from './pages/Register'
import Home from './pages/Home'
import Upload from './pages/Upload'
import Gallery from './pages/Gallery'
import ImageDetail from './pages/ImageDetail'
import { AuthProvider, useAuth } from './context/AuthContext'
import './App.css'

function PrivateRoute({ children }) {
  const { isAuthenticated } = useAuth()
  return isAuthenticated ? children : <Navigate to="/login" />
}

function AppRoutes() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route path="/register" element={<Register />} />
      <Route path="/" element={<PrivateRoute><Home /></PrivateRoute>} />
      <Route path="/upload" element={<PrivateRoute><Upload /></PrivateRoute>} />
      <Route path="/gallery" element={<PrivateRoute><Gallery /></PrivateRoute>} />
      <Route path="/image/:id" element={<PrivateRoute><ImageDetail /></PrivateRoute>} />
    </Routes>
  )
}

function App() {
  return (
    <ConfigProvider locale={zhCN}>
      <AuthProvider>
        <Router>
          <AppRoutes />
        </Router>
      </AuthProvider>
    </ConfigProvider>
  )
}

export default App

