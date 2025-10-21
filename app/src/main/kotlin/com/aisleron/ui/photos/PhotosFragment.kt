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
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val bitmap = BitmapFactory.decodeFile(photoPath)
                if (bitmap != null) {
                    performOcrOnPhoto(bitmap, photoPath)
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Failed to load image", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Error scanning text: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun performOcrOnPhoto(bitmap: Bitmap, filePath: String) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val ocrResult = recognizeTextFromBitmap(bitmap, useChineseRecognizer = false)

                withContext(Dispatchers.Main) {
                    if (ocrResult.isNotBlank()) {
                        showOcrResult(ocrResult, filePath)
                    } else {
                        Toast.makeText(requireContext(), "No text detected in image", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "OCR failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private suspend fun recognizeTextFromBitmap(
        bitmap: Bitmap,
        useChineseRecognizer: Boolean = true
    ): String = suspendCancellableCoroutine { continuation ->
        val image = InputImage.fromBitmap(bitmap, 0)

        val recognizer = if (useChineseRecognizer) {
            TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        } else {
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        }

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val recognizedText = visionText.text
                continuation.resume(recognizedText)
            }
            .addOnFailureListener { exception ->
                continuation.resumeWithException(exception)
            }

        continuation.invokeOnCancellation {
            recognizer.close()
        }
    }


    private fun showOcrResult(recognizedText: String, filePath: String) {
        // 创建自定义对话框
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_ocr_result, null)
        val etOcrText = dialogView.findViewById<EditText>(R.id.et_ocr_text)
        val tvSelectionInfo = dialogView.findViewById<TextView>(R.id.tv_selection_info)
        val btnCopyAll = dialogView.findViewById<MaterialButton>(R.id.btn_copy_all)
        val btnCopySelected = dialogView.findViewById<MaterialButton>(R.id.btn_copy_selected)
        val btnClose = dialogView.findViewById<MaterialButton>(R.id.btn_close)

        // 设置OCR文本
        etOcrText.setText(recognizedText)

        // 默认情况下禁用复制选中按钮
        btnCopySelected.isEnabled = false

        // 监听文本选择变化
        etOcrText.setOnClickListener {
            updateSelectionInfo(etOcrText, tvSelectionInfo, btnCopySelected)
        }

        etOcrText.setOnLongClickListener {
            updateSelectionInfo(etOcrText, tvSelectionInfo, btnCopySelected)
            false
        }

        // 创建对话框
        val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(true)
            .create()

        // 设置窗口背景
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // 按钮点击事件
        btnCopyAll.setOnClickListener {
            copyTextToClipboard(recognizedText)
            Toast.makeText(requireContext(), "All text copied to clipboard", Toast.LENGTH_SHORT).show()
        }

        btnCopySelected.setOnClickListener {
            val selectedText = getSelectedText(etOcrText)
            if (selectedText.isNotEmpty()) {
                copyTextToClipboard(selectedText)
                Toast.makeText(requireContext(), "Selected text copied to clipboard", Toast.LENGTH_SHORT).show()
            }
        }

        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        // 显示对话框
        dialog.show()

        // 设置对话框大小
        val displayMetrics = resources.displayMetrics
        val width = (displayMetrics.widthPixels * 0.9).toInt()
        val height = (displayMetrics.heightPixels * 0.8).toInt()
        dialog.window?.setLayout(width, height)
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
}