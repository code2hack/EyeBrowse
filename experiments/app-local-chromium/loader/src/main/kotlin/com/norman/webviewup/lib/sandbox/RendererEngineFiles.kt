package com.norman.webviewup.lib.sandbox

import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.os.Parcelable
import android.os.Process
import java.io.File

/** Capability for these pinned code files only; the renderer never supplies a filesystem path. */
internal class RendererEngineFiles(archive: String, libraries: String) : Binder() {
    private val files = listOf("engine.apk" to File(archive)) +
        listOf("libwebviewchromium.so", "libcrashpad_handler_trampoline.so").map {
            it to File(libraries, "arm64-v8a/$it")
        }

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        if (code != 1) return super.onTransact(code, data, reply, flags)
        data.enforceInterface(DESCRIPTOR)
        val uid = getCallingUid()
        check(uid / 100000 == Process.myUid() / 100000 && uid % 100000 in 90000..99999) {
            "Engine files are available only to the assigned isolated child"
        }
        reply!!.writeNoException()
        reply.writeInt(files.size)
        files.forEach { (name, file) ->
            reply.writeString(name)
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use {
                reply.writeParcelable(it, Parcelable.PARCELABLE_WRITE_RETURN_VALUE)
            }
        }
        return true
    }

    companion object {
        const val EXTRA = "eyebrowse.chromium.engine.files"
        private const val DESCRIPTOR = "eyebrowse.chromium.PinnedEngineFiles"

        @Suppress("DEPRECATION")
        fun receive(broker: IBinder): Map<String, ParcelFileDescriptor> {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            val files = linkedMapOf<String, ParcelFileDescriptor>()
            try {
                data.writeInterfaceToken(DESCRIPTOR)
                check(broker.transact(1, data, reply, 0)) { "Missing engine file broker" }
                reply.readException()
                check(reply.readInt() == 3) { "Unexpected engine file inventory" }
                repeat(3) {
                    val name = checkNotNull(reply.readString())
                    check(name in setOf("engine.apk", "libwebviewchromium.so", "libcrashpad_handler_trampoline.so") && name !in files)
                    files[name] = checkNotNull(reply.readParcelable(ParcelFileDescriptor::class.java.classLoader))
                }
                return files
            } catch (failure: Throwable) {
                files.values.forEach { it.close() }
                throw failure
            } finally { reply.recycle(); data.recycle() }
        }
    }
}
