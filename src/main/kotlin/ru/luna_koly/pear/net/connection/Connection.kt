package ru.luna_koly.pear.net.connection

import ru.luna_koly.pear.util.ByteCache
import ru.luna_koly.pear.net.NetParameters
import ru.luna_koly.pear.net.provide
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel

/**
 * Allows to easily read data from
 * SocketChannel and write to it
 */
class Connection(private val channel: SocketChannel) {
    companion object {
        const val CHUNK_CAPACITY = 48
    }

    var protector: Protector = TrivialProtector()

    /**
     * Sends a package containing parameters byte and
     * part of data described via offset and length
     */
    private fun sendRawChunk(data: ByteArray, offset: Int, length: Int, parameters: Int) {
        val buffer = ByteBuffer.allocate(length + 1)
        buffer.put(parameters.toByte())
        buffer.put(data, offset, length)
        buffer.flip()

        do {
            val bytesWritten = channel.write(buffer)
        } while (bytesWritten > 0)
    }

    /**
     * Sends the whole data. It may be spited into
     * pieces and require other side to reconstruct it
     * via their headerCache
     */
    private fun sendRawBytes(data: ByteArray) {
        var offset = 0

        while (offset < data.size - CHUNK_CAPACITY) {
            sendRawChunk(data, offset,
                CHUNK_CAPACITY,
                NetParameters.HAS_NEXT
            )
            offset += CHUNK_CAPACITY
        }

        sendRawChunk(data, offset, data.size - offset, NetParameters.NOTHING)
    }

    /**
     * Protects data and sends it
     */
    fun sendBytes(data: ByteArray) = sendRawBytes(protector.encrypt(data))

    /**
     * Caches incoming ByteArray data and helps to
     * construct it back into one big ByteArray when
     * no further packages are expected (NetParameters.HAS_NEXT)
     */
    private val cache = ByteCache()

    /**
     * Accepts ByteArray coming from the socket. Due to
     * the fact that data may be passed as several packages,
     * it will automatically be reconstructed via headerCache
     */
    private fun readRawBytes(): ByteArray {
        val buffer = ByteBuffer.allocate(CHUNK_CAPACITY + 1)
        var headerLength = 0
        cache.clear()

        do {
            // if got something
            val bytesRead = channel.read(buffer)

            if (bytesRead <= 0)
                break

            buffer.flip()

            // accept parameters & other data
            val parameters = buffer.get().toInt()

            // put data into a list ByteArray item
            val data = ByteArray(buffer.limit() - 1)
            var index = 0

            while (buffer.hasRemaining()) {
                data[index] = buffer.get()
                index++
            }

            headerLength += data.size
            cache.add(data)
            buffer.clear()

            // if there's something further
        } while (parameters.provide(NetParameters.HAS_NEXT))

        // bring list items together into `header`
        return cache.concat()
    }

    /**
     * Reads protected data
     */
    fun readBytes() = protector.decrypt(readRawBytes())

    /**
     * Alias for string data
     */
    fun sendString(string: String) = sendBytes(string.toByteArray(Charsets.UTF_8))

    /**
     * Alias for string data
     */
    fun readString() = String(readBytes(), Charsets.UTF_8)

    override fun toString() = channel.remoteAddress.toString()
}