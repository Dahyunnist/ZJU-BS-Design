import React, { useState, useEffect } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { NavBar, Image, Button, Toast, Tag, Card, Space } from 'antd-mobile'
import { DeleteOutline } from 'antd-mobile-icons'
import api from '../utils/api'
import './ImageDetail.css'

function ImageDetail() {
  const navigate = useNavigate()
  const { id } = useParams()
  const [image, setImage] = useState(null)
  const [loading, setLoading] = useState(true)
  const [deleting, setDeleting] = useState(false)

  useEffect(() => {
    loadImage()
  }, [id])

  const loadImage = async () => {
    try {
      const response = await api.get(`/images/${id}`)
      setImage(response.data.data)
    } catch (error) {
      Toast.show({ icon: 'fail', content: '加载失败' })
      navigate('/gallery')
    } finally {
      setLoading(false)
    }
  }

  const handleDelete = async () => {
    if (!window.confirm('确定要删除这张图片吗？')) {
      return
    }

    setDeleting(true)
    try {
      await api.delete(`/images/${id}`)
      Toast.show({ icon: 'success', content: '删除成功' })
      navigate('/gallery')
    } catch (error) {
      Toast.show({ icon: 'fail', content: '删除失败' })
    } finally {
      setDeleting(false)
    }
  }

  if (loading) {
    return <div className="image-detail-container">加载中...</div>
  }

  if (!image) {
    return <div className="image-detail-container">图片不存在</div>
  }

  return (
    <div className="image-detail-container">
      <NavBar
        onBack={() => navigate(-1)}
        right={
          <Button
            size="small"
            color="danger"
            onClick={handleDelete}
            loading={deleting}
          >
            <DeleteOutline /> 删除
          </Button>
        }
      >
        图片详情
      </NavBar>
      <div className="image-detail-content">
        <Card>
          <Image
            src={`/api/images/${id}/file`}
            alt={image.filename}
            fit="contain"
            style={{ width: '100%', height: 'auto', maxHeight: '80vh', objectFit: 'contain', background: '#f7f7f7' }}
          />
        </Card>
        <Card title="图片信息" style={{ marginTop: 16 }}>
          <div className="image-info-item">
            <strong>文件名：</strong>
            <span>{image.filename}</span>
          </div>
          {image.tags && image.tags.length > 0 && (
            <div className="image-info-item">
              <strong>标签：</strong>
              <Space wrap>
                {image.tags.map((tag, idx) => (
                  <Tag key={idx} color="primary">
                    {tag}
                  </Tag>
                ))}
              </Space>
            </div>
          )}
          {image.width && image.height && (
            <div className="image-info-item">
              <strong>尺寸：</strong>
              <span>{image.width} × {image.height}</span>
            </div>
          )}
          {image.fileSize && (
            <div className="image-info-item">
              <strong>大小：</strong>
              <span>{(image.fileSize / 1024 / 1024).toFixed(2)} MB</span>
            </div>
          )}
          {image.uploadTime && (
            <div className="image-info-item">
              <strong>上传时间：</strong>
              <span>{new Date(image.uploadTime).toLocaleString()}</span>
            </div>
          )}
        </Card>
        {image.exifData && Object.keys(image.exifData).length > 0 && (
          <Card title="EXIF信息" style={{ marginTop: 16 }}>
            {Object.entries(image.exifData).map(([key, value]) => (
              <div key={key} className="image-info-item">
                <strong>{key}：</strong>
                <span>{String(value)}</span>
              </div>
            ))}
          </Card>
        )}
      </div>
    </div>
  )
}

export default ImageDetail

