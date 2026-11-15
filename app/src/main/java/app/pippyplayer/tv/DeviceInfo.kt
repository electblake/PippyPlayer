package app.pippyplayer.tv

import android.app.ActivityManager
import android.content.Context
import android.media.MediaCodecList
import android.os.Build

internal object DeviceInfo {
    fun report(context: Context): String {
        val memory = ActivityManager.MemoryInfo()
        context.getSystemService(ActivityManager::class.java).getMemoryInfo(memory)
        val runtime = Runtime.getRuntime()
        val version = context.packageManager.getPackageInfo(context.packageName, 0)
        val decoders = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
            .filter { !it.isEncoder && !it.isAlias && it.isHardwareAccelerated }
            .flatMap { codec ->
                codec.supportedTypes.filter { it.startsWith("video/") }.map { type ->
                    val limit = codec.getCapabilitiesForType(type).maxSupportedInstances
                    "${codec.name}\n  $type: reported maximum $limit concurrent instances"
                }
            }.sorted()
        return buildString {
            appendLine("PippyPlayer ${version.versionName} (${version.longVersionCode})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}")
            appendLine("Hardware: ${Build.HARDWARE} / board ${Build.BOARD}")
            appendLine("CPU architectures: ${Build.SUPPORTED_ABIS.joinToString()}")
            appendLine("CPU cores available to the app: ${runtime.availableProcessors()}")
            appendLine("RAM: ${memory.totalMem / 1048576} MiB total, ${memory.availMem / 1048576} MiB available")
            appendLine("App Java heap limit: ${runtime.maxMemory() / 1048576} MiB (excludes decoder/GPU memory)")
            appendLine()
            appendLine("Hardware video decoder entries: ${decoders.size}")
            appendLine(decoders.joinToString("\n\n"))
            appendLine()
            append("Decoder limits are upper-bound hints, not a guaranteed player count. Different codecs may share hardware. " +
                "Resolution, frame rate, memory, and other apps can reduce capacity. Do not add these limits together. " +
                "CPU cores and GPU graphics cores do not determine the number of video decoders; adding threads cannot increase decoder capacity.")
        }
    }
}
