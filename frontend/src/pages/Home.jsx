import React from 'react'
import { useNavigate } from 'react-router-dom'
import { NavBar, Card, Button, Grid } from 'antd-mobile'
import { AddOutline, PictureOutline, SearchOutline } from 'antd-mobile-icons'
import { useAuth } from '../context/AuthContext'
import './Home.css'

function Home() {
  const navigate = useNavigate()
  const { user, logout } = useAuth()

  return (
    <div className="home-container">
      <NavBar
        back={null}
        right={
          <Button size="small" onClick={logout}>
            退出
          </Button>
        }
      >
        智能图片管理
      </NavBar>
      <div className="home-content">
        <div className="welcome-section">
          <h2>欢迎, {user?.username}!</h2>
          <p>管理您的图片收藏</p>
        </div>
        <Grid columns={2} gap={16}>
          <Grid.Item>
            <Card
              className="feature-card"
              onClick={() => navigate('/upload')}
            >
              <div className="feature-icon">
                <AddOutline fontSize={40} />
              </div>
              <h3>上传图片</h3>
              <p>上传新图片到您的收藏</p>
            </Card>
          </Grid.Item>
          <Grid.Item>
            <Card
              className="feature-card"
              onClick={() => navigate('/gallery')}
            >
              <div className="feature-icon">
                <PictureOutline fontSize={40} />
              </div>
              <h3>图片库</h3>
              <p>浏览和管理您的图片</p>
            </Card>
          </Grid.Item>
        </Grid>
      </div>
    </div>
  )
}

export default Home

