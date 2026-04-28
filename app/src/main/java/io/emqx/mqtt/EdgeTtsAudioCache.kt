package io.emqx.mqtt

import android.util.LruCache
import java.io.File
import java.security.MessageDigest

/**
 * ⭐ Edge-TTS音频缓存管理器
 * 采用LRU（最近最少使用）策略，仅对Edge-TTS生效
 * 磁盘缓存最大50MB，超出时自动清理最旧的文件
 */
class EdgeTtsAudioCache(
    private val cacheDir: File,
    private val maxDiskSizeMB: Int = 50  // 磁盘缓存最大50MB
) {
    companion object {
        private const val TAG = "EdgeTtsAudioCache"
        private const val CACHE_SUBDIR = "edge_tts_cache"
    }
    
    // 内存缓存：快速访问最近使用的音频（最多20个）
    private val memoryCache = LruCache<String, File>(20)
    
    // 磁盘缓存目录
    private val diskCacheDir = File(cacheDir, CACHE_SUBDIR).apply {
        if (!exists()) mkdirs()
    }
    
    init {
        // 启动时清理过期缓存
        cleanupOldCache()
        android.util.Log.d(TAG, "Edge-TTS audio cache initialized (max ${maxDiskSizeMB}MB)")
    }
    
    /**
     * 生成文本的唯一缓存键（使用MD5哈希）
     */
    private fun generateCacheKey(text: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(text.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
    
    /**
     * 获取缓存的音频文件
     * @param text 原始文本
     * @return 缓存文件，如果不存在返回null
     */
    fun getCachedAudio(text: String): File? {
        val key = generateCacheKey(text)
        
        // 1. 先查内存缓存（最快）
        memoryCache.get(key)?.let { cachedFile ->
            if (cachedFile.exists()) {
                android.util.Log.d(TAG, "Memory cache hit: $text")
                return cachedFile
            }
        }
        
        // 2. 再查磁盘缓存
        val diskFile = File(diskCacheDir, "$key.mp3")
        if (diskFile.exists()) {
            android.util.Log.d(TAG, "Disk cache hit: $text (${diskFile.length()} bytes)")
            
            // 加入内存缓存（加速下次访问）
            memoryCache.put(key, diskFile)
            
            return diskFile
        }
        
        android.util.Log.d(TAG, "Cache miss: $text")
        return null
    }
    
    /**
     * 保存音频到缓存
     * @param text 原始文本
     * @param audioFile 下载的音频文件
     */
    fun cacheAudio(text: String, audioFile: File) {
        val key = generateCacheKey(text)
        val cachedFile = File(diskCacheDir, "$key.mp3")
        
        try {
            // 复制到磁盘缓存
            audioFile.copyTo(cachedFile, overwrite = true)
            
            // 加入内存缓存
            memoryCache.put(key, cachedFile)
            
            android.util.Log.d(TAG, "Cached audio: $text (${cachedFile.length()} bytes)")
            
            // 检查磁盘缓存大小，超出限制则清理
            enforceDiskCacheLimit()
            
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to cache audio: ${e.message}", e)
        }
    }
    
    /**
     * 强制清理超出限制的磁盘缓存
     */
    private fun enforceDiskCacheLimit() {
        val maxSizeBytes = maxDiskSizeMB * 1024 * 1024
        val currentSize = diskCacheDir.listFiles()?.sumOf { it.length() } ?: 0
        
        if (currentSize > maxSizeBytes) {
            android.util.Log.w(TAG, "Cache size ${currentSize / 1024 / 1024}MB exceeds limit, cleaning up...")
            
            // 按最后修改时间排序，删除最旧的文件
            val files = diskCacheDir.listFiles()?.sortedBy { it.lastModified() } ?: emptyList()
            var freedSize = 0L
            
            for (file in files) {
                if (currentSize - freedSize <= maxSizeBytes * 0.8) break  // 清理到80%
                
                file.delete()
                freedSize += file.length()
                
                // 同时从内存缓存移除
                val key = file.nameWithoutExtension
                memoryCache.remove(key)
            }
            
            android.util.Log.d(TAG, "Freed ${freedSize / 1024}KB from cache")
        }
    }
    
    /**
     * 清理超过7天的旧缓存
     */
    private fun cleanupOldCache() {
        val sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000)
        val files = diskCacheDir.listFiles() ?: return
        
        var deletedCount = 0
        for (file in files) {
            if (file.lastModified() < sevenDaysAgo) {
                file.delete()
                memoryCache.remove(file.nameWithoutExtension)
                deletedCount++
            }
        }
        
        if (deletedCount > 0) {
            android.util.Log.d(TAG, "Cleaned up $deletedCount old cache files")
        }
    }
    
    /**
     * 清空所有缓存
     */
    fun clearCache() {
        memoryCache.evictAll()
        diskCacheDir.listFiles()?.forEach { it.delete() }
        android.util.Log.d(TAG, "Cache cleared")
    }
    
    /**
     * 获取缓存统计信息
     */
    fun getCacheStats(): CacheStats {
        val diskFiles = diskCacheDir.listFiles() ?: emptyArray()
        val diskSize = diskFiles.sumOf { it.length() }
        val memorySize = memoryCache.size()
        
        return CacheStats(
            memoryCount = memorySize,
            diskCount = diskFiles.size,
            diskSizeBytes = diskSize,
            diskSizeMB = diskSize / 1024.0 / 1024.0
        )
    }
    
    data class CacheStats(
        val memoryCount: Int,
        val diskCount: Int,
        val diskSizeBytes: Long,
        val diskSizeMB: Double
    )
}
