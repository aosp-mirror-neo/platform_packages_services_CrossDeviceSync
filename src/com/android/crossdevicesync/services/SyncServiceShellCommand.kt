/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.crossdevicesync.services

import android.os.ShellCommand
import com.android.crossdevicesync.notification.NotificationHelper
import javax.inject.Inject

/** [ShellCommand] Handler for commands passed to the [SyncService]. */
class SyncServiceShellCommand
@Inject
internal constructor(private val notificationHelper: NotificationHelper) : ShellCommand() {
    override fun onCommand(cmd: String?) =
        when (cmd) {
            null,
            "dump" -> -1
            "reset" -> {
                val type = nextArg
                if (type == "notifications") {
                    notificationHelper.reset()
                    outPrintWriter.println("Reset all Notifications for crossdevicesync")
                    0
                } else {
                    outPrintWriter.println("Unknown command : reset $type")
                    -1
                }
            }
            "help",
            "-h" -> {
                onHelp()
                0
            }
            else -> handleDefaultCommands(cmd)
        }

    override fun onHelp() {
        val pw = outPrintWriter
        pw.println("SyncService commands:")
        pw.println("  help")
        pw.println("      Print this help text.")
        pw.println("  reset notifications")
        pw.println("      Resets all notifications to show them again if needed.")
        pw.println()
    }
}
