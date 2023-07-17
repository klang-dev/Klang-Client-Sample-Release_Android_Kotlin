package com.laonda.kckitsamplekt

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.laonda.kckit.KCConfig
import com.laonda.kckit.KCSessionManager
import com.laonda.kckit.KCType
import com.laonda.kckit.KCUser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private val TAG = "MainActivity"

    private val TEST_APP_ID = "Meet3"
    private val TEST_SECRET_KEY = "Meet3_secret"
    private val TEST_ROOM_ID = "shawn"

    private var isPublish = true
    private var is1stPublish = false
    private var isSpeaker = false

    private val config: KCConfig = KCConfig.getInstance()

    private val kcSessionManager: KCSessionManager = KCSessionManager.getInstance()

    private val renders: MutableMap<String, View> = HashMap()

    private val listener: KCSessionManager.KCManagerListener = object : KCSessionManager.KCManagerListener {
        override fun onAddStream(
            manager: KCSessionManager,
            roomId: String?,
            userId: String,
            mediaType: Int
        ) {
            println("onAddStream")
            CoroutineScope(Dispatchers.Main).launch {
                if (mediaType > KCType.KCMediaType.KC_AUDIO) {
                    val view = manager.getRender(TEST_ROOM_ID, userId) as View
                    renders[userId] = view
                    alignRender()
                }
            }
        }

        override fun onRemoveStream(manager: KCSessionManager?, roomId: String?, userId: String?) {
            println("didLeaveRoom")
            userId?.let { _userId ->
                CoroutineScope(Dispatchers.Main).launch {
                    removeRender(_userId)
                }
            }
        }

        override fun didOccurError(manager: KCSessionManager?, errorCode: Int, errorMessage: String) {
            println("didOccurError$errorCode $errorMessage")
            if (errorCode == KCType.KCError.KC_ERROR_NEED_REFRESH_PUB) {
                CoroutineScope(Dispatchers.Main).launch {
                    val mainLayout = findViewById(R.id.scrollLinear) as LinearLayout
                    mainLayout.removeView(renders[config.user])
                    renders.remove(config.user)
                    alignRender()
                    kcSessionManager.stopPreview(TEST_ROOM_ID)
                    kcSessionManager.unpublish(TEST_ROOM_ID, config.user)
                    isPublish = false
                }
            }
        }

        override fun didRecvRoomInfo(manager: KCSessionManager, roomId: String, users: ArrayList<KCUser?>) {
            CoroutineScope(Dispatchers.Main).launch {
                val arPresenter = ArrayList<String>()
                users.forEach { user ->
                    user?.let { _user ->
                        Log.d(TAG, "didRecvRoomInfo] room:" + roomId + "user:" + _user.userId + "userType:" + _user.userType + "stMic:" + _user.stMic + "stCam:" + _user.stCam + "stScreen:" + _user.stScreen + "stData:" + _user.stData)
                        if (_user.userId.equals(config.user) && user.userType === KCType.KCUserType.KC_USER_VIEWER) {
                            if (is1stPublish == false) {
                                Log.d(TAG, "sendMedia============================")
                                manager.publish(TEST_ROOM_ID, KCType.KCMediaType.KC_AUDIO_VIDEO)
                                Log.d(TAG, "startPreview============================")
                                manager.startPreview(TEST_ROOM_ID)
                                is1stPublish = true
                            }
                        }
                        if (user.userType === KCType.KCUserType.KC_USER_PRESENTER && !_user.userId.equals(
                                config.user
                            )
                        ) {
                            arPresenter.add(_user.userId)
                        }
                    }
                }

                if (arPresenter.size > 0) {
                    manager.subscribeUsers(TEST_ROOM_ID, arPresenter)
                }
            }
        }

        override fun didChangeAudioLevel(manager: KCSessionManager?, userId: String?, audioLevel: Double) {
//            System.out.println("didChangeAudioLevel:"+userId+" "+audioLevel);
        }

        override fun didChangeVideoSize(
            manager: KCSessionManager?,
            userId: String,
            width: Int,
            height: Int
        ) {
            println("didChangeVideoSize:$userId $width $height")
            CoroutineScope(Dispatchers.Main).launch {
                alignRender()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        checkPermission()

        config.countryCode = applicationContext.resources.configuration.locale.country
        config.appID = "Meet3"
        config.apiToken = "klang"
        config.sessionNode = "wss://ss.klang.network/ws"
        config.user = "shawnAndroid"
        config.context = applicationContext
        config.captureWidth = 640
        config.captureHeight = 480
        config.captureFps = 15
        config.isFrontCamera = true

        config.enableHWCodec = true
        config.enableAEC = true
        config.enableAGC = true
        config.enableNS = true
        config.enableHPF = true
        config.maxBitrate = 500

        kcSessionManager.initWithConfig(config, listener)

        val onClickListener =
            View.OnClickListener { view ->
                when (view.id) {
                    R.id.btnJoin -> kcSessionManager.joinRoomByToken(
                        config.apiToken,
                        TEST_ROOM_ID,
                        config.user,
                        KCType.KCMediaType.KC_AUDIO_VIDEO
                    )
                    R.id.btnLeave -> {
                        kcSessionManager.stopPreview(TEST_ROOM_ID)
                        kcSessionManager.leaveRoom(TEST_ROOM_ID)
                        is1stPublish = false
                        clearRender()
                    }
                    R.id.btnUnPub -> if (isPublish == true) {
                        val mainLayout = findViewById(R.id.scrollLinear) as LinearLayout
                        mainLayout.removeView(renders[config.user])
                        renders.remove(config.user)
                        alignRender()
                        kcSessionManager.stopPreview(TEST_ROOM_ID)
                        kcSessionManager.unpublish(TEST_ROOM_ID, config.user)
                        isPublish = false
                    } else {
                        kcSessionManager.publish(TEST_ROOM_ID, KCType.KCMediaType.KC_AUDIO_VIDEO)
                        isPublish = true
                        val localrender =
                            kcSessionManager.getRender(TEST_ROOM_ID, config.user) as View
                        renders[config.user] = localrender
                        alignRender()
                        kcSessionManager.startPreview(TEST_ROOM_ID)
                    }
                    R.id.btnUnsubscribe -> {
                        val arPresenter = java.util.ArrayList<String>()
                        for (key in renders.keys) {
                            if (key != config.user) arPresenter.add(key)
                        }
                        kcSessionManager.unsubscribeUsers(TEST_ROOM_ID, arPresenter)
                    }
                    R.id.btnCamera -> kcSessionManager.switchCamera(TEST_ROOM_ID)
                    R.id.btnSpeaker -> isSpeaker = if (isSpeaker == false) {
                        kcSessionManager.enableSpeaker()
                        true
                    } else {
                        kcSessionManager.disableSpeaker()
                        false
                    }
                }
            }

        val btnJoin = findViewById(R.id.btnJoin) as Button
        btnJoin.setOnClickListener(onClickListener)
        val btnLeave = findViewById(R.id.btnLeave) as Button
        btnLeave.setOnClickListener(onClickListener)
        val btnUnsub = findViewById(R.id.btnUnsubscribe) as Button
        btnUnsub.setOnClickListener(onClickListener)
        val btnPub = findViewById(R.id.btnUnPub) as Button
        btnPub.setOnClickListener(onClickListener)
        val btnCamera = findViewById(R.id.btnCamera) as Button
        btnCamera.setOnClickListener(onClickListener)
        val btnSpeaker = findViewById(R.id.btnSpeaker) as Button
        btnSpeaker.setOnClickListener(onClickListener)
    }

    fun checkPermission() {
        val deniedPermissions = arrayListOf<String>()

        val permissions: Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA
        ) else arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.CAMERA
        )

        permissions.forEach {
            if (ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_DENIED) {
                deniedPermissions.add(it)
            }
        }

        if (deniedPermissions.size > 0) {
            ActivityCompat.requestPermissions(this, deniedPermissions.toArray(arrayOfNulls<String>(deniedPermissions.size)), 1)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        grantResults.forEach {
            if (it == PackageManager.PERMISSION_DENIED) {
                Toast.makeText(applicationContext, "필수 권한이 허용되지 않아 앱을 종료합니다.", Toast.LENGTH_SHORT).show()
                finish()
                return@forEach
            }
        }
    }

    private fun alignRender() {
        val mainLayout = findViewById(R.id.scrollLinear) as LinearLayout
        for (key in renders.keys) {
            renders[key]!!.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                1200
            )
            mainLayout.removeView(renders[key])
            mainLayout.addView(renders[key])
            if (key == config.user) {
            }
        }
    }

    private fun removeRender(userId: String) {
        val mainLayout = findViewById(R.id.scrollLinear) as LinearLayout
        mainLayout.removeView(renders[userId])
        renders.remove(userId)
        alignRender()
    }

    private fun clearRender() {
        val mainLayout = findViewById(R.id.scrollLinear) as LinearLayout
        for (key in renders.keys) {
            mainLayout.removeView(renders[key])
        }
        renders.clear()
    }
}