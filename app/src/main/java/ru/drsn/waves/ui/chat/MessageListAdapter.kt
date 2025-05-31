package ru.drsn.waves.ui.chat

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
            binding.messageTextSent.text = message.content
            binding.timestampTextSent.text = timeFormatter.format(Date(message.timestamp))
            // TODO: Отображение статуса сообщения (галочки)
        }
    }

    class ReceivedMessageViewHolder(
        private val binding: ListItemMessageReceivedBinding,
        private val timeFormatter: SimpleDateFormat,
        private val chatType: ChatType
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(message: DomainMessage) {
            binding.messageTextReceived.text = message.content
            binding.timestampTextReceived.text = timeFormatter.format(Date(message.timestamp))

            if (chatType == ChatType.GROUP) {
                binding.senderNameTextReceived.text = message.senderId // Или более дружелюбное имя, если есть
                binding.senderNameTextReceived.visibility = View.VISIBLE
                binding.avatarImageViewReceived.visibility = View.VISIBLE // Показываем аватар в группе
                // TODO: Загрузка аватара для binding.avatarImageViewReceived
            } else {
                binding.senderNameTextReceived.visibility = View.GONE
                binding.avatarImageViewReceived.visibility = View.GONE // Скрываем аватар в личном чате
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