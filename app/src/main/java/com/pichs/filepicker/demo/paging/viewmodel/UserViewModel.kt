package com.pichs.filepicker.demo.paging.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.pichs.filepicker.demo.paging.model.User
import com.pichs.filepicker.demo.paging.repository.UserRepository
import kotlinx.coroutines.flow.Flow

/**
 * 用户列表 ViewModel
 */
class UserViewModel : ViewModel() {
    
    private val repository = UserRepository()
    
    val userPagingData: Flow<PagingData<User>> = repository.getUserStream()
        .cachedIn(viewModelScope)
}
