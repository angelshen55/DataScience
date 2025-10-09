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


class PhotosFragment : Fragment() {
    private val recordRepository: RecordRepository by inject()
    private val productRepository: ProductRepository by inject()

    private var _binding: FragmentPhotosBinding? = null
    private val binding get() = _binding!!


    private lateinit var photosRecycler: RecyclerView
    private val historyAdapter = SimpleRecordAdapter()
    private val photosAdapter = PhotoAdapter { photoPath ->
        deletePhoto(photoPath)
    }

    private var currentPhotoPath: String? = null

    // Activity result launchers
    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            currentPhotoPath?.let { path ->
                savePhotoToGallery(path)
                loadPhotos()
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
        loadHistory()
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

    private fun loadHistory() {
        viewLifecycleOwner.lifecycleScope.launch {
            val data: List<Record> = recordRepository.getAll()
            historyAdapter.submit(data, productRepository)
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
            val photoFile = createImageFile()
            photoFile?.let { file ->
                val photoURI = FileProvider.getUriForFile(
                    requireContext(),
                    "${requireContext().packageName}.fileprovider",
                    file
                )
                intent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                takePictureLauncher.launch(intent)
            }
        }
    }

    private fun selectImageFromGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        selectImageLauncher.launch(intent)
    }

    private fun createImageFile(): File? {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = File(requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES), "AisleronPhotos")
        if (!storageDir.exists()) {
            storageDir.mkdirs()
        }

        val imageFile = File(storageDir, "AISLERON_${timeStamp}.jpg")
        currentPhotoPath = imageFile.absolutePath
        return imageFile
    }

    private fun savePhotoToGallery(photoPath: String) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val bitmap = BitmapFactory.decodeFile(photoPath)
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
                    Toast.makeText(requireContext(), "Photo saved successfully", Toast.LENGTH_SHORT).show()
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}