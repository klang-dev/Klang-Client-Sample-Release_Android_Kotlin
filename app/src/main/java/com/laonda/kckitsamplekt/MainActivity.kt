package com.laonda.kckitsamplekt

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.JsonReader
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
import com.laonda.kckit.KCSessionManager.KCManagerListener
import com.laonda.kckit.KCType
import com.laonda.kckit.KCUser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.InputStreamReader
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class MainActivity : AppCompatActivity() {
    private val TEST_ROOM_ID = "shawn"
    private var isPublish = false
    private var isJoined = false
    private var isSpeaker = false
    private val config = KCConfig.getInstance()
    private val kcSessionManager = KCSessionManager.getInstance()
    private val renders: MutableMap<String, View> = HashMap()
    private val listener: KCManagerListener = object : KCManagerListener {
        override fun onAddStream(
            manager: KCSessionManager,
            roomId: String,
            userId: String,
            mediaType: Int
        ) {
            println("onAddStream")
            runOnUiThread {
                if (mediaType > KCType.KCMediaType.KC_AUDIO) {
                    val view = manager.getRender(TEST_ROOM_ID, userId) as View
                    renders[userId] = view
                    alignRender()
                }
            }
        }

        override fun onRemoveStream(manager: KCSessionManager, roomId: String, userId: String) {
            println("didLeaveRoom")
            runOnUiThread { removeRender(userId) }
        }

        override fun didOccurError(
            manager: KCSessionManager,
            errorCode: Int,
            errorMessage: String
        ) {
            println("didOccurError$errorCode $errorMessage")
            if (errorCode == KCType.KCError.KC_ERROR_NEED_REFRESH_PUB) {
                runOnUiThread {
                    val mainLayout = findViewById<View>(R.id.scrollLinear) as LinearLayout
                    mainLayout.removeView(renders[config.user])
                    renders.remove(config.user)
                    alignRender()
                    kcSessionManager.stopPreview(TEST_ROOM_ID)
                    kcSessionManager.unpublish(TEST_ROOM_ID, config.user)
                    isPublish = false
                }
            }
        }

        override fun didRecvRoomInfo(
            manager: KCSessionManager,
            roomId: String,
            users: ArrayList<KCUser>
        ) {
            runOnUiThread {
                val arPresenter = ArrayList<String>()
                users.forEach { user ->
                    Log.d(TAG,  "didRecvRoomInfo] room:" + roomId + "user:" + user.userId + "userType:" + user.userType + "stMic:" + user.stMic + "stCam:" + user.stCam + "stScreen:" + user.stScreen + "stData:" + user.stData)

                    if (user.userId == config.user && user.userType == KCType.KCUserType.KC_USER_VIEWER) {
                        if (isJoined == false) {
                            isJoined = true
                        }
                    }
                    if (user.userType == KCType.KCUserType.KC_USER_PRESENTER && user.userId != config.user) {
                        arPresenter.add(user.userId)
                    }
                }

                if (arPresenter.size > 0) {
                    manager.subscribeUsers(TEST_ROOM_ID, arPresenter)
                }
            }
        }

        override fun didChangeAudioLevel(
            manager: KCSessionManager,
            userId: String,
            audioLevel: Double
        ) {
//            System.out.println("didChangeAudioLevel:"+userId+" "+audioLevel);
        }

        override fun didChangeVideoSize(
            manager: KCSessionManager,
            userId: String,
            width: Int,
            height: Int
        ) {
            println("didChangeVideoSize:$userId $width $height")
            runOnUiThread { alignRender() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setPermission()

        config.countryCode = applicationContext.resources.configuration.locale.country
        config.appID = "432e409445f9ab18a74d5c604c97addf"
        config.apiToken = "klang"
        config.sessionNode = "ws://3.37.135.207:7780/ws"
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

//        getServiceInfo();
        kcSessionManager.initWithConfig(config, listener)
        val onClickListener =
            View.OnClickListener { view ->
                when (view.id) {
                    R.id.btnJoin -> getToken()
                    R.id.btnLeave -> {
                        kcSessionManager.stopPreview(TEST_ROOM_ID)
                        kcSessionManager.leaveRoom(TEST_ROOM_ID)
                        isJoined = false
                        clearRender()
                    }
                    R.id.btnUnPub -> if (isPublish == true) {
                        val mainLayout = findViewById<View>(R.id.scrollLinear) as LinearLayout
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
                        kcSessionManager.resumeRecording(TEST_ROOM_ID, KCType.KCMediaType.KC_AUDIO)
                        kcSessionManager.resumeRecording(TEST_ROOM_ID, KCType.KCMediaType.KC_VIDEO)
                    }
                    R.id.btnUnsubscribe -> {
                        val arPresenter = ArrayList<String>()
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
        val btnJoin = findViewById<View>(R.id.btnJoin) as Button
        btnJoin.setOnClickListener(onClickListener)
        val btnLeave = findViewById<View>(R.id.btnLeave) as Button
        btnLeave.setOnClickListener(onClickListener)
        val btnUnsub = findViewById<View>(R.id.btnUnsubscribe) as Button
        btnUnsub.setOnClickListener(onClickListener)
        val btnPub = findViewById<View>(R.id.btnUnPub) as Button
        btnPub.setOnClickListener(onClickListener)
        val btnCamera = findViewById<View>(R.id.btnCamera) as Button
        btnCamera.setOnClickListener(onClickListener)
        val btnSpeaker = findViewById<View>(R.id.btnSpeaker) as Button
        btnSpeaker.setOnClickListener(onClickListener)
    }

    fun setPermission() {
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
        val mainLayout = findViewById<View>(R.id.scrollLinear) as LinearLayout
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
        val mainLayout = findViewById<View>(R.id.scrollLinear) as LinearLayout
        mainLayout.removeView(renders[userId])
        renders.remove(userId)
        alignRender()
    }

    private fun clearRender() {
        val mainLayout = findViewById<View>(R.id.scrollLinear) as LinearLayout
        for (key in renders.keys) {
            mainLayout.removeView(renders[key])
        }
        renders.clear()
    }

    private fun getToken() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val urlstr =
                    "https://jwt.klang.network:8008/rtc-token?room_id=" + TEST_ROOM_ID + "&user_id=" + config.user
                val url = URL(urlstr)
                val conn = url.openConnection() as HttpsURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Accept", "application/json")
                conn.doOutput = true
                conn.doInput = true

                //                    JSONObject json = new JSONObject();
                //                    json.put("app_id", TEST_APP_ID);
                //                    json.put("secret_key", TEST_SECRET_KEY);
                //                    json.put("room_id", TEST_ROOM_ID);
                //                    json.put("login_id", config.user);
                //
                //                    OutputStream outputStream;
                //                    outputStream = conn.getOutputStream();
                //                    outputStream.write(json.toString().getBytes());
                //                    outputStream.flush();

                // 실제 서버로 Request 요청 하는 부분 (응답 코드를 받음, 200은 성공, 나머지 에러)
                val response = conn.responseCode
                val responseMessage = conn.responseMessage
                val responseBody = conn.inputStream
                val responseBodyReader = InputStreamReader(responseBody, "UTF-8")
                val jsonReader = JsonReader(responseBodyReader)
                jsonReader.beginObject() // Start processing the JSON object
                while (jsonReader.hasNext()) { // Loop through all keys
                    val key = jsonReader.nextName() // Fetch the next key
                    if (key == "jwt") { // Check if desired key
                        config.apiToken = jsonReader.nextString()
                    } else {
                        jsonReader.skipValue() // Skip values of other keys
                    }
                }
                conn.disconnect()
                kcSessionManager.initWithConfig(config, listener)
                kcSessionManager.joinRoomByToken(
                    config.apiToken,
                    TEST_ROOM_ID,
                    config.user,
                    KCType.KCMediaType.KC_AUDIO_VIDEO
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}