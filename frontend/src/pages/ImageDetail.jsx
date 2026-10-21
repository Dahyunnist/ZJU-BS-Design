import React, { useState, useEffect } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { NavBar, Button, Toast, Tag, Card, Space, Input } from 'antd-mobile'
import { DeleteOutline, EditSOutline } from 'antd-mobile-icons'
import api from '../utils/api'
import AuthenticatedImage from '../components/AuthenticatedImage'
import './ImageDetail.css'

function ImageDetail() {
  const navigate = useNavigate()
  const { id } = useParams()
  const [image, setImage] = useState(null)
  const [loading, setLoading] = useState(true)
  const [deleting, setDeleting] = useState(false)
  const [editingTags, setEditingTags] = useState(false)
  const [tags, setTags] = useState([])
  const [tagInput, setTagInput] = useState('')
  const [savingTags, setSavingTags] = useState(false)

  useEffect(() => {
    loadImage()
  }, [id])

  const loadImage = async () => {
    try {
      const response = await api.get(`/images/${id}`)
      setImage(response.data.data)
      // 初始化标签（只显示自定义标签）
      if (response.data.data.customTags) {
        setTags([...response.data.data.customTags])
      } else {
        setTags([])
      }
    } catch (error) {
      Toast.show({ icon: 'fail', content: '加载失败' })
      navigate('/gallery')
    } finally {
      setLoading(false)
    }
  }

  const addTag = () => {
    const trimmed = tagInput.trim()
    if (trimmed && !tags.includes(trimmed)) {
      setTags([...tags, trimmed])
      setTagInput('')
    }
  }

  const removeTag = (tag) => {
    setTags(tags.filter(t => t !== tag))
  }

  const handleSaveTags = async () => {
    setSavingTags(true)
    try {
      await api.put(`/images/${id}/tags`, { tags })
      Toast.show({ icon: 'success', content: '标签保存成功' })
      setEditingTags(false)
      loadImage() // 重新加载图片信息
    } catch (error) {
      Toast.show({ icon: 'fail', content: '标签保存失败: ' + (error.response?.data?.message || error.message) })
    } finally {
      setSavingTags(false)
    }
  }

  const handleCancelEditTags = () => {
    // 恢复原始标签
    if (image && image.customTags) {
      setTags([...image.customTags])
    } else {
      setTags([])
    }
    setEditingTags(false)
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
          <Space>
            <Button
              size="large"
              color="primary"
              onClick={() => navigate(`/image/${id}/edit`)}
              style={{ fontSize: '16px', fontWeight: 600 }}
            >
              <EditSOutline /> 编辑
            </Button>
            <Button
              size="large"
              color="danger"
              onClick={handleDelete}
              loading={deleting}
              style={{ fontSize: '16px', fontWeight: 600 }}
            >
              <DeleteOutline /> 删除
            </Button>
          </Space>
        }
      >
        图片详情
      </NavBar>
      <div className="image-detail-content">
        <div className="image-detail-layout">
          <div className="image-info-section">
            <Card title="图片信息">
              <div className="image-info-item">
                <strong>文件名：</strong>
                <span>{image.filename}</span>
              </div>
              <div className="image-info-item">
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
                  <strong>标签：</strong>
              {!editingTags && (
                <Button 
                  size="middle" 
                  onClick={() => setEditingTags(true)}
                  color="primary"
                  style={{ fontSize: '14px', fontWeight: 600, color: 'white' }}
                >
                  编辑
                </Button>
              )}
                </div>
                {editingTags ? (
                  <div>
                    <div style={{ display: 'flex', gap: 8, marginBottom: 8 }}>
                      <Input
                        value={tagInput}
                        onChange={setTagInput}
                        placeholder="输入标签后按回车"
                        onKeyPress={(e) => {
                          if (e.key === 'Enter') {
                            addTag()
                          }
                        }}
                        style={{ flex: 1 }}
                      />
                      <Button 
                        size="middle" 
                        onClick={addTag}
                        color="primary"
                        style={{ fontSize: '14px', fontWeight: 600, minWidth: '80px' }}
                      >
                        添加
                      </Button>
                    </div>
                    <Space wrap style={{ marginBottom: 8 }}>
                      {tags.map((tag, idx) => (
                        <Tag
                          key={idx}
                          color="primary"
                          style={{ color: 'white', position: 'relative', paddingRight: 24 }}
                        >
                          {tag}
                          <span
                            onClick={(e) => {
                              e.stopPropagation()
                              removeTag(tag)
                            }}
                            style={{
                              position: 'absolute',
                              right: 0,
                              top: '50%',
                              transform: 'translateY(-50%)',
                              cursor: 'pointer',
                              fontSize: '16px',
                              lineHeight: 1,
                              padding: '0 4px',
                              fontWeight: 'bold'
                            }}
                          >
                            ×
                          </span>
                        </Tag>
                      ))}
                    </Space>
                    <Space>
                      <Button 
                        size="middle" 
                        color="primary" 
                        onClick={handleSaveTags} 
                        loading={savingTags}
                        style={{ fontSize: '14px', fontWeight: 600 }}
                      >
                        保存
                      </Button>
                      <Button 
                        size="middle" 
                        onClick={handleCancelEditTags}
                        style={{ fontSize: '14px', fontWeight: 600 }}
                      >
                        取消
                      </Button>
                    </Space>
                  </div>
                ) : (
                  <Space wrap>
                    {image.tags && image.tags.length > 0 ? (
                      image.tags.map((tag, idx) => (
                        <Tag 
                          key={idx} 
                          color="primary"
                          style={{ color: 'white' }}
                        >
                          {tag}
                        </Tag>
                      ))
                    ) : (
                      <span style={{ color: '#999' }}>暂无标签</span>
                    )}
                  </Space>
                )}
              </div>
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
          <div className="image-display-section">
            <Card>
              <div className="image-wrapper">
                <AuthenticatedImage
                  src={`/api/images/${id}/file`}
                  alt={image.filename}
                  className="detail-image-content"
                />
              </div>
            </Card>
          </div>
        </div>
      </div>
    </div>
  )
}

export default ImageDetail

