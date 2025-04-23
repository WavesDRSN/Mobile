package ru.drsn.waves.data

class GroupStore {
    private val groups = mutableMapOf<String, Group>()

    fun getGroup(name: String): Group? = groups[name]

    fun getOrCreateGroup(name: String): Group =
        groups.getOrPut(name) { Group(name) }

    fun addUserToGroup(groupName: String, userName: String) {
        val group = getOrCreateGroup(groupName)
        group.members.add(userName.trim())
    }

    fun addMessage(groupName: String, message: String) {
        groups[groupName]?.messages?.add(message)
    }

    fun getAllGroups(): List<Group> = groups.values.toList()
}