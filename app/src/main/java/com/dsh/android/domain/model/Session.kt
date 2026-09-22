package com.dsh.android.domain.model

data class Session(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val model: String? = null,
    val cwd: String = "",
    val running: Boolean = false,
    val isSubagent: Boolean = false,
    val subagentLabel: String? = null,
    val subagentMode: String? = null,
    val turnCount: Int = 0
) {
    /** Extract short workspace name from full cwd path */
    val workspaceName: String
        get() {
            if (cwd.isBlank()) return "未知工作区"
            // /home/jingyx/project/dsh-android → dsh-android
            // /home/jingyx → ~
            val parts = cwd.split("/")
            return when {
                parts.size <= 3 -> "~"  // /home/jingyx
                parts[2] == "project" && parts.size > 4 -> parts[4] // project name
                else -> parts.last()
            }
        }

    /** Extract workspace category (project folder) */
    val workspaceCategory: String
        get() {
            if (cwd.isBlank()) return "未知"
            val parts = cwd.split("/")
            return when {
                parts.size <= 3 -> "Home"
                parts[2] == "project" && parts.size > 4 -> "project"
                else -> parts[2] // first-level folder
            }
        }
}
