package com.tyukei.beachpartycamera

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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

data class ImageResult(val image: Bitmap, val text: String)

class MainActivity : AppCompatActivity() {

    private lateinit var apiKey: String
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var imageView: ImageView
    private lateinit var resultTextView: TextView
    private lateinit var deleteButton: Button
    private val results = mutableListOf<ImageResult>()
    private lateinit var sidebarAdapter: SidebarAdapter

    private lateinit var db: AppDatabase
    private lateinit var imageResultDao: ImageResultDao

    private val REQUEST_CODE_PICK_IMAGE = 1001
    private val REQUEST_CODE_CAPTURE_IMAGE = 1002

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        drawerLayout = findViewById(R.id.drawerLayout)
        imageView = findViewById(R.id.imageView)
        resultTextView = findViewById(R.id.resultTextView)
        deleteButton = findViewById(R.id.deleteButton)
        apiKey = BuildConfig.OPENAI_API_KEY

        db = AppDatabase.getDatabase(this)
        imageResultDao = db.imageResultDao()

        loadSavedResults()

        sidebarAdapter = SidebarAdapter(results, ::restoreImageResult, ::deleteResult)
        val sidebarRecyclerView: RecyclerView = findViewById(R.id.sidebarRecyclerView)
        sidebarRecyclerView.layoutManager = LinearLayoutManager(this)
        sidebarRecyclerView.adapter = sidebarAdapter

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        val openSidebarButton: ImageButton = findViewById(R.id.openSidebarButton)
        val newPageButton: ImageButton = findViewById(R.id.newPageButton)
        val uploadButton: Button = findViewById(R.id.uploadButton)
        val cameraButton: Button = findViewById(R.id.cameraButton)

        deleteButton.visibility = View.GONE

        openSidebarButton.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        newPageButton.setOnClickListener {
            clearCurrentImageAndText()
            deleteButton.visibility = View.GONE
            uploadButton.visibility = View.VISIBLE
            cameraButton.visibility = View.VISIBLE
        }

        uploadButton.setOnClickListener {
            pickImage()
        }

        cameraButton.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.CAMERA), REQUEST_CODE_CAPTURE_IMAGE)
            } else {
                openCamera()
            }
        }

        deleteButton.setOnClickListener {
            val currentPosition = results.indexOfFirst { it.text == resultTextView.text.toString() }
            if (currentPosition != -1) {
                deleteResult(currentPosition)
                clearCurrentImageAndText()
                deleteButton.visibility = View.GONE
                uploadButton.visibility = View.VISIBLE
                cameraButton.visibility = View.VISIBLE
            }
        }
    }

    private fun loadSavedResults() {
        CoroutineScope(Dispatchers.IO).launch {
            val savedResults = imageResultDao.getAll()
            withContext(Dispatchers.Main) {
                for (result in savedResults) {
                    val bitmap = BitmapFactory.decodeByteArray(result.image, 0, result.image.size)
                    results.add(ImageResult(bitmap, result.text))
                }
                sidebarAdapter.notifyDataSetChanged()
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
        if (resultCode == RESULT_OK) {
            when (requestCode) {
                REQUEST_CODE_PICK_IMAGE -> {
                    val uri: Uri? = data?.data
                    uri?.let {
                        handleImageUpload(it)
                    }
                }
                REQUEST_CODE_CAPTURE_IMAGE -> {
                    val photo: Bitmap = data?.extras?.get("data") as Bitmap
                    imageView.setImageBitmap(photo)
                    val byteArray = bitmapToByteArray(photo)
                    val base64 = Base64.encodeToString(byteArray, Base64.DEFAULT)
                    sendImageToOpenAI(base64)
                }
            }
        }
    }

    private fun handleImageUpload(uri: Uri) {
        val inputStream = contentResolver.openInputStream(uri)
        val byteArray = inputStream?.readBytes()
        byteArray?.let {
            val bitmap = BitmapFactory.decodeByteArray(it, 0, it.size)
            imageView.setImageBitmap(bitmap)
            val base64 = Base64.encodeToString(it, Base64.DEFAULT)
            sendImageToOpenAI(base64)
        }
    }

    private fun bitmapToByteArray(bitmap: Bitmap): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
        return stream.toByteArray()
    }

    private fun saveResult(bitmap: Bitmap, result: String) {
        val byteArray = bitmapToByteArray(bitmap)
        val imageResultEntity = ImageResultEntity(image = byteArray, text = result)

        CoroutineScope(Dispatchers.IO).launch {
            imageResultDao.insert(imageResultEntity)
            withContext(Dispatchers.Main) {
                results.add(ImageResult(bitmap, result))
                sidebarAdapter.notifyDataSetChanged()
            }
        }
    }

    private fun deleteResult(position: Int) {
        val imageResult = results[position]
        val byteArray = bitmapToByteArray(imageResult.image)

        CoroutineScope(Dispatchers.IO).launch {
            val entityToDelete = imageResultDao.getAll().firstOrNull {
                it.text == imageResult.text && it.image.contentEquals(byteArray)
            }
            if (entityToDelete != null) {
                imageResultDao.delete(entityToDelete)
            }
            withContext(Dispatchers.Main) {
                results.removeAt(position)
                sidebarAdapter.notifyDataSetChanged()
            }
        }
    }

    // restoreImageResult 関数をここに追加
    private fun restoreImageResult(position: Int) {
        val imageResult = results[position]
        imageView.setImageBitmap(imageResult.image)
        resultTextView.text = imageResult.text
        drawerLayout.closeDrawer(GravityCompat.START)

        findViewById<Button>(R.id.uploadButton).visibility = View.GONE
        findViewById<Button>(R.id.cameraButton).visibility = View.GONE
        deleteButton.visibility = View.VISIBLE
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
                                text = "画像に点数をつけてください。その後理由を一言(30文字以内)。例:90\n服とズボンが合っている。\nA:"
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
                resultTextView.text = "評価中。。。。"
                val result = openAI.chatCompletion(request)
                val content = result.choices.firstOrNull()?.message?.content
                withContext(Dispatchers.Main) {
                    val bitmap = (imageView.drawable as BitmapDrawable).bitmap
                    if (content != null) {
                        saveResult(bitmap, content)
                    }
                    resultTextView.text = content ?: "結果がありません"

                    findViewById<Button>(R.id.uploadButton).visibility = View.GONE
                    findViewById<Button>(R.id.cameraButton).visibility = View.GONE
                    deleteButton.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    resultTextView.text = "エラーが発生しました: ${e.message}"
                }
            }
        }
    }

    private fun clearCurrentImageAndText() {
        imageView.setImageDrawable(null)
        resultTextView.text = ""
    }
}
