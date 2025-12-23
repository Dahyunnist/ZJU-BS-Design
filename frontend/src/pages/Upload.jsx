import React, { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { NavBar, Button, Toast, Input, Tag } from 'antd-mobile'
import { useAuth } from '../context/AuthContext'
import api from '../utils/api'
import './Upload.css'

function Upload() {
  const navigate = useNavigate()
  const { user } = useAuth()
  const [file, setFile] = useState(null)
  const [tags, setTags] = useState([])
  const [tagInput, setTagInput] = useState('')
  const [uploading, setUploading] = useState(false)
  const [preview, setPreview] = useState(null)

  const handleFileChange = (e) => {
    const selectedFile = e.target.files[0]
    if (selectedFile) {
      if (!selectedFile.type.startsWith('image/')) {
        Toast.show({ icon: 'fail', content: '请选择图片文件' })
        return
      }
      if (selectedFile.size > 20 * 1024 * 1024) {
        Toast.show({ icon: 'fail', content: '文件大小不能超过20MB' })
        return
      }
      setFile(selectedFile)
      const reader = new FileReader()
      reader.onloadend = () => {
        setPreview(reader.result)
      }
      reader.readAsDataURL(selectedFile)
    }
  }

  const addTag = () => {
    if (tagInput.trim() && !tags.includes(tagInput.trim())) {
      setTags([...tags, tagInput.trim()])
      setTagInput('')
    }
  }

  const removeTag = (tag) => {
    setTags(tags.filter(t => t !== tag))
  }

  const handleUpload = async () => {
    if (!file) {
      Toast.show({ icon: 'fail', content: '请选择要上传的图片' })
      return
    }

    setUploading(true)
    try {
      const formData = new FormData()
      formData.append('file', file)
      if (tags.length > 0) {
        tags.forEach(tag => {
          formData.append('tags', tag)
        })
      }

      await api.post('/images/upload', formData, {
        headers: {
          'Content-Type': 'multipart/form-data'
        }
      })

      Toast.show({ icon: 'success', content: '上传成功' })
      navigate('/gallery')
    } catch (error) {
      const msg =
        error?.response?.data?.message ||
        error?.message ||
        '上传失败'
      Toast.show({ icon: 'fail', content: msg })
    } finally {
      setUploading(false)
    }
  }

  return (
    <div className="upload-container">
      <NavBar onBack={() => navigate(-1)}>上传图片</NavBar>
      <div className="upload-content">
        <div className="upload-section">
          <div className="file-upload-area">
            <input
              type="file"
              accept="image/*"
              onChange={handleFileChange}
              id="file-input"
              style={{ display: 'none' }}
            />
            <label htmlFor="file-input" className="upload-label">
              {preview ? (
                <img src={preview} alt="预览" className="preview-image" />
              ) : (
                <div className="upload-placeholder">
                  <p>点击选择图片</p>
                  <p className="upload-hint">支持 JPG、PNG、GIF 等格式，最大 20MB</p>
                </div>
              )}
            </label>
          </div>
        </div>

        <div className="tags-section">
          <h3>添加标签</h3>
          <div className="tag-input-group">
            <Input
              value={tagInput}
              onChange={setTagInput}
              placeholder="输入标签后按回车"
              onKeyPress={(e) => {
                if (e.key === 'Enter') {
                  addTag()
                }
              }}
            />
            <Button size="small" onClick={addTag}>添加</Button>
          </div>
          <div className="tags-list">
            {tags.map((tag, index) => (
              <Tag
                key={index}
                closeable
                onClose={() => removeTag(tag)}
                style={{ marginRight: 8, marginTop: 8 }}
              >
                {tag}
              </Tag>
            ))}
          </div>
        </div>

        <Button
          block
          color="primary"
          size="large"
          loading={uploading}
          onClick={handleUpload}
          style={{ marginTop: 20 }}
        >
          上传图片
        </Button>
      </div>
    </div>
  )
}

export default Upload

