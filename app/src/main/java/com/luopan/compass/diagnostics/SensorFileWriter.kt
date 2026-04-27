package com.luopan.compass.diagnostics

import java.io.File

/**
 * Abstraction over the file write operation for [SensorCapabilityLogger].
 *
 * The production implementation writes [content] to [file] using [File.writeText].
 * Test stubs can throw [java.io.IOException] or [SecurityException] to verify the
 * failure contract without touching the real filesystem.
 *
 * [SensorCapabilityLogger] is responsible for computing the target [File] path
 * (Context.getFilesDir() / "sensor_profile.json") and the serialised JSON [content].
 * This interface is responsible only for the write operation.
 *
 * TSPEC §5.4
 */
interface SensorFileWriter {
    /**
     * Writes [content] to [file], overwriting if it exists.
     * @throws java.io.IOException if the write fails.
     * @throws SecurityException if the write is denied by the OS.
     */
    fun write(file: File, content: String)
}

/** Production implementation. Calls [File.writeText] which uses UTF-8 by default. */
class RealSensorFileWriter : SensorFileWriter {
    override fun write(file: File, content: String) {
        file.writeText(content)
    }
}
