/*
 * Copyright (C) 2025 aisleron.com
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.aisleron.ui.photos

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import androidx.core.os.bundleOf
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aisleron.R
import com.aisleron.databinding.FragmentPhotosBinding
import com.aisleron.domain.record.Record
import com.aisleron.domain.record.RecordRepository
import com.aisleron.domain.product.ProductRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import android.content.ClipboardManager
import android.content.ClipData
import android.content.Context
import com.google.android.material.button.MaterialButton
import android.widget.EditText
import android.widget.TextView
import android.util.Base64
import com.aisleron.data.receipt.ReceiptRemoteParser
import com.aisleron.ui.receipt.ReceiptPreviewDialog


// Photo upload and recognize text from image

class PhotosFragment : Fragment() {
    private val recordRepository: RecordRepository by inject()
    private val productRepository: ProductRepository by inject()

    private var _binding: FragmentPhotosBinding? = null
    private val binding get() = _binding!!


    private lateinit var photosRecycler: RecyclerView
    private val photosAdapter = PhotoAdapter (
        { photoPath ->
            deletePhoto(photoPath)
        },
        onScanTextClick = { photoPath ->
            scanTextFromPhoto(photoPath)
        }
    )


    // Activity result launchers
    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.extras?.get("data")?.let { photo ->
                if (photo is Bitmap) {
                    savePhotoFromBitmap(photo)
                }
            }
        }
    }

    private val selectImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                savePhotoFromUri(uri)
            }
        }
    }

    // Permission launchers
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            takePicture()
        } else {
            Toast.makeText(requireContext(), "Camera permission is required to take photos", Toast.LENGTH_SHORT).show()
        }
    }

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            selectImageFromGallery()
        } else {
            Toast.makeText(requireContext(), "Storage permission is required to access photos", Toast.LENGTH_SHORT).show()
        }
    }


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPhotosBinding.inflate(inflater, container, false)
        setWindowInsetListeners(this, binding.root, false, null)
        return binding.root
    }

    private fun setWindowInsetListeners(
        fragment: PhotosFragment,
        view: LinearLayout,
        fabPadding: Boolean,
        baseMarginId: Int?
    ) {
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerViews()
        setupClickListeners()
        loadPhotos()
    }

    private fun setupRecyclerViews() {


        // Photos RecyclerView
        photosRecycler = binding.recyclerPhotos
        photosRecycler.layoutManager = GridLayoutManager(requireContext(), 2)
        photosRecycler.adapter = photosAdapter
    }

    private fun setupClickListeners() {
        binding.btnTakePhoto.setOnClickListener {
            checkCameraPermissionAndTakePhoto()
        }

        binding.btnSelectFromGallery.setOnClickListener {
            checkStoragePermissionAndSelectImage()
        }
    }



    private fun loadPhotos() {
        viewLifecycleOwner.lifecycleScope.launch {
            val photos = getPhotosFromStorage()
            photosAdapter.submitList(photos)
        }
    }

    private fun checkCameraPermissionAndTakePhoto() {
        when {
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> {
                takePicture()
            }
            ActivityCompat.shouldShowRequestPermissionRationale(requireActivity(), Manifest.permission.CAMERA) -> {
                Toast.makeText(requireContext(), "Camera permission is needed to take photos", Toast.LENGTH_SHORT).show()
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
            else -> {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun checkStoragePermissionAndSelectImage() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        when {
            ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED -> {
                selectImageFromGallery()
            }
            ActivityCompat.shouldShowRequestPermissionRationale(requireActivity(), permission) -> {
                Toast.makeText(requireContext(), "Storage permission is needed to access photos", Toast.LENGTH_SHORT).show()
                storagePermissionLauncher.launch(permission)
            }
            else -> {
                storagePermissionLauncher.launch(permission)
            }
        }
    }

    private fun takePicture() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        intent.resolveActivity(requireContext().packageManager)?.let {
            // Don't specify EXTRA_OUTPUT - this will return the photo as a thumbnail in the result
            takePictureLauncher.launch(intent)
        }
    }

    private fun selectImageFromGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        selectImageLauncher.launch(intent)
    }


    private fun savePhotoFromBitmap(bitmap: Bitmap) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val fileName = "AISLERON_${timeStamp}.jpg"

                val photosDir = File(requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES), "AisleronPhotos")
                if (!photosDir.exists()) {
                    photosDir.mkdirs()
                }

                val file = File(photosDir, fileName)
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Photo captured successfully", Toast.LENGTH_SHORT).show()
                    loadPhotos()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Error saving photo: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun savePhotoFromUri(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val inputStream: InputStream? = requireContext().contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val fileName = "AISLERON_GALLERY_${timeStamp}.jpg"

                val photosDir = File(requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES), "AisleronPhotos")
                if (!photosDir.exists()) {
                    photosDir.mkdirs()
                }

                val file = File(photosDir, fileName)
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }

//                performOcrOnPhoto(bitmap, file.absolutePath)

                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Photo imported successfully", Toast.LENGTH_SHORT).show()
                    loadPhotos()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Error importing photo: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun getPhotosFromStorage(): List<String> {
        val photosDir = File(requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES), "AisleronPhotos")
        if (!photosDir.exists()) {
            return emptyList()
        }

        return photosDir.listFiles()?.filter { file ->
            file.isFile && (file.name.endsWith(".jpg") || file.name.endsWith(".jpeg") || file.name.endsWith(".png"))
        }?.map { it.absolutePath }?.sortedByDescending { File(it).lastModified() } ?: emptyList()
    }

    private fun deletePhoto(photoPath: String) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val file = File(photoPath)
                if (file.exists() && file.delete()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Photo deleted", Toast.LENGTH_SHORT).show()
                        loadPhotos()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Error deleting photo", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Error deleting photo: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun scanTextFromPhoto(photoPath: String) {
        val bitmap = BitmapFactory.decodeFile(photoPath)
        if (bitmap == null) {
            Toast.makeText(requireContext(), "无法加载图片进行解析", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            try {
                sendBitmapToRemoteParserAndShowPreview(bitmap, photoPath)
            } catch (e: Exception) {
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    Toast.makeText(requireContext(), "远端解析失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun performOcrOnPhoto(bitmap: Bitmap, filePath: String) {
        lifecycleScope.launch {
            try {
                sendBitmapToRemoteParserAndShowPreview(bitmap, filePath)
            } catch (e: Exception) {
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    Toast.makeText(requireContext(), "远端解析失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showOcrResult(recognizedText: String, filePath: String) {
        // 重定向：如果已有图片路径，使用远端解析流程（避免使用本地 ReceiptParser）
        scanTextFromPhoto(filePath)
    }

    private fun updateSelectionInfo(
        editText: EditText,
        selectionInfo: TextView,
        copySelectedButton: MaterialButton
    ) {
        val selectedText = getSelectedText(editText)
        if (selectedText.isNotEmpty()) {
            val charCount = selectedText.length
            val wordCount = selectedText.split("\\s+".toRegex()).count { it.isNotEmpty() }
            selectionInfo.text = "Selected: $charCount characters, $wordCount words"
            copySelectedButton.isEnabled = true
        } else {
            selectionInfo.text = "No text selected"
            copySelectedButton.isEnabled = false
        }
    }

    private fun getSelectedText(editText: EditText): String {
        val start = editText.selectionStart
        val end = editText.selectionEnd
        return if (start >= 0 && end >= 0 && start != end) {
            editText.text.substring(start, end)
        } else {
            ""
        }
    }

    private fun copyTextToClipboard(text: String) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("OCR Text", text)
        clipboard.setPrimaryClip(clip)
    }

    private fun saveOcrResultToFile(text: String, imagePath: String) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val imageFile = File(imagePath)
                val textFileName = imageFile.nameWithoutExtension + "_ocr.txt"
                val textFile = File(imageFile.parent, textFileName)

                textFile.writeText(text)

                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "OCR result saved to: ${textFile.name}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Error saving OCR result: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun sendBitmapToRemoteParserAndShowPreview(bitmap: Bitmap, filePath: String) {
        // lifecycleScope 在文件顶部应该已导入
        lifecycleScope.launch {
            try {
                // bitmap -> base64 (JPEG, quality 可调整)
                val baos = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos)
                val base64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)

                // 调用远端解析 API（替换为真实 URL）
                // 使用 ReceiptRemoteParser 的单参数签名（内部会获取 token 并调用百度 API）
                val items = ReceiptRemoteParser.parseImageBase64(base64)

                // 展示到现有的预览对话框（保持现有导入流程）
                val dialog = ReceiptPreviewDialog(
                    initialItems = items,
                    onCancelImport = { /* 可保持原有行为 */ },
                    onConfirmImport = { confirmedItems ->
                        // 将用户确认的 items 传回主流程：这里调用现有的导入逻辑
                        // 假设原来有个函数 handleConfirmedReceiptItems(...)
                        handleConfirmedReceiptItems(confirmedItems)
                    }
                )
                dialog.show(parentFragmentManager, "receipt_preview")
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "远程解析失败: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun sendBitmapToBaiduAndShowPreview(bitmap: Bitmap, filePath: String) {
        lifecycleScope.launch {
            try {
                // bitmap -> base64
                val baos = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos)
                val base64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)

                // 调用百度 API（自动获取 token）
                val items = ReceiptRemoteParser.parseImageBase64(base64)

                if (items.isEmpty()) {
                    Toast.makeText(requireContext(), "未识别到商品信息", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // 展示预览对话框
                val dialog = ReceiptPreviewDialog(
                    initialItems = items,
                    onCancelImport = {
                        Toast.makeText(requireContext(), "已取消导入", Toast.LENGTH_SHORT).show()
                    },
                    onConfirmImport = { confirmedItems ->
                        navigateToReceiptPreview(confirmedItems)
                    }
                )
                dialog.show(parentFragmentManager, "receipt_preview")
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "识别失败: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // 新增：处理确认后的 items，导航到 ReceiptPreviewFragment（复用现有 ReceiptPreviewBundle）
    private fun handleConfirmedReceiptItems(items: List<com.aisleron.domain.receipt.ReceiptItem>) {
        try {
            val receiptPreviewBundle = com.aisleron.ui.bundles.ReceiptPreviewBundle.fromReceiptItems(items)
            val bundle = Bundle().apply {
                putParcelable("receiptPreview", receiptPreviewBundle)
            }
            findNavController().navigate(R.id.nav_receipt_preview, bundle)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Navigation failed: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    // 将 navigateToReceiptPreview 简单实现为调用 handleConfirmedReceiptItems
    private fun navigateToReceiptPreview(items: List<com.aisleron.domain.receipt.ReceiptItem>) {
        handleConfirmedReceiptItems(items)
    }
}