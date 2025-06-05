package ru.drsn.waves.ui.chat

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import ru.drsn.waves.databinding.ListItemMessageReceivedBinding
import ru.drsn.waves.databinding.ListItemMessageSentBinding
import ru.drsn.waves.domain.model.chat.DomainMessage
import ru.drsn.waves.domain.model.chat.ChatType // Для определения, показывать ли имя отправителя
import ru.drsn.waves.domain.model.chat.MessageType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MessageListAdapter(
    private val currentUserId: String,
    private val chatType: ChatType
) : ListAdapter<DomainMessage, RecyclerView.ViewHolder>(MessageDiffCallback()) {

    private val VIEW_TYPE_SENT = 1
    private val VIEW_TYPE_RECEIVED = 2

    // Форматтер для времени
    private val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())

    override fun getItemViewType(position: Int): Int {
        val message = getItem(position)
        return if (message.senderId == currentUserId) {
            VIEW_TYPE_SENT
        } else {
            VIEW_TYPE_RECEIVED
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_SENT) {
            SentMessageViewHolder(
                ListItemMessageSentBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                ),
                timeFormatter
            )
        } else {
            ReceivedMessageViewHolder(
                ListItemMessageReceivedBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                ),
                timeFormatter,
                chatType
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = getItem(position)
        if (holder is SentMessageViewHolder) {
            holder.bind(message)
        } else if (holder is ReceivedMessageViewHolder) {
            holder.bind(message)
        }
    }

    class SentMessageViewHolder(
        private val binding: ListItemMessageSentBinding,
        private val timeFormatter: SimpleDateFormat
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(message: DomainMessage) {
            if (message.messageType == MessageType.TEXT) {
                binding.textViewMessage.text = message.content
                binding.textViewMessage.visibility = View.VISIBLE
            }
            else if (message.messageType == MessageType.IMAGE) {
                binding.imageViewMessage.setImageURI(Uri.parse(message.mediaUri))
                binding.imageViewMessage.visibility = View.VISIBLE
            }
            binding.textViewTimestamp.text = timeFormatter.format(Date(message.timestamp))
            binding.textViewTimestamp.visibility = View.VISIBLE
            // TODO: Отображение статуса сообщения (галочки)
        }
    }

    class ReceivedMessageViewHolder(
        private val binding: ListItemMessageReceivedBinding,
        private val timeFormatter: SimpleDateFormat,
        private val chatType: ChatType
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(message: DomainMessage) {
            if (message.messageType == MessageType.TEXT) {
                binding.textViewMessage.text = message.content
                binding.textViewMessage.visibility = View.VISIBLE
            } else if (message.messageType == MessageType.IMAGE && message.mediaUri != null) {

                val imageUri = Uri.parse(message.mediaUri)

                if (imageUri != null) {
                    binding.imageViewMessage.setImageURI(imageUri)
                    binding.imageViewMessage.visibility = View.VISIBLE
                }
            }
            binding.textViewTimestamp.text = timeFormatter.format(Date(message.timestamp))
            binding.textViewTimestamp.visibility = View.VISIBLE

            if (chatType == ChatType.GROUP) {
                // TODO: Загрузка аватара для binding.avatarImageViewReceived
            } else {
            }
        }
    }

    class MessageDiffCallback : DiffUtil.ItemCallback<DomainMessage>() {
        override fun areItemsTheSame(oldItem: DomainMessage, newItem: DomainMessage): Boolean {
            return oldItem.messageId == newItem.messageId
        }

        override fun areContentsTheSame(oldItem: DomainMessage, newItem: DomainMessage): Boolean {
            return oldItem == newItem // Сравнение по equals в DomainMessage
        }
    }
}