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


import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.aisleron.R
import com.aisleron.databinding.ItemPhotoBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class PhotoAdapter(
    private val onDeleteClick: (String) -> Unit
) : ListAdapter<String, PhotoAdapter.PhotoViewHolder>(PhotoDiffCallback()) {

    class PhotoViewHolder(private val binding: ItemPhotoBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(photoPath: String, onDeleteClick: (String) -> Unit) {
            // Load and display the photo
            val bitmap = BitmapFactory.decodeFile(photoPath)
            binding.ivPhoto.setImageBitmap(bitmap)

            // Set the date from file modification time
            val file = File(photoPath)
            val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            val dateText = dateFormat.format(Date(file.lastModified()))
            binding.tvPhotoDate.text = dateText

            // Set delete button click listener
            binding.btnDeletePhoto.setOnClickListener {
                onDeleteClick(photoPath)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val binding = ItemPhotoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PhotoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        holder.bind(getItem(position), onDeleteClick)
    }

    class PhotoDiffCallback : DiffUtil.ItemCallback<String>() {
        override fun areItemsTheSame(oldItem: String, newItem: String): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: String, newItem: String): Boolean {
            return oldItem == newItem
        }
    }
}
