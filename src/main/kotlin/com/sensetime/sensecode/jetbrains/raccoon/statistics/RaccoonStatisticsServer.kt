package com.sensetime.sensecode.jetbrains.raccoon.statistics

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.sensetime.sensecode.jetbrains.raccoon.topics.RACCOON_STATISTICS_TOPIC
import com.sensetime.sensecode.jetbrains.raccoon.topics.RaccoonStatisticsListener
import com.sensetime.sensecode.jetbrains.raccoon.utils.RaccoonUtils
import com.sensetime.sensecode.jetbrains.raccoon.utils.ifNullOrBlank
import com.sensetime.sensecode.jetbrains.raccoon.utils.takeIfNotBlank
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.*
import kotlin.io.path.Path

object RaccoonStatisticsServer : RaccoonStatisticsListener {
    private enum class UsagesType(val columnIndex: Int) {
        TOOLWINDOW_REQUEST(0),
        TOOLWINDOW_RESPONSE_CODE(1),
        TOOLWINDOW_CODE_ACCEPTED(2),
        INLINE_COMPLETION_REQUEST(4),
        INLINE_COMPLETION_ACCEPTED(5),
    }

    const val IS_ENABLE: Boolean = false
    val rootDir: String = Path(PathManager.getLogPath(), "raccoon", "statistics").toString()
    private val fileCharset: Charset = StandardCharsets.UTF_8
    private val usagesChannel = Channel<Int>(Channel.UNLIMITED)
    private fun getDateString(): String = SimpleDateFormat("yyyy-MM-dd").format(Date())
    private const val HEADER =
        "日期, 用户名, 代码对话次数, 代码对话-代码生成次数, 代码对话-复制次数, 代码对话-收藏次数, 代码对话-今日代码采用率, 代码补全次数, 代码采用次数, 代码补全-今日代码采用率\n"

    fun launchStatisticsTask(coroutineScope: CoroutineScope) {
        if (!IS_ENABLE) {
            return
        }
        File(rootDir).mkdirs()
        val usagesMessageBusConnection = ApplicationManager.getApplication().messageBus.connect().also {
            it.subscribe(RACCOON_STATISTICS_TOPIC, this)
        }
        coroutineScope.launch {
            for (columnIndex in usagesChannel) {
                kotlin.runCatching {
                    appendToColumnIndex(columnIndex)
                }
            }

        }.invokeOnCompletion {
            usagesMessageBusConnection.disconnect()
            usagesChannel.close()
        }
    }

    private fun loadUsageFile(file: File): String? {
        var fr: FileReader? = null
        var br: BufferedReader? = null
        val result: String? = kotlin.runCatching {
            fr = FileReader(file, fileCharset)
            br = BufferedReader(fr!!)
            br?.readLines()?.getOrNull(1)
        }.getOrNull()
        fr?.close()
        br?.close()
        return result
    }

    private fun parseUsagesString(usagesString: String?): Pair<List<Int>, List<Float>> = kotlin.runCatching {
        val expectedSize = 10
        usagesString?.takeIfNotBlank()?.split(',')?.takeIf { it.size == expectedSize }?.map { it.trim() }?.run {
            Pair((subList(2, 6) + subList(7, 9)).map { it.toInt() }, listOf(get(6), get(9)).map { it.toFloat() })
        }!!
    }.getOrDefault(Pair(listOf(0, 0, 0, 0, 0, 0), listOf(0F, 0F)))

    private fun incrementColumnIndex(
        usages: Pair<List<Int>, List<Float>>,
        columnIndex: Int
    ): Pair<List<Int>, List<Float>> {
        val usagesCountList = usages.first.toMutableList()
        val usagesRateList = usages.second.toMutableList()
        usagesCountList[columnIndex] += 1
        usagesCountList[UsagesType.TOOLWINDOW_RESPONSE_CODE.columnIndex].let {
            usagesRateList[0] =
                if (it <= 0) 0F else (usagesCountList[UsagesType.TOOLWINDOW_CODE_ACCEPTED.columnIndex].toFloat() / it.toFloat())
        }
        usagesCountList[UsagesType.INLINE_COMPLETION_REQUEST.columnIndex].let {
            usagesRateList[1] =
                if (it <= 0) 0F else (usagesCountList[UsagesType.INLINE_COMPLETION_ACCEPTED.columnIndex].toFloat() / it.toFloat())
        }
        return Pair(usagesCountList, usagesRateList)
    }

    private fun toUsagesString(date: String, pair: Pair<List<Int>, List<Float>>): String = HEADER +
            (listOf(date, RaccoonUtils.machineID.ifNullOrBlank(RaccoonUtils.DEFAULT_MACHINE_ID)) + pair.first.subList(
                0,
                4
            )
                .map { "$it" } + pair.second[0] + pair.first.subList(4, 6)
                .map { "$it" } + pair.second[1]).joinToString(", ")

    private fun appendToColumnIndex(columnIndex: Int) {
        var fw: FileWriter? = null
        val date: String = requireNotNull(getDateString().takeIfNotBlank())
        val file = File(requireNotNull(rootDir.takeIfNotBlank()), "${date}_usage.csv")
        try {
            val usagesString =
                toUsagesString(date, incrementColumnIndex(parseUsagesString(loadUsageFile(file)), columnIndex))
            File(rootDir).mkdirs()
            fw = FileWriter(file, fileCharset)
            fw.write(usagesString)
        } finally {
            fw?.close()
        }
    }

    override fun onToolWindowRequest() {
        usagesChannel.trySend(UsagesType.TOOLWINDOW_REQUEST.columnIndex)
    }

    override fun onToolWindowResponseCode() {
        usagesChannel.trySend(UsagesType.TOOLWINDOW_RESPONSE_CODE.columnIndex)
    }

    override fun onToolWindowCodeAccepted() {
        usagesChannel.trySend(UsagesType.TOOLWINDOW_CODE_ACCEPTED.columnIndex)
    }

    override fun onInlineCompletionRequest() {
        usagesChannel.trySend(UsagesType.INLINE_COMPLETION_REQUEST.columnIndex)
    }

    override fun onInlineCompletionAccepted() {
        usagesChannel.trySend(UsagesType.INLINE_COMPLETION_ACCEPTED.columnIndex)
    }
}