package com.luopan.compass.diagnostics

import java.io.File

/**
 * Abstraction over the file write operation for [SensorCapabilityLogger].
 *
 * The production implementation writes [content] to [file] using [file.writeText].
 * The test stub throws [java.io.IOException] to simulate write failure.
 */
interface SensorFileWriter {
    /**
     * Writes [content] to [file], overwriting if it exists.
     * @throws java.io.IOException if the write fails.
     */
    fun write(file: File, content: String)
}

/** Production implementation. */
class RealSensorFileWriter : SensorFileWriter {
    override fun write(file: File, content: String) {
        file.writeText(content)
    }
}
