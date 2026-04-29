package io.emqx.mqtt

import android.util.LruCache
import java.io.File
import java.security.MessageDigest

/**
 * ⭐ Edge-TTS音频缓存管理器
 * 采用LFU（最不常用）策略，仅对Edge-TTS生效
 * 磁盘缓存最大50MB，超出时自动清理最不常用的文件
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
    
    // ⭐ 访问频率统计：记录每个缓存文件的访问次数
    private val accessCountMap = mutableMapOf<String, Int>()
    
    // 磁盘缓存目录
    private val diskCacheDir = File(cacheDir, CACHE_SUBDIR).apply {
        if (!exists()) mkdirs()
    }
    
    init {
        // ⭐ 启动时加载已有的访问频率统计（从文件名推断，初始为0）
        android.util.Log.d(TAG, "Edge-TTS audio cache initialized (max ${maxDiskSizeMB}MB, LFU strategy)")
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
                // ⭐ 增加访问计数
                accessCountMap[key] = (accessCountMap[key] ?: 0) + 1
                android.util.Log.d(TAG, "Memory cache hit: $text (access count: ${accessCountMap[key]})")
                return cachedFile
            }
        }
        
        // 2. 再查磁盘缓存
        val diskFile = File(diskCacheDir, "$key.mp3")
        if (diskFile.exists()) {
            // ⭐ 增加访问计数
            accessCountMap[key] = (accessCountMap[key] ?: 0) + 1
            android.util.Log.d(TAG, "Disk cache hit: $text (${diskFile.length()} bytes, access count: ${accessCountMap[key]})")
            
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
     * 强制清理超出限制的磁盘缓存（⭐ 改为LFU策略：删除最不常用的文件）
     */
    private fun enforceDiskCacheLimit() {
        val maxSizeBytes = maxDiskSizeMB * 1024 * 1024
        val currentSize = diskCacheDir.listFiles()?.sumOf { it.length() } ?: 0
        
        if (currentSize > maxSizeBytes) {
            android.util.Log.w(TAG, "Cache size ${currentSize / 1024 / 1024}MB exceeds limit, cleaning up...")
            
            // ⭐ 按访问频率排序，删除最不常用的文件（访问次数少的优先删除）
            val files = diskCacheDir.listFiles() ?: emptyArray()
            val filesWithAccessCount = files.map { file ->
                val key = file.nameWithoutExtension
                val accessCount = accessCountMap[key] ?: 0
                Pair(file, accessCount)
            }.sortedBy { it.second }  // 按访问次数升序排序
            
            var freedSize = 0L
            
            for ((file, accessCount) in filesWithAccessCount) {
                if (currentSize - freedSize <= maxSizeBytes * 0.8) break  // 清理到80%
                
                android.util.Log.d(TAG, "Deleting LFU cache: ${file.name} (access count: $accessCount)")
                file.delete()
                freedSize += file.length()
                
                // 同时从内存缓存和访问统计移除
                val key = file.nameWithoutExtension
                memoryCache.remove(key)
                accessCountMap.remove(key)
            }
            
            android.util.Log.d(TAG, "Freed ${freedSize / 1024}KB from cache (deleted ${filesWithAccessCount.takeWhile { (currentSize - freedSize) > maxSizeBytes * 0.8 }.size} files)")
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
    
    /**
     * ⭐ 新增：缓存文件信息（用于管理界面）
     */
    data class CachedFileInfo(
        val fileName: String,       // 文件名（如 "a1b2c3d4.mp3"）
        val fileSize: Long,         // 文件大小（字节）
        val lastModified: Long      // 最后修改时间
    )
    
    /**
     * ⭐ 新增：获取所有缓存文件列表（用于管理界面）
     * @return 缓存文件列表，按最后修改时间降序排列
     */
    fun getAllCachedFiles(): List<CachedFileInfo> {
        val diskFiles = diskCacheDir.listFiles() ?: emptyArray()
        
        return diskFiles.map { file ->
            CachedFileInfo(
                fileName = file.name,
                fileSize = file.length(),
                lastModified = file.lastModified()
            )
        }.sortedByDescending { it.lastModified }  // 按时间降序
    }
    
    /**
     * ⭐ 新增：删除指定的缓存文件
     * @param fileName 文件名（如 "a1b2c3d4.mp3"）
     * @return true=删除成功
     */
    fun deleteCachedFile(fileName: String): Boolean {
        val file = File(diskCacheDir, fileName)
        if (file.exists()) {
            val deleted = file.delete()
            if (deleted) {
                // 同时从内存缓存移除
                val key = fileName.substringBeforeLast(".")
                memoryCache.remove(key)
                accessCountMap.remove(key)
                android.util.Log.d(TAG, "Deleted cache file: $fileName")
            }
            return deleted
        }
        return false
    }
}
