package io.emqx.mqtt

import android.app.job.JobParameters
import android.app.job.JobService
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * ⭐ P0-2修复：JobService用于Android 12+后台启动MqttService
 * 
 * 作用：
 * 1. 满足Android 12+前台服务启动豁免条件
 * 2. 系统调度执行，避免ForegroundServiceStartNotAllowedException
 * 3. 在JobService中启动前台服务是合法的
 */
class MqttJobService : JobService() {
    
    override fun onStartJob(params: JobParameters?): Boolean {
        Log.d("MqttJobService", "Job started, launching MqttService")
        
        try {
            // 在JobService中启动前台服务（满足Android 12+豁免条件）
            val serviceIntent = Intent(this, MqttService::class.java).apply {
                action = MqttService.ACTION_START
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
                Log.d("MqttJobService", "MqttService started via startForegroundService")
            } else {
                startService(serviceIntent)
                Log.d("MqttJobService", "MqttService started via startService")
            }
            
            // 返回false表示任务已完成（异步启动Service）
            return false
            
        } catch (e: Exception) {
            Log.e("MqttJobService", "Failed to start MqttService: ${e.message}", e)
            return false
        }
    }
    
    override fun onStopJob(params: JobParameters?): Boolean {
        Log.d("MqttJobService", "Job stopped")
        // 返回false表示不需要重新调度
        return false
    }
}
