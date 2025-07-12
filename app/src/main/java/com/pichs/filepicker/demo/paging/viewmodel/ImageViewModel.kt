package com.pichs.filepicker.demo.paging.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.pichs.filepicker.demo.paging.model.ImageItem
import com.pichs.filepicker.demo.paging.repository.ImageRepository
import kotlinx.coroutines.flow.Flow

/**
 * 图片列表 ViewModel
 */
class ImageViewModel : ViewModel() {
    
    private val repository = ImageRepository()
    
    val imagePagingData: Flow<PagingData<ImageItem>> = repository.getImageStream()
        .cachedIn(viewModelScope)
}
