package com.pichs.filepicker.demo.paging.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.pichs.filepicker.demo.R
import com.pichs.filepicker.demo.paging.model.User

/**
 * 用户列表分页适配器
 */
class UserPagingAdapter : PagingDataAdapter<User, UserPagingAdapter.UserViewHolder>(USER_COMPARATOR) {

    companion object {
        private val USER_COMPARATOR = object : DiffUtil.ItemCallback<User>() {
            override fun areItemsTheSame(oldItem: User, newItem: User): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: User, newItem: User): Boolean {
                return oldItem == newItem
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_user, parent, false)
        return UserViewHolder(view)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        val user = getItem(position)
        if (user != null) {
            holder.bind(user)
        }
    }

    class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val avatarImageView: ImageView = itemView.findViewById(R.id.iv_avatar)
        private val nameTextView: TextView = itemView.findViewById(R.id.tv_name)
        private val emailTextView: TextView = itemView.findViewById(R.id.tv_email)
        private val ageTextView: TextView = itemView.findViewById(R.id.tv_age)
        private val cityTextView: TextView = itemView.findViewById(R.id.tv_city)

        fun bind(user: User) {
            nameTextView.text = user.name
            emailTextView.text = user.email
            ageTextView.text = "年龄: ${user.age}"
            cityTextView.text = "城市: ${user.city}"
            
            // 使用 Glide 加载头像
            Glide.with(itemView.context)
                .load(user.avatar)
                .placeholder(R.drawable.ic_launcher_background)
                .error(R.drawable.ic_launcher_background)
                .circleCrop()
                .into(avatarImageView)
        }
    }
}
