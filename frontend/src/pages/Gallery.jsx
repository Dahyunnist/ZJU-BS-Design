import React, { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { NavBar, Grid, Image, InfiniteScroll, SearchBar, Toast, Space, Tag, Button, Checkbox } from 'antd-mobile'
import api from '../utils/api'
import './Gallery.css'

function Gallery() {
  const navigate = useNavigate()
  const [images, setImages] = useState([])
  const [loading, setLoading] = useState(false)
  const [hasMore, setHasMore] = useState(true)
  const [page, setPage] = useState(0)
  const [keyword, setKeyword] = useState('')
  const [selectedIds, setSelectedIds] = useState(new Set())
  const [selectMode, setSelectMode] = useState(false)

  const loadImages = async (reset = false) => {
    if (loading) return
    
    setLoading(true)
    try {
      const currentPage = reset ? 0 : page
      const response = await api.get('/images', {
        params: {
          page: currentPage,
          size: 20
        }
      })
      
      const newImages = response.data.data.content || []
      
      if (reset) {
        setImages(newImages)
      } else {
        setImages([...images, ...newImages])
      }
      
      setHasMore(!response.data.data.last)
      setPage(currentPage + 1)
    } catch (error) {
      console.error('加载图片失败:', error)
      const errorMsg = error.response?.data?.message || error.message || '加载失败'
      Toast.show({ icon: 'fail', content: `加载失败: ${errorMsg}` })
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadImages(true)
  }, [])

  const handleSearch = async (value) => {
    const kw = value.trim()
    setKeyword(value)

    // 关键字为空时，恢复为完整列表并允许继续下拉加载
    if (!kw) {
      setPage(0)
      setHasMore(true)
      loadImages(true)
      return
    }

    // 为了按标签搜索，这里在前端做一次过滤：
    // 1）从后端取较大一页的数据
    // 2）在前端按文件名和标签过滤
    try {
      const response = await api.get('/images', {
        params: {
          page: 0,
          size: 200  // 一次取足够多的图片，方便在前端筛选
        }
      })

      const all = response.data.data.content || []
      const lower = kw.toLowerCase()

      const filtered = all.filter(img => {
        const nameMatch =
          img.filename &&
          String(img.filename).toLowerCase().includes(lower)

        const tagList = img.tags || img.customTags || []
        const tagMatch =
          Array.isArray(tagList) &&
          tagList.some(t =>
            t && String(t).toLowerCase().includes(lower)
          )

        return nameMatch || tagMatch
      })

      setImages(filtered)
      // 搜索结果不再使用无限滚动，防止继续加载又把全部列表加回来
      setHasMore(false)
      setPage(1)
    } catch (error) {
      Toast.show({ icon: 'fail', content: '搜索失败' })
    }
  }

  const toggleSelect = (imageId) => {
    const newSelected = new Set(selectedIds)
    if (newSelected.has(imageId)) {
      newSelected.delete(imageId)
    } else {
      newSelected.add(imageId)
    }
    setSelectedIds(newSelected)
  }

  const handleStartSlideshow = () => {
    if (selectedIds.size === 0) {
      Toast.show({ icon: 'fail', content: '请至少选择一张图片' })
      return
    }
    const ids = Array.from(selectedIds).join(',')
    navigate(`/slideshow?ids=${ids}`)
  }

  return (
    <div className="gallery-container">
      <NavBar 
        onBack={() => navigate(-1)}
        right={
          selectMode ? (
            <Space>
              <Button size="small" onClick={() => {
                setSelectMode(false)
                setSelectedIds(new Set())
              }}>
                取消
              </Button>
              <Button 
                size="small" 
                color="primary"
                onClick={handleStartSlideshow}
                disabled={selectedIds.size === 0}
              >
                轮播({selectedIds.size})
              </Button>
            </Space>
          ) : (
            <Button size="small" onClick={() => setSelectMode(true)}>
              选择
            </Button>
          )
        }
      >
        图片库
      </NavBar>
      <div className="gallery-content">
        <SearchBar
          placeholder="搜索图片..."
          value={keyword}
          onChange={setKeyword}
          onSearch={handleSearch}
          style={{ marginBottom: 16 }}
        />
        <Grid columns={2} gap={8}>
          {images.map((image) => (
            <Grid.Item key={image.id}>
              <div
                className={`image-item ${selectMode ? 'select-mode' : ''} ${selectedIds.has(image.id) ? 'selected' : ''}`}
                onClick={() => {
                  if (selectMode) {
                    toggleSelect(image.id)
                  } else {
                    navigate(`/image/${image.id}`)
                  }
                }}
              >
                {selectMode && (
                  <Checkbox
                    checked={selectedIds.has(image.id)}
                    onChange={() => toggleSelect(image.id)}
                    style={{ position: 'absolute', top: 8, left: 8, zIndex: 10 }}
                  />
                )}
                <Image
                  src={`/api/images/${image.id}/thumbnail?size=600`}
                  alt={image.filename}
                  fit="contain"
                  style={{ width: '100%', height: 220, objectFit: 'contain', background: '#f7f7f7' }}
                  lazy
                />
                <div className="image-info">
                  <p className="image-filename">{image.filename}</p>
                  {image.customTags && image.customTags.length > 0 && (
                    <Space wrap>
                      {image.customTags.slice(0, 3).map((tag, idx) => (
                        <Tag key={idx} color="primary" fill="outline" style={{ '--border-radius': '6px' }}>
                          {tag}
                        </Tag>
                      ))}
                    </Space>
                  )}
                </div>
              </div>
            </Grid.Item>
          ))}
        </Grid>
        <InfiniteScroll
          loadMore={loadImages}
          hasMore={hasMore}
          threshold={10}
        />
      </div>
    </div>
  )
}

export default Gallery

