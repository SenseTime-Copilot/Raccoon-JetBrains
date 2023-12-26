package com.sensetime.sensecode.jetbrains.raccoon.utils

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.psi.PsiFile
import com.sensetime.sensecode.jetbrains.raccoon.resources.RaccoonResources
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File

private val RaccoonLanguagesJson = Json {
    coerceInputValues = true
    ignoreUnknownKeys = true
}

object RaccoonLanguages {
    @Serializable
    data class Language(
        val type: String? = null,
        val aliases: List<String> = emptyList(),
        val extensions: List<String> = emptyList(),
        val filenames: List<String> = emptyList(),
        val color: String? = null,
        val group: String? = null,
    ) {
        val isValid: Boolean
            get() = !(type.isNullOrBlank() || (extensions.isEmpty() && filenames.isEmpty()))

        val fileType: FileType?
            get() = FileTypeRegistry.getInstance().let { fileTypeRegistry ->
                extensions.firstNotNullOfOrNull { extension ->
                    fileTypeRegistry.getFileTypeByExtension(
                        toExtensionNotIncludingDot(extension)
                    ).takeIfNotUnknownType()
                } ?: filenames.firstNotNullOfOrNull { filename ->
                    fileTypeRegistry.getFileTypeByFileName(filename).takeIfNotUnknownType()
                }
            }

        val primaryExtension: String?
            get() = extensions.firstOrNull()

        fun indexOfFilename(filename: String): Int = filenames.indexOf(filename)
        fun indexOfExtension(extension: String): Int = extensions.indexOfFirst { 0 == it.compareTo(extension, true) }
    }

    private const val DOT = '.'
    private const val LANGUAGES_JSON_PATH = "/languages/languages.json"
    private val nameToLanguageMap: Map<String, Pair<String, Language>>
    private val filenamesToLanguageMap: Map<String, Pair<String, Language>>
    private val extensionToLanguageMap: Map<String, Pair<String, Language>>
    val DEFAULT_FILE_TYPE: FileType by lazy {
        FileTypeRegistry.getInstance().findFileTypeByName("textmate")?.takeIfNotUnknownType() ?: FileTypes.PLAIN_TEXT
    }

    init {
        val tmpNameToLanguageMap: MutableMap<String, Pair<String, Language>> = mutableMapOf()
        val tmpFilenamesToLanguageMap: MutableMap<String, Pair<String, Language>> = mutableMapOf()
        val tmpExtensionToLanguageMap: MutableMap<String, Pair<String, Language>> = mutableMapOf()

        RaccoonLanguagesJson.decodeFromString(
            MapSerializer(String.serializer(), Language.serializer()),
            RaccoonResources.getResourceContent(LANGUAGES_JSON_PATH)!!
        ).filter { (key, value) -> key.isNotBlank() && value.isValid }.forEach { entry ->
            entry.toPair().let { pair ->
                val (mainName, language) = pair

                (listOf(mainName) + language.aliases).map { it.lowercase() }.toSet().forEach { name ->
                    tmpNameToLanguageMap.putUnique(name.lowercase(), pair)
                }

                language.filenames.forEachIndexed { index, filename ->
                    tmpFilenamesToLanguageMap.putIf(
                        filename, pair
                    ) {
                        null == it[filename]?.takeIf { (_, prevLanguage) ->
                            prevLanguage.indexOfFilename(filename) in 0..index
                        }
                    }
                }

                language.extensions.forEachIndexed { index, extension ->
                    require(extension.startsWith('.')) { "Extension $extension must start with ." }
                    val lowercaseExtension = extension.lowercase()

                    tmpExtensionToLanguageMap.putIf(
                        lowercaseExtension, pair
                    ) {
                        null == it[lowercaseExtension]?.takeIf { (_, prevLanguage) ->
                            prevLanguage.indexOfExtension(lowercaseExtension) in 0..index
                        }
                    }
                }
            }
        }

        nameToLanguageMap = tmpNameToLanguageMap.toMap()
        filenamesToLanguageMap = tmpFilenamesToLanguageMap.toMap()
        extensionToLanguageMap = tmpExtensionToLanguageMap.toMap()
    }

    @JvmStatic
    fun getLanguageFromFilename(filename: String): Pair<String, Language>? =
        filenamesToLanguageMap[filename]
            ?: toExtensionIncludingDot(File(filename).extension.lowercase()).letIfNotBlank { lowercaseExtension -> extensionToLanguageMap[lowercaseExtension] }

    @JvmStatic
    fun getMarkdownLanguageFromPsiFile(psiFile: PsiFile?): String = psiFile?.takeIf { !it.isDirectory }?.run {
        getLanguageFromFilename(name)?.first ?: language.id.takeIf { isValidMarkdownLanguage(it) }
        ?: fileType.name.takeIf { isValidMarkdownLanguage(it) }
    } ?: ""

    @JvmStatic
    fun getLanguageFromMarkdownLanguage(markdownLanguage: String): Pair<String, Language>? =
        nameToLanguageMap[markdownLanguage.lowercase()]

    @JvmStatic
    private fun FileType.takeIfNotUnknownType(): FileType? = takeIf { it.name != FileTypes.UNKNOWN.name }

    @JvmStatic
    private fun isValidMarkdownLanguage(name: String): Boolean = null != getLanguageFromMarkdownLanguage(name)

    @JvmStatic
    private fun toExtensionIncludingDot(extension: String): String =
        toExtensionNotIncludingDot(extension).ifNullOrBlankElse("") { DOT.toString() + it }

    @JvmStatic
    private fun toExtensionNotIncludingDot(extension: String): String = extension.trim().trimStart('.')
}

fun RaccoonLanguages.Language?.getFileTypeOrDefault(defaultFileType: FileType = RaccoonLanguages.DEFAULT_FILE_TYPE): FileType =
    (this?.fileType) ?: defaultFileType