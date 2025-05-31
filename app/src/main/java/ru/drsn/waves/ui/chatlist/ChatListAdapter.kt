package ru.drsn.waves.ui.chatlist

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import ru.drsn.waves.R
import ru.drsn.waves.databinding.ListItemChatBinding
import ru.drsn.waves.domain.model.chat.DomainChatSession // Используем доменную модель
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class ChatListAdapter(
    private val onItemClicked: (DomainChatSession) -> Unit
) : ListAdapter<DomainChatSession, ChatListAdapter.ChatViewHolder>(ChatSessionDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val binding = ListItemChatBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ChatViewHolder(binding, onItemClicked)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ChatViewHolder(
        private val binding: ListItemChatBinding,
        private val onItemClicked: (DomainChatSession) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        // Форматтеры для времени и даты
        private val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())
        private val dateFormatter = SimpleDateFormat("dd MMM", Locale.getDefault()) // Например, "29 мая"
        private val fullDateFormatter = SimpleDateFormat("dd.MM.yy", Locale.getDefault()) // Например, "29.05.24"


        fun bind(session: DomainChatSession) {
            binding.contactNameTextView.text = session.peerName
            binding.lastMessageTextView.text = session.lastMessagePreview ?: ""

            // Форматирование времени последнего сообщения
            if (session.lastMessageTimestamp > 0) {
                binding.timestampTextView.text = formatTimestamp(session.lastMessageTimestamp)
            } else {
                binding.timestampTextView.text = "" // Если нет сообщений или времени
            }

            // Установка аватара (здесь можно добавить логику для загрузки URL, если peerAvatarUrl не null)
            // Пока используем дефолтный
            binding.avatarImageView.setImageResource(R.drawable.ic_default_avatar)


            binding.root.setOnClickListener {
                onItemClicked(session)
            }
        }

        private fun formatTimestamp(timestamp: Long): String {
            val messageDate = Date(timestamp)
            val currentDate = Date()

            val diffInMillis = currentDate.time - messageDate.time
            val diffInDays = TimeUnit.MILLISECONDS.toDays(diffInMillis)

            return when {
                diffInDays == 0L -> { // Сегодня
                    // Проверяем, является ли дата сообщения сегодняшней датой (без учета времени)
                    val calMessage = java.util.Calendar.getInstance().apply { time = messageDate }
                    val calCurrent = java.util.Calendar.getInstance().apply { time = currentDate }
                    if (calMessage.get(java.util.Calendar.YEAR) == calCurrent.get(java.util.Calendar.YEAR) &&
                        calMessage.get(java.util.Calendar.DAY_OF_YEAR) == calCurrent.get(java.util.Calendar.DAY_OF_YEAR)) {
                        timeFormatter.format(messageDate)
                    } else { // Вчера (если diffInDays == 0, но день уже другой из-за полуночи)
                        "Вчера" // Или dateFormatter.format(messageDate)
                    }
                }
                diffInDays == 1L -> "Вчера" // Вчера
                diffInDays < 7L -> dateFormatter.format(messageDate) // День недели или дата (например, "Пн" или "25 мая")
                else -> fullDateFormatter.format(messageDate) // Полная дата для старых сообщений
            }
        }
    }

    class ChatSessionDiffCallback : DiffUtil.ItemCallback<DomainChatSession>() {
        override fun areItemsTheSame(oldItem: DomainChatSession, newItem: DomainChatSession): Boolean {
            return oldItem.sessionId == newItem.sessionId // Сравниваем по уникальному ID сессии
        }

        override fun areContentsTheSame(oldItem: DomainChatSession, newItem: DomainChatSession): Boolean {
            // Сравниваем поля, которые влияют на отображение элемента списка
            return oldItem.peerName == newItem.peerName &&
                    oldItem.lastMessagePreview == newItem.lastMessagePreview &&
                    oldItem.lastMessageTimestamp == newItem.lastMessageTimestamp &&
                    oldItem.unreadMessagesCount == newItem.unreadMessagesCount // и другие важные поля
        }
    }
}