package com.madus.mobile.domain

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 防止「编译通过、真机 NoSuchMethodError」的 JDK 21 API 泄漏。
 *
 * 背景：1.22.0 在 `repeatProfiles` 里用了 `MutableList.addFirst(...)`。它实际解析到的是
 * Java 21 的 `SequencedCollection` 接口方法，Android/ART 没有这个接口，于是真机上抛
 * `NoSuchMethodError: No interface method addFirst(Ljava/lang/Object;)V in class Ljava/util/List;`。
 *
 * 这类问题**单测抓不到**：单元测试与编译都跑在同机的 JDK 21 上，方法存在。
 * 所以这里直接扫源码，把已知的 Java 21 集合 API 当成硬错误。
 *
 * 允许的例外：接收者是 `java.util.ArrayDeque` / `LinkedList` / `Deque` 时这些方法自 Java 6 就有。
 */
class Jdk21ApiGuardTest {
    private val banned = listOf("addFirst", "addLast", "removeFirst", "removeLast", "getFirst", "getLast")

    /** 接收者本身是 Deque 实现时合法（这些方法在 Java 6 就存在）。 */
    private val allowedFiles = setOf("AiChatViewModel.kt")

    @Test fun mainSourcesMustNotUseJava21SequencedCollectionMethods() {
        val root = mainSourceRoot()
        assertTrue("找不到 Android 源码目录: ${root.absolutePath}", root.isDirectory)

        val offences = mutableListOf<String>()
        root.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            if (file.name in allowedFiles) return@forEach
            file.readLines().forEachIndexed { index, line ->
                val code = line.substringBefore("//")
                banned.forEach { method ->
                    if (code.contains(".$method(")) {
                        offences += "${file.name}:${index + 1}  $method  ->  ${line.trim().take(100)}"
                    }
                }
            }
        }
        assertEquals(
            "这些调用在 Android 上会 NoSuchMethodError（编译/单测在 JDK 21 上都能过）：\n" +
                offences.joinToString("\n"),
            emptyList<String>(),
            offences,
        )
    }

    private fun mainSourceRoot(): File {
        // 单测工作目录是 app/，但也兼容从仓库根运行。
        val candidates = listOf(
            File("src/main/java/com/madus/mobile"),
            File("app/src/main/java/com/madus/mobile"),
        )
        return candidates.firstOrNull { it.isDirectory } ?: candidates.first()
    }
}
