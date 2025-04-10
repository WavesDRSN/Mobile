package ru.drsn.waves.ui.chat


import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import ru.drsn.waves.R
import ru.drsn.waves.data.Message

class ChatAdapter(
    private val currentUserId: String // ID текущего пользователя
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val messages = mutableListOf<Message>()

    // Константы для типов View
    companion object {
        private const val VIEW_TYPE_SENT = 1
        private const val VIEW_TYPE_RECEIVED = 2
    }

    // Добавление списка сообщений (например, при начальной загрузке)
    fun submitList(messageList: List<Message>) {
        messages.clear()
        messages.addAll(messageList)
        notifyDataSetChanged() // В реальном приложении лучше использовать DiffUtil
    }

    // Добавление одного нового сообщения
    fun addMessage(message: Message) {
        messages.add(message)
        // Уведомляем адаптер о добавлении элемента в конкретную позицию
        notifyItemInserted(messages.size - 1)
    }

    override fun getItemViewType(position: Int): Int {
        val message = messages[position]
        return if (message.senderId == currentUserId) {
            VIEW_TYPE_SENT
        } else {
            VIEW_TYPE_RECEIVED
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_SENT -> {
                val view = inflater.inflate(R.layout.item_message_sent, parent, false)
                SentMessageViewHolder(view)
            }
            VIEW_TYPE_RECEIVED -> {
                val view = inflater.inflate(R.layout.item_message_received, parent, false)
                ReceivedMessageViewHolder(view)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        when (holder) {
            is SentMessageViewHolder -> holder.bind(message)
            is ReceivedMessageViewHolder -> holder.bind(message)
        }
    }

    override fun getItemCount(): Int = messages.size

    // ViewHolder для исходящих сообщений
    inner class SentMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageText: TextView = itemView.findViewById(R.id.textViewMessage)
        // private val timestampText: TextView? = itemView.findViewById(R.id.textViewTimestamp) // Если добавили время

        fun bind(message: Message) {
            messageText.text = message.text
            // timestampText?.text = formatTimestamp(message.timestamp) // Отформатировать время
        }
    }

    // ViewHolder для входящих сообщений
    inner class ReceivedMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageText: TextView = itemView.findViewById(R.id.textViewMessage)
        // private val timestampText: TextView? = itemView.findViewById(R.id.textViewTimestamp) // Если добавили время

        fun bind(message: Message) {
            messageText.text = message.text
            // timestampText?.text = formatTimestamp(message.timestamp) // Отформатировать время
        }
    }

    // Пример функции форматирования времени (нужно реализовать)
    /*
    private fun formatTimestamp(timestamp: Long): String {
        // Логика преобразования Long в строку времени (напр. "15:30")
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
    }
    */
}