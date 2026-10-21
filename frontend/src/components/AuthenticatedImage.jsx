import React, { useEffect, useState } from 'react'
import { fetchAuthenticatedImage } from '../utils/api'

function AuthenticatedImage({ src, alt, ...props }) {
  const [objectUrl, setObjectUrl] = useState(null)

  useEffect(() => {
    let active = true
    let loadedUrl
    fetchAuthenticatedImage(src)
      .then(url => {
        loadedUrl = url
        if (active) setObjectUrl(url)
        else URL.revokeObjectURL(url)
      })
      .catch(error => console.error('图片加载失败:', error))
    return () => {
      active = false
      if (loadedUrl) URL.revokeObjectURL(loadedUrl)
    }
  }, [src])

  return <img src={objectUrl || undefined} alt={alt} loading="lazy" {...props} />
}

export default AuthenticatedImage
