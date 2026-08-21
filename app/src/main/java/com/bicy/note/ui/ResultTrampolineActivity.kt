package com.bicy.note.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts

/**
 * 透明中转 Activity：悬浮窗里发起的「选图/拍照/拍视频/选音频/权限申请」等
 * ActivityResult 请求，经它转发给系统并接收结果，再把结果交回悬浮窗的注册表。
 * （悬浮窗是 Service 上下文，没有 Activity 结果机制，必须经过这个中转站。）
 */
class ResultTrampolineActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val requestCode = intent?.getIntExtra(QuickNoteService.EXTRA_REQUEST_CODE, 0) ?: 0
        val target = intent?.getParcelableExtra<Intent>(QuickNoteService.EXTRA_INTENT)
        if (target == null) {
            finish()
            return
        }
        Log.d(TAG, "onCreate: action=${target.action} code=$requestCode")
        if (ActivityResultContracts.RequestMultiplePermissions.ACTION_REQUEST_PERMISSIONS == target.action) {
            val permissions = target.getStringArrayExtra(
                ActivityResultContracts.RequestMultiplePermissions.EXTRA_PERMISSIONS
            )
            if (permissions != null) {
                Log.d(TAG, "权限请求: ${permissions.toList()}")
                requestPermissions(permissions, requestCode)
                return
            }
        }
        try {
            startActivityForResult(target, requestCode)
        } catch (_: ActivityNotFoundException) {
            Log.e(TAG, "无 Activity 处理 ${target.action}")
            QuickNoteService.dispatchResult(requestCode, Activity.RESULT_CANCELED, null)
            finish()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Log.d(TAG, "权限结果: code=$requestCode ${grantResults.toList()}")
        QuickNoteService.dispatchResult(
            requestCode,
            Activity.RESULT_OK,
            Intent().apply {
                putExtra(
                    ActivityResultContracts.RequestMultiplePermissions.EXTRA_PERMISSIONS,
                    permissions,
                )
                putExtra(
                    ActivityResultContracts.RequestMultiplePermissions.EXTRA_PERMISSION_GRANT_RESULTS,
                    grantResults,
                )
            },
        )
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.d(TAG, "onActivityResult: code=$requestCode result=$resultCode")
        QuickNoteService.dispatchResult(requestCode, resultCode, data)
        finish()
    }

    private companion object {
        const val TAG = "寄意中转"
    }
}