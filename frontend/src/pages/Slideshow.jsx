import React, { useState, useEffect } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { NavBar, Button, Toast } from 'antd-mobile'
import ImageGallery from 'react-image-gallery'
import 'react-image-gallery/styles/css/image-gallery.css'
import api, { fetchAuthenticatedImage } from '../utils/api'
import './Slideshow.css'

function Slideshow() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const [images, setImages] = useState([])
  const [galleryItems, setGalleryItems] = useState([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    const ids = searchParams.get('ids')
    if (!ids) {
      Toast.show({ icon: 'fail', content: '缺少图片ID参数' })
      navigate(-1)
      return
    }

    let active = true
    const objectUrls = []
    loadImages(ids.split(',').map(id => parseInt(id.trim())), objectUrls, () => active)
    return () => {
      active = false
      objectUrls.forEach(url => URL.revokeObjectURL(url))
    }
  }, [searchParams])

  const loadImages = async (imageIds, objectUrls, isActive) => {
    try {
      const imagePromises = imageIds.map(id => 
        api.get(`/images/${id}`).then(res => res.data.data)
      )
      const loadedImages = await Promise.all(imagePromises)
      const items = await Promise.all(loadedImages.map(async img => {
        const [original, thumbnail] = await Promise.all([
          fetchAuthenticatedImage(`/api/images/${img.id}/file`),
          fetchAuthenticatedImage(`/api/images/${img.id}/thumbnail?size=300`)
        ])
        objectUrls.push(original, thumbnail)
        return { original, thumbnail, description: img.filename }
      }))
      if (!isActive()) {
        objectUrls.forEach(url => URL.revokeObjectURL(url))
        return
      }
      setImages(loadedImages)
      setGalleryItems(items)
    } catch (error) {
      Toast.show({ icon: 'fail', content: '加载图片失败' })
      navigate(-1)
    } finally {
      if (isActive()) setLoading(false)
    }
  }

  if (loading) {
    return <div className="slideshow-container">加载中...</div>
  }

  if (galleryItems.length === 0) {
    return <div className="slideshow-container">没有图片</div>
  }

  return (
    <div className="slideshow-container">
      <NavBar onBack={() => navigate(-1)}>图片轮播</NavBar>
      <div className="slideshow-content">
        <ImageGallery
          items={galleryItems}
          showPlayButton={true}
          showFullscreenButton={true}
          showThumbnails={true}
          autoPlay={true}
          slideInterval={3000}
          slideDuration={450}
        />
      </div>
    </div>
  )
}

export default Slideshow

