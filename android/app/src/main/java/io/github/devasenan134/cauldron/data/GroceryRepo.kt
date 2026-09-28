package io.github.devasenan134.cauldron.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.io.IOException

/**
 * The grocery list, usable without signal (in a store's basement, say).
 *
 * Changes apply to the local copy at once and are queued; [sync] sends the queue in order and
 * then fetches the server's list. Items added offline get a negative id until the server
 * assigns a real one. Both the list and the queue are saved to files, so they survive the app
 * being closed.
 */
class GroceryRepo(private val api: Api, dir: File) {
    @Serializable
    sealed interface Op {
        @Serializable data class Check(val id: Int, val checked: Boolean) : Op
        @Serializable data class Add(val tempId: Int, val name: String, val amount: String) : Op
        @Serializable data class Remove(val id: Int) : Op
        @Serializable data object ClearChecked : Op
    }

    private val listFile = File(dir, "grocery.json")
    private val queueFile = File(dir, "grocery-pending.json")
    // Two locks: state changes are instant and never wait for the network; syncs run one at a time.
    private val stateLock = Mutex()
    private val syncLock = Mutex()

    private val _items = MutableStateFlow(read<List<GroceryItem>>(listFile) ?: emptyList())
    val items: StateFlow<List<GroceryItem>> = _items
    private var queue: List<Op> = read<List<Op>>(queueFile) ?: emptyList()
    private val _pending = MutableStateFlow(queue.size)
    /** Changes not yet on the server. */
    val pending: StateFlow<Int> = _pending
    private val _online = MutableStateFlow(true)
    /** Whether the last attempt to reach the server worked. */
    val online: StateFlow<Boolean> = _online
    /** Server ids for items added offline (temp id -> real id), for changes made before the list reloads. */
    private val realIds = mutableMapOf<Int, Int>()

    suspend fun check(item: GroceryItem) = change(Op.Check(item.id, !item.checked)) { list ->
        list.map { if (it.id == item.id) it.copy(checked = !it.checked) else it }
    }

    suspend fun add(name: String, amount: String) {
        val tempId = -(System.currentTimeMillis() % 1_000_000_000).toInt()
        change(Op.Add(tempId, name.trim(), amount.trim())) { it + GroceryItem(tempId, name.trim(), amount.trim(), manual = true) }
    }

    suspend fun remove(item: GroceryItem) = change(Op.Remove(item.id)) { list -> list.filter { it.id != item.id } }

    suspend fun clearChecked() = change(Op.ClearChecked) { list -> list.filter { !it.checked } }

    /** The planner rebuilt the list on the server: take it (unless changes are still queued; sync sorts those out). */
    suspend fun replace(fresh: List<GroceryItem>) {
        stateLock.withLock { if (queue.isEmpty()) save(fresh) }
        runCatching { sync() }
    }

    /** Send queued changes, then load the server's list. Throws IOException when offline. */
    suspend fun sync() = syncLock.withLock {
        try {
            while (true) {
                val op = stateLock.withLock { queue.firstOrNull() }
                if (op == null) {
                    val fresh = api.grocery()
                    // Only take it if nothing was changed meanwhile; otherwise send that first.
                    val done = stateLock.withLock { queue.isEmpty().also { if (it) { save(fresh); realIds.clear() } } }
                    if (done) break else continue
                }
                send(op)
                stateLock.withLock {
                    queue = queue.drop(1).map(::remap)
                    write(queueFile, queue)
                    _pending.value = queue.size
                }
            }
            _online.value = true
        } catch (e: IOException) {
            if (e !is ApiException) _online.value = false
            throw e
        }
    }

    private suspend fun send(op: Op) {
        try {
            when (op) {
                is Op.Check -> op.id.takeIf { it > 0 }?.let { api.updateGrocery(it, buildJsonObject { put("checked", op.checked) }) }
                is Op.Add -> {
                    val id = api.addGrocery(op.name, op.amount).id
                    stateLock.withLock {
                        realIds[op.tempId] = id
                        _items.value = _items.value.map { if (it.id == op.tempId) it.copy(id = id) else it }
                    }
                }
                is Op.Remove -> op.id.takeIf { it > 0 }?.let { api.deleteGrocery(it) }
                Op.ClearChecked -> api.clearChecked()
            }
        } catch (e: ApiException) {
            // The item is gone on the server (say, the list was cleared on the website): skip it.
            if (e.code != 404) throw e
        }
    }

    /** Point a change made against an offline-added item at its real id. */
    private fun remap(op: Op): Op = when (op) {
        is Op.Check -> realIds[op.id]?.let { op.copy(id = it) } ?: op
        is Op.Remove -> realIds[op.id]?.let { op.copy(id = it) } ?: op
        else -> op
    }

    private suspend fun change(op: Op, apply: (List<GroceryItem>) -> List<GroceryItem>) {
        stateLock.withLock {
            queue = queue + remap(op)
            write(queueFile, queue)
            _pending.value = queue.size
            save(apply(_items.value))
        }
        try { sync() } catch (_: IOException) { /* offline: stays queued */ }
    }

    /** Forget everything (on sign-out). */
    suspend fun clear() = stateLock.withLock {
        queue = emptyList(); _pending.value = 0
        listFile.delete(); queueFile.delete()
        _items.value = emptyList()
    }

    private fun save(list: List<GroceryItem>) {
        _items.value = list
        write(listFile, list)
    }

    private inline fun <reified T> read(file: File): T? =
        runCatching { Api.json.decodeFromString<T>(file.readText()) }.getOrNull()

    private inline fun <reified T> write(file: File, value: T) {
        val tmp = File(file.path + ".tmp")
        tmp.writeText(Api.json.encodeToString(value))
        tmp.renameTo(file)
    }
}
