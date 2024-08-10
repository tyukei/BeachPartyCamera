package com.tyukei.beachpartycamera

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.chat.ImagePart
import com.aallam.openai.api.chat.ListContent
import com.aallam.openai.api.chat.TextPart
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream

class MainActivity : AppCompatActivity() {

    private val REQUEST_CODE_PICK_IMAGE = 1001
    private val REQUEST_CODE_CAPTURE_IMAGE = 1002
    private lateinit var apiKey: String
    private lateinit var imageView: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // APIキーを取得
        apiKey = BuildConfig.OPENAI_API_KEY

        imageView = findViewById(R.id.imageView)

        val uploadButton: Button = findViewById(R.id.uploadButton)
        val cameraButton: Button = findViewById(R.id.cameraButton)

        uploadButton.setOnClickListener {
            pickImage()
        }

        cameraButton.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQUEST_CODE_CAPTURE_IMAGE)
            } else {
                openCamera()
            }
        }
    }

    private fun pickImage() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        startActivityForResult(intent, REQUEST_CODE_PICK_IMAGE)
    }

    private fun openCamera() {
        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        startActivityForResult(cameraIntent, REQUEST_CODE_CAPTURE_IMAGE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_PICK_IMAGE && resultCode == RESULT_OK) {
            val uri: Uri? = data?.data
            uri?.let {
                val inputStream = contentResolver.openInputStream(it)
                val byteArray = inputStream?.readBytes()
                byteArray?.let {
                    val bitmap = BitmapFactory.decodeByteArray(it, 0, it.size)
                    imageView.setImageBitmap(bitmap)
                    val base64 = Base64.encodeToString(it, Base64.DEFAULT)
                    sendImageToOpenAI(base64)
                }
            }
        } else if (requestCode == REQUEST_CODE_CAPTURE_IMAGE && resultCode == RESULT_OK) {
            val photo: Bitmap = data?.extras?.get("data") as Bitmap
            imageView.setImageBitmap(photo)
            val byteArray = bitmapToByteArray(photo)
            val base64 = Base64.encodeToString(byteArray, Base64.DEFAULT)
            sendImageToOpenAI(base64)
        }
    }

    private fun bitmapToByteArray(bitmap: Bitmap): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
        return stream.toByteArray()
    }

    private fun sendImageToOpenAI(base64: String) {
        val openAI = OpenAI(token = apiKey)

        val request = ChatCompletionRequest(
            model = ModelId("gpt-4-vision-preview"),
            messages = listOf(
                ChatMessage(
                    role = ChatRole.User,
                    messageContent = ListContent(
                        listOf(
                            TextPart(
                                text = "画像にあるテキストをまとめて、得られる知見、事実を簡潔に説明してください。"
                            ),
                            ImagePart(
                                imageUrl = ImagePart.ImageURL(url = "data:image/jpeg;base64,$base64")
                            )
                        )
                    )
                )
            ),
        )

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = openAI.chatCompletion(request)
                val content = result.choices.firstOrNull()?.message?.content
                withContext(Dispatchers.Main) {
                    findViewById<TextView>(R.id.resultTextView).text = content ?: "結果がありません"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    findViewById<TextView>(R.id.resultTextView).text = "エラーが発生しました: ${e.message}"
                }
            }
        }
    }
}