package com.example.farmvisionapp

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.ViewGroup
import android.webkit.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    WebViewScreen(activity = this)
                }
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun WebViewScreen(activity: Activity) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var progress by remember { mutableStateOf(0) }
    var filePathCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    var showWebView by remember { mutableStateOf(false) }
    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }

    // Define permissions
    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO
        )
    } else {
        listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
    }

    val permissionsState = rememberMultiplePermissionsState(permissions)

    // File picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val results: Array<Uri>? = result.data?.let { intent ->
                intent.clipData?.let { clipData ->
                    Array(clipData.itemCount) { i -> clipData.getItemAt(i).uri }
                } ?: intent.data?.let { arrayOf(it) }
            }
            filePathCallback?.onReceiveValue(results)
        } else {
            filePathCallback?.onReceiveValue(null)
        }
        filePathCallback = null
    }

    // Request permissions
    LaunchedEffect(Unit) {
        if (!permissionsState.allPermissionsGranted) {
            permissionsState.launchMultiplePermissionRequest()
        } else {
            showWebView = true
        }
    }

    LaunchedEffect(permissionsState.allPermissionsGranted) {
        if (permissionsState.allPermissionsGranted) {
            showWebView = true
        }
    }

    BackHandler(enabled = webView?.canGoBack() == true) {
        webView?.goBack()
    }

    if (!showWebView) {
        // Permission info screen
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                Text("🌾", fontSize = 60.sp)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Farm Vision App",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "This app needs the following permissions:",
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(12.dp))
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    PermissionItem("📷 Camera", "Take photos and scan crops")
                    PermissionItem("🎤 Microphone", "Voice assistant feature")
                    PermissionItem("📍 Location", "Track farm locations")
                    PermissionItem("🖼 Storage", "Upload and save images")
                }
                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = { permissionsState.launchMultiplePermissionRequest() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp)
                        .height(50.dp)
                ) {
                    Text("Grant All Permissions", fontSize = 16.sp)
                }
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            if (progress in 1..99) {
                LinearProgressIndicator(
                    progress = progress / 100f,
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF4CAF50)
                )
            }

            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )

                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            allowFileAccess = true
                            allowContentAccess = true
                            builtInZoomControls = true
                            displayZoomControls = false
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            mediaPlaybackRequiresUserGesture = false
                            javaScriptCanOpenWindowsAutomatically = true
                            setGeolocationEnabled(true)
                        }

                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                val url = request?.url.toString()
                                view?.loadUrl(url)
                                return true
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                progress = 0
                            }
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                progress = newProgress
                            }

                            override fun onGeolocationPermissionsShowPrompt(
                                origin: String?,
                                callback: GeolocationPermissions.Callback?
                            ) {
                                callback?.invoke(origin, true, false)
                            }

                            override fun onPermissionRequest(request: PermissionRequest?) {
                                request?.let { permRequest ->
                                    val grantedResources = mutableListOf<String>()
                                    permRequest.resources.forEach { resource ->
                                        when (resource) {
                                            PermissionRequest.RESOURCE_AUDIO_CAPTURE -> {
                                                if (ContextCompat.checkSelfPermission(
                                                        context,
                                                        Manifest.permission.RECORD_AUDIO
                                                    ) == PackageManager.PERMISSION_GRANTED
                                                ) grantedResources.add(resource)
                                            }
                                            PermissionRequest.RESOURCE_VIDEO_CAPTURE -> {
                                                if (ContextCompat.checkSelfPermission(
                                                        context,
                                                        Manifest.permission.CAMERA
                                                    ) == PackageManager.PERMISSION_GRANTED
                                                ) grantedResources.add(resource)
                                            }
                                        }
                                    }

                                    if (grantedResources.isNotEmpty()) {
                                        activity.runOnUiThread {
                                            permRequest.grant(grantedResources.toTypedArray())
                                        }
                                    } else {
                                        activity.runOnUiThread { permRequest.deny() }
                                    }
                                }
                            }

                            override fun onShowFileChooser(
                                webView: WebView?,
                                filePathCallbackParam: ValueCallback<Array<Uri>>?,
                                fileChooserParams: FileChooserParams?
                            ): Boolean {
                                filePathCallback?.onReceiveValue(null)
                                filePathCallback = filePathCallbackParam

                                try {
                                    // Create file for camera image
                                    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                                    val imageFileName = "FARM_${timeStamp}.jpg"
                                    val storageDir = context.getExternalFilesDir(null)
                                    val imageFile = File(storageDir, imageFileName)

                                    cameraImageUri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.provider",
                                        imageFile
                                    )

                                    // Camera intent
                                    val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                                        putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                                    }

                                    // Gallery intent
                                    val galleryIntent = Intent(Intent.ACTION_PICK).apply {
                                        type = "image/*"
                                        putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "image/jpeg", "image/png"))
                                    }

                                    // File picker intent
                                    val contentIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
                                        type = "image/*"
                                        addCategory(Intent.CATEGORY_OPENABLE)
                                    }

                                    // Create chooser
                                    val chooserIntent = Intent.createChooser(contentIntent, "Select or Capture Image").apply {
                                        putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(
                                            takePictureIntent,
                                            galleryIntent
                                        ))
                                    }

                                    filePickerLauncher.launch(chooserIntent)
                                    return true
                                } catch (e: Exception) {
                                    Log.e("WebView", "File chooser error", e)
                                    filePathCallback?.onReceiveValue(null)
                                    filePathCallback = null
                                    cameraImageUri = null
                                    return false
                                }
                            }
                        }

                        loadUrl("https://crop-speak-farm-76771-18828.lovable.app")
                        webView = this
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun PermissionItem(title: String, description: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(0.4f)
        )
        Text(
            text = description,
            fontSize = 14.sp,
            color = Color.Gray,
            modifier = Modifier.weight(0.6f)
        )
    }
}