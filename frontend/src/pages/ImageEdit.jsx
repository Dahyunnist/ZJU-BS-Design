import React, { useState, useEffect, useRef } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { NavBar, Button, Toast, Slider, Tabs, Input, Tag, Space } from 'antd-mobile'
import ReactCrop from 'react-image-crop'
import 'react-image-crop/dist/ReactCrop.css'
import api, { fetchAuthenticatedImage } from '../utils/api'
import './ImageEdit.css'

function ImageEdit() {
  const navigate = useNavigate()
  const { id } = useParams()
  const [image, setImage] = useState(null)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [activeTab, setActiveTab] = useState('crop')
  const [tags, setTags] = useState([])
  const [tagInput, setTagInput] = useState('')
  
  // 裁剪相关
  const [crop, setCrop] = useState({ unit: '%', width: 90, aspect: 1 })
  const [completedCrop, setCompletedCrop] = useState(null)
  const imgRef = useRef(null)
  const canvasRef = useRef(null)
  const objectUrls = useRef(new Set())
  
  // 色调调整相关
  const [brightness, setBrightness] = useState(0)
  const [contrast, setContrast] = useState(0)
  const [saturation, setSaturation] = useState(0)
  const [previewUrl, setPreviewUrl] = useState(null)

  useEffect(() => {
    loadImage()
    return () => objectUrls.current.forEach(url => URL.revokeObjectURL(url))
  }, [id])

  const loadImage = async () => {
    try {
      const response = await api.get(`/images/${id}`)
      setImage(response.data.data)
      const url = await fetchAuthenticatedImage(`/api/images/${id}/file`)
      objectUrls.current.add(url)
      setPreviewUrl(url)
      // 初始化标签
      if (response.data.data.customTags) {
        setTags([...response.data.data.customTags])
      } else {
        setTags([])
      }
    } catch (error) {
      Toast.show({ icon: 'fail', content: '加载失败' })
      navigate(-1)
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

  const onImageLoaded = (img) => {
    // ReactCrop的onImageLoaded回调会传递图片元素
    // 但有时也会传递事件对象，需要处理两种情况
    if (img) {
      if (img instanceof HTMLImageElement) {
        imgRef.current = img
      } else if (img.target && img.target instanceof HTMLImageElement) {
        imgRef.current = img.target
      }
    }
    return false
  }

  const onCropComplete = (crop) => {
    setCompletedCrop(crop)
  }


  const handleCropSave = async () => {
    if (!completedCrop) {
      Toast.show({ icon: 'fail', content: '请先选择裁剪区域' })
      return
    }

    // 获取实际的图片元素
    const imageElement = imgRef.current
    if (!imageElement || !(imageElement instanceof HTMLImageElement)) {
      Toast.show({ icon: 'fail', content: '图片未加载完成，请稍候再试' })
      return
    }

    if (!imageElement.complete || imageElement.naturalWidth === 0) {
      Toast.show({ icon: 'fail', content: '图片未完全加载，请等待加载完成' })
      return
    }

    setSaving(true)
    try {
      // 计算实际像素坐标
      const scaleX = imageElement.naturalWidth / imageElement.width
      const scaleY = imageElement.naturalHeight / imageElement.height
      
      const cropParams = {
        type: 'CROP',
        crop: {
          x: Math.round(completedCrop.x * scaleX),
          y: Math.round(completedCrop.y * scaleY),
          width: Math.round(completedCrop.width * scaleX),
          height: Math.round(completedCrop.height * scaleY)
        },
        tags: tags.length > 0 ? tags : null
      }

      const response = await api.post(`/images/${id}/edit`, cropParams)
      Toast.show({ icon: 'success', content: '裁剪成功' })
      navigate(`/image/${response.data.data.id}`)
    } catch (error) {
      console.error('裁剪保存失败:', error)
      Toast.show({ icon: 'fail', content: '裁剪失败: ' + (error.response?.data?.message || error.message) })
    } finally {
      setSaving(false)
    }
  }

  const handleAdjustSave = async () => {
    setSaving(true)
    try {
      const adjustParams = {
        type: 'ADJUST',
        adjust: {
          brightness,
          contrast,
          saturation
        },
        tags: tags.length > 0 ? tags : null
      }

      const response = await api.post(`/images/${id}/edit`, adjustParams)
      Toast.show({ icon: 'success', content: '调整成功' })
      navigate(`/image/${response.data.data.id}`)
    } catch (error) {
      Toast.show({ icon: 'fail', content: '调整失败: ' + (error.response?.data?.message || error.message) })
    } finally {
      setSaving(false)
    }
  }

  const updatePreview = () => {
    const imgElement = imgRef.current
    if (!imgElement || !(imgElement instanceof HTMLImageElement)) {
      return
    }
    
    if (!imgElement.complete || imgElement.naturalWidth === 0) {
      return
    }
    
    try {
      const canvas = document.createElement('canvas')
      const ctx = canvas.getContext('2d')
      if (!ctx) {
        console.error('无法获取canvas context')
        return
      }
      
      canvas.width = imgElement.naturalWidth
      canvas.height = imgElement.naturalHeight
      
      ctx.filter = `brightness(${100 + brightness}%) contrast(${100 + contrast}%) saturate(${100 + saturation}%)`
      ctx.drawImage(imgElement, 0, 0)
      
      canvas.toBlob((blob) => {
        if (blob) {
          const url = URL.createObjectURL(blob)
          objectUrls.current.add(url)
          setPreviewUrl(url)
        }
      }, 'image/jpeg', 0.95)
    } catch (error) {
      console.error('预览更新失败:', error)
    }
  }

  useEffect(() => {
    if (activeTab === 'adjust' && imgRef.current) {
      updatePreview()
    }
  }, [brightness, contrast, saturation, activeTab])

  if (loading) {
    return <div className="image-edit-container">加载中...</div>
  }

  if (!image) {
    return <div className="image-edit-container">图片不存在</div>
  }

  return (
    <div className="image-edit-container">
      <NavBar onBack={() => navigate(-1)}>图片编辑</NavBar>
      
      <Tabs activeKey={activeTab} onChange={setActiveTab}>
        <Tabs.Tab title="裁剪" key="crop">
          <div className="edit-content">
            <div className="crop-container">
              {previewUrl && (
                <ReactCrop
                  crop={crop}
                  onChange={(c) => setCrop(c)}
                  onComplete={onCropComplete}
                  aspect={1}
                >
                  <img
                    ref={imgRef}
                    src={previewUrl}
                    alt="编辑"
                    onLoad={(e) => {
                      if (e && e.target) {
                        imgRef.current = e.target
                        onImageLoaded(e.target)
                      }
                    }}
                    style={{ maxWidth: '100%', maxHeight: '60vh' }}
                  />
                </ReactCrop>
              )}
              <canvas
                ref={canvasRef}
                style={{ display: 'none' }}
              />
            </div>
            <div style={{ marginTop: 20 }}>
              <div style={{ marginBottom: 16, background: 'white', padding: 16, borderRadius: 8 }}>
                <h4 style={{ marginBottom: 8, fontSize: 16 }}>添加标签（可选）</h4>
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
                <Space wrap>
                  {tags.map((tag, idx) => (
                    <Tag
                      key={idx}
                      color="primary"
                      closeable
                      onClose={() => removeTag(tag)}
                    >
                      {tag}
                    </Tag>
                  ))}
                </Space>
              </div>
              <Button
                block
                color="primary"
                size="large"
                loading={saving}
                onClick={handleCropSave}
              >
                保存裁剪
              </Button>
            </div>
          </div>
        </Tabs.Tab>
        
        <Tabs.Tab title="色调调整" key="adjust">
          <div className="edit-content">
            <div className="preview-container">
              {previewUrl && (
                <img
                  ref={imgRef}
                  src={previewUrl}
                  alt="预览"
                  onLoad={(e) => {
                    if (e && e.target) {
                      imgRef.current = e.target
                    }
                  }}
                  style={{ maxWidth: '100%', maxHeight: '60vh' }}
                />
              )}
            </div>
            
            <div className="adjust-controls">
              <div className="control-item">
                <label>亮度: {brightness}</label>
                <Slider
                  min={-100}
                  max={100}
                  value={brightness}
                  onChange={setBrightness}
                />
              </div>
              
              <div className="control-item">
                <label>对比度: {contrast}</label>
                <Slider
                  min={-100}
                  max={100}
                  value={contrast}
                  onChange={setContrast}
                />
              </div>
              
              <div className="control-item">
                <label>饱和度: {saturation}</label>
                <Slider
                  min={-100}
                  max={100}
                  value={saturation}
                  onChange={setSaturation}
                />
              </div>
            </div>
            
            <div style={{ marginTop: 20 }}>
              <div style={{ marginBottom: 16, background: 'white', padding: 16, borderRadius: 8 }}>
                <h4 style={{ marginBottom: 8 }}>添加标签（可选）</h4>
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
                <Space wrap>
                  {tags.map((tag, idx) => (
                    <Tag
                      key={idx}
                      color="primary"
                      closeable
                      onClose={() => removeTag(tag)}
                    >
                      {tag}
                    </Tag>
                  ))}
                </Space>
              </div>
              <Button
                block
                color="primary"
                size="large"
                loading={saving}
                onClick={handleAdjustSave}
              >
                保存调整
              </Button>
            </div>
          </div>
        </Tabs.Tab>
      </Tabs>
    </div>
  )
}

export default ImageEdit

