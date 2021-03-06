/*
 * Copyright (c) 2016 Steve Christensen
 *
 * This file is part of Adventuresmith.
 *
 * Adventuresmith is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Adventuresmith is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Adventuresmith.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.stevesea.adventuresmith.core

import com.fasterxml.jackson.databind.ObjectReader
import com.github.salomonbrys.kodein.Kodein
import com.github.salomonbrys.kodein.KodeinAware
import com.github.salomonbrys.kodein.factory
import com.github.salomonbrys.kodein.instance
import com.samskivert.mustache.Mustache
import com.samskivert.mustache.MustacheException
import mu.KLoggable
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.Reader
import java.io.StringReader
import java.util.Locale
import java.util.Stack

interface Generator {
    fun getId() : String
    fun generate(locale: Locale = Locale.ENGLISH, input: Map<String, String>? = null) : String
    fun getMetadata(locale: Locale = Locale.ENGLISH): GeneratorMetaDto
}

interface ContextImporter {
    fun loadContext(name: String, locale: Locale) : Map<String, Any>
}

interface DtoLoadingStrategy<out TDto> {
    fun load(locale: Locale) : TDto
    fun getMetadata(locale: Locale = Locale.ENGLISH): GeneratorMetaDto
}

data class InputParamDto(val name: String,
                         val uiName: String,
                         val numbersOnly: Boolean = false, // hint to restrict input edits to numbers
                         val defaultValue: String = "",
                         val helpText: String = "",
                         val defaultOverridesUserEmpty: Boolean = true, // if user val is blank/null, use the default (ie. don't let user put blank in and confuse things)
                         val nullIfZero: Boolean = false,
                         val isInt: Boolean = false,
                         val maxVal: Int = Int.MAX_VALUE,
                         val minVal: Int = 0,
                         val values: List<String>? = null // valid values
) {
    fun getVal(inputVal: String?) : Any? {
        var result = defaultValue
        if (inputVal.isNullOrBlank()) {
            if (!defaultOverridesUserEmpty) {
                // input is blank/null, and param's defaultVal should _not_ be used
                result = ""
            }
        } else {
            if (values != null) {
                // verify that the input is one of our valid values, set to default if not
                if (values.contains(inputVal)) {
                    result = inputVal.orEmpty()
                } else {
                    result = defaultValue
                }
            }
            result = inputVal.orEmpty()
        }
        if (result.isNullOrEmpty()) {
            return null
        }
        if (isInt) {
            try {
                val retval : Int = result.toInt()
                if (retval == 0 && nullIfZero) {
                    return null
                } else if (retval < minVal) {
                    return minVal
                } else if (retval > maxVal) {
                    return maxVal
                } else {
                    return retval
                }
            } catch (e: Exception) {
                return defaultValue
            }
        } else {
            return result
        }
    }
}
data class GeneratorInputDto(
        val displayTemplate: String,
        val useWizard: Boolean = true,
        val params: List<InputParamDto> = listOf()
) {
    fun mergeInputWithDefaults(input: Map<String, String>?) : Map<String, Any?> {
        val merged : MutableMap<String, Any?> = mutableMapOf()
        params.forEach {
            if (input != null) {
                merged.put(it.name, it.getVal(input.get(it.name)))
            } else {
                merged.put(it.name, it.getVal(""))
            }
        }
        return merged
    }

    fun processInputForDisplay(inputMap: Map<String, String>?) : String {
        try {
            val context = mergeInputWithDefaults(inputMap)
            return Mustache.compiler()
                    .escapeHTML(false)
                    .compile(displayTemplate)
                    .execute(context)
                    .trim()
        } catch (e: Exception) {
            return e.message.orEmpty()
        }
    }
}

data class GeneratorMetaDto(val name: String,
                            val useIconicsTextView : Boolean = false,
                            val input: GeneratorInputDto? = null,
                            val tags: List<String>? = null,
                            val desc: String? = null) {
    fun mergeInputWithDefaults(inputMap: Map<String, String>?) : Map<String, Any?> {
        if (input != null) {
            return input.mergeInputWithDefaults(inputMap)
        }
        else {
            return mutableMapOf()
        }
    }
    fun processInputForDisplay(inputMap: Map<String, String>?) : String {
        if (input != null) {
            return input.processInputForDisplay(inputMap)
        }
        else {
            if (inputMap != null)
                return inputMap.toString()
            else
                return ""
        }
    }
}

// the list of collections
data class CollectionListDto(val collections: List<String>)

// the collection of generators -- in file: <collection-dir>/collection.yml (differs depending on locale)
data class CollectionDto(val id: String,
                         val icon: String,
                         val generators: List<String> = listOf(),
                         val groupedGenerators: Map<String, List<String>> = mapOf()) {
    // retrieve the generators from a specific group
    fun getGeneratorsForGroup(groupId: String? = null) : List<String> {
        if (groupId.isNullOrEmpty()) {
            return generators
        } else {
            return groupedGenerators.getOrElse(groupId ?: "" ) { listOf() }
        }
    }
    // get all the generators in this collection
    fun getAllGeneratorIds() : List<String> {
        return generators + groupedGenerators.values.flatten()
    }
}

data class CollGroupMetaDto(val name: String,
                            val uiName: String,
                            val icon: String,
                            val groups: List<CollGroupMetaDto> = listOf()) {
    fun getIconIndex() : Map<String, String> {
        val result : MutableMap<String, String> = mutableMapOf(name to icon)
        groups.forEach {
            result.putAll(it.getIconIndex())
        }
        return result
    }
}

// the 'metadata' for a collection -- in file: <collection-dir>/meta.yml (differs depending on locale)
data class CollectionMetaDto(val url: String? = null,
                             val name: String,
                             val desc: String? = null,
                             val credit: String? = null,
                             val attribution: String? = null,
                             val groups: List<CollGroupMetaDto>? = null) {

    val iconIndex : Map<String, String> by lazy {
        val result : MutableMap<String, String> = mutableMapOf()
        groups?.forEach { grpMeta ->
            result.putAll(grpMeta.getIconIndex())
        }
        result
    }
    val hasGroups : Boolean by lazy {
        groups?.isNotEmpty() ?: false
    }

    fun toMarkdownStr() : String {
        val sb = StringBuffer("## $name - $credit\n")
        if (url != null)
            sb.append("[$url]($url)\n")
        sb.append("\n")
        sb.append(attribution.orEmpty())
        sb.append("\n")
        sb.append("\n")
        return sb.toString()
    }
    fun toHtmlStr(): String {
        val collname = name
        val sb = StringBuilder()
        sb.append("<h2>$collname")
        if (credit != null) {
            sb.append(" - ${credit.orEmpty()}")
        }
        sb.append("</h2>")

        if (url != null) {
            sb.append("<p>")
            sb.append("<a href=\"$url\">$url</a>")
            sb.append("</p>")
        }
        if (desc != null) {
            sb.append("<p>")
            sb.append(desc)
            sb.append("</p>")
        }
        if (attribution != null) {
            sb.append("<p>")
            sb.append(attribution)
            sb.append("</p>")
        }
        return sb.toString()
    }
}

class CollectionMetaLoader(override val kodein: Kodein) : KodeinAware {
    val resourceDeserializer: CachingResourceDeserializer = instance()
    fun load(collection_path: String, locale: Locale = Locale.US): CollectionMetaDto {
        return resourceDeserializer.deserialize(
                CollectionMetaDto::class.java,
                collection_path + "/" + "meta",
                locale
        )
    }
}

data class DataDrivenGenDto(val templates: RangeMap?,
                            val tables: Map<String, RangeMap>?,
                            val imports: List<String>?,
                            val nested_tables : Map<String, Map<String, RangeMap>>?,
                            val definitions: Map<String, Any>?)

class DataDrivenGenDtoCachingResourceLoader(val resource_prefix: String, override val kodein: Kodein)
: DtoLoadingStrategy<DataDrivenGenDto>, KodeinAware {
    val resourceDeserializer: CachingResourceDeserializer = instance()
    override fun load(locale: Locale): DataDrivenGenDto {
        return resourceDeserializer.deserialize(
                DataDrivenGenDto::class.java,
                resource_prefix,
                locale
        )
    }

    override fun getMetadata(locale: Locale): GeneratorMetaDto {
        return resourceDeserializer.deserialize(
                GeneratorMetaDto::class.java,
                resource_prefix + ".meta",
                locale
        )
    }
}

class DataDrivenGenDtoFileDeserializer(val input: File, override val kodein: Kodein) :
        DtoLoadingStrategy<DataDrivenGenDto>, KodeinAware {
    val objectReader : ObjectReader = instance()

    override fun load(locale: Locale): DataDrivenGenDto {
        val bestFileForLocale = LocaleAwareFinderForFiles.findBestFile(input, locale)
        return bestFileForLocale.bufferedReader().use {
            objectReader.forType(DataDrivenGenDto::class.java).readValue(it)
        }
    }

    override fun getMetadata(locale: Locale): GeneratorMetaDto {
        val bestFileForLocale = LocaleAwareFinderForFiles.findBestFile(File(input.absolutePath.replace(".yml", ".meta.yml")), locale)
        return bestFileForLocale.bufferedReader().use {
            objectReader.forType(GeneratorMetaDto::class.java).readValue(it)
        }
    }
}
class DataDrivenGeneratorForFiles(
        val inputFile: File,
        override val kodein: Kodein) : Generator, KodeinAware, KLoggable {

    override val logger = logger()
    val contextLoader : ContextImporterForFiles = instance()
    val templateProcessor: DataDrivenDtoTemplateProcessor = instance()
    val loaderFactory: (File) -> DataDrivenGenDtoFileDeserializer = factory()
    override fun getId(): String {
        return inputFile.absolutePath
    }
    override fun generate(locale: Locale, input: Map<String, String>?): String {
        try {
            val context = contextLoader.loadContext(getId(), locale)

            context.put("input", getMetadata(locale).mergeInputWithDefaults(input))

            return templateProcessor.processTemplate(context)
        } catch (ex: Exception) {
            throw IOException("problem running generator ${inputFile.name}: ${ex.message}", ex)
        }
    }

    override fun getMetadata(locale: Locale): GeneratorMetaDto {
        try {
            return loaderFactory.invoke(inputFile).getMetadata(locale)
        } catch (ex: FileNotFoundException) {
            logger.debug("unable to locate metadata for ${inputFile.name}: ${ex.message}")
            return GeneratorMetaDto(name = "UNKNOWN");
        }
    }
}

abstract class AbstractContextImporter : ContextImporter {

    abstract fun load(name: String, locale: Locale) : DataDrivenGenDto
    override fun loadContext(name: String, locale: Locale) : MutableMap<String, Any> {

        val result: MutableMap<String, Any> = mutableMapOf()
        val initialDto = load(name, locale)
        val alreadyLoaded = mutableSetOf(name)

        val importStack = Stack<List<String>>()
        importStack.add(initialDto.imports ?: listOf())

        result.mergeCombineInPlace(initialDto.tables ?: mapOf(), { k, a, b -> throw IOException("key conflict: $name.tables.$k")})
        result.mergeCombineInPlace(initialDto.nested_tables ?: mapOf(), { k, a, b -> throw IOException("key conflict: $name.nested_tables.$k")})
        result.mergeCombineInPlace(initialDto.definitions ?: mapOf(), { k, a, b -> throw IOException("key conflict: $name.definitions.$k")})

        while (importStack.isNotEmpty()) {
            val imports = importStack.pop()

            imports.filterNot { alreadyLoaded.contains(it) }
                    .forEach { imp ->
                        val sibling = getImportLoadName(name, imp)

                        val dto = load(sibling, locale)

                        result.mergeCombineInPlace(dto.tables ?: mapOf(), { k, a, b -> throw IOException("key conflict: $imp.tables.$k")})
                        result.mergeCombineInPlace(dto.nested_tables ?: mapOf(), { k, a, b -> throw IOException("key conflict: $imp.nested_tables.$k")})
                        result.mergeCombineInPlace(dto.definitions ?: mapOf(), { k, a, b -> throw IOException("key conflict: $imp.definitions.$k")})

                        alreadyLoaded.add(imp)

                        importStack.add(dto.imports ?: listOf())
                    }
        }
        if (initialDto.templates == null) {
            throw IOException("$name must have 'templates' key")
        } else {
            result.put("templates", initialDto.templates)
        }

        return result
    }

    private fun getImportLoadName(initialName: String, imp: String): String {
        return if (initialName.contains(File.separator))
            initialName.replaceAfterLast(File.separator, imp)
        else
            imp
    }
}

class ContextImporterForResources(
        override val kodein: Kodein) : AbstractContextImporter(), KodeinAware {
    val loaderFactory : (String) -> DataDrivenGenDtoCachingResourceLoader = factory()
    override fun load(name: String, locale: Locale) = loaderFactory.invoke(name).load(locale)
}

class ContextImporterForFiles(
        override val kodein: Kodein) : AbstractContextImporter(), KodeinAware {
    val loaderFactory : (File) -> DataDrivenGenDtoFileDeserializer = factory()
    override fun load(name: String, locale: Locale) = loaderFactory.invoke(File(name)).load(locale)
}

class DataDrivenGeneratorForResources(
        val resource_prefix: String,
        override val kodein: Kodein) : Generator, KodeinAware {

    val contextLoader : ContextImporterForResources = instance()
    val templateProcessor: DataDrivenDtoTemplateProcessor = instance()
    val loaderFactory : (String) -> DataDrivenGenDtoCachingResourceLoader = factory()
    override fun getId(): String {
        return resource_prefix
    }
    override fun generate(locale: Locale, input: Map<String, String>?): String {
        try {
            val context = contextLoader.loadContext(resource_prefix, locale)

            context.put("input", getMetadata(locale).mergeInputWithDefaults(input))

            return templateProcessor.processTemplate(context)
        } catch (ex: Exception) {
            throw IOException("problem running generator $resource_prefix: ${ex.message}", ex)
        }
    }

    override fun getMetadata(locale: Locale): GeneratorMetaDto {
        return loaderFactory.invoke(resource_prefix).getMetadata(locale)
    }
}

class DataDrivenDtoTemplateProcessor(override val kodein: Kodein) : KodeinAware, KLoggable {

    override val logger = logger()

    val shuffler : Shuffler = instance()

    fun processTemplate(context: Map<String, Any>) : String {
        val state : MutableMap<String, Any> = mutableMapOf()
        val templates = context["templates"]
        val template = if (templates is RangeMap)
            shuffler.pick(templates)
        else
            throw IOException("Context missing 'templates' element")

        val compiler = Mustache.compiler()
                .escapeHTML(false)
                .withFormatter(object : Mustache.Formatter {
                    // this method is called after jmustache locates {{key}} in the context.
                    // the context.key is passed to this method, and are given opportunity to
                    // do something special w/ the value
                    override fun format(value: Any?): String {
                        if (value is RangeMap) {
                            return shuffler.pick(value)
                        }
                        return value.toString()
                    }
                })
                .withLoader(object : Mustache.TemplateLoader {
                    // getTemplate is called to evaluate Partials {{>subtmpl}}
                    // in mustache, this typically means loading a different file.
                    // i'm going to abuse it to allow some dynamic template stuff

                    fun findCtxtValNoThrow(findVal: String) : Any? {
                        try {
                            return findCtxtVal(findVal)
                        } catch (ignored: Exception) {
                            return null
                        }
                    }
                    fun findCtxtVal(findVal: String) : Any {
                        val ctxtVal = context[findVal]
                        if (ctxtVal != null)
                            return ctxtVal
                        val stateVal = state[findVal]
                        if (stateVal != null)
                            return stateVal

                        if (findVal.contains(".")) {
                            val pieces = findVal.split(".")
                            val v = context[pieces[0]]
                            if (v is Map<*, *>) {
                                val v2 = v[pieces[1]]
                                if (v2 != null) {
                                    if (v2 is Map<*, *> && pieces.size > 2) {
                                        val v3 = v2[pieces[2]]
                                        if (v3 != null) {
                                            return v3
                                        }
                                    } else {
                                        return v2
                                    }
                                }
                                throw IllegalArgumentException("couldn't find child ${pieces[1]} for $findVal")
                            }
                        }
                        throw IllegalArgumentException("unknown context key: $findVal")
                    }
                    override fun getTemplate(name: String?): Reader {
                        try {
                            if (name == null)
                                return StringReader("null")
                            val cmd_and_params = name.trim().split(" ", limit = 2)
                            if (cmd_and_params[0] == "pickN:") {
                                // {{>pickN: <dice/#/variable> <key> <delim>}}
                                val params = cmd_and_params[1].split(" ", limit = 3)

                                if (!(2..3).contains(params.size)) {
                                    throw IllegalArgumentException("pickN syntax must be: <dice/#/variable> <key> [<delim>]. input: ${cmd_and_params[1]}")
                                }

                                // could be entry in state or context, if neither of those try to roll it
                                val nCtxtVal = findCtxtValNoThrow(params[0])
                                val n = if (nCtxtVal != null) when (nCtxtVal)
                                {
                                    is String -> nCtxtVal.toInt()
                                    else -> nCtxtVal as Int
                                }
                                else if (state.containsKey(params[0])) state[params[0]] as Int
                                else shuffler.roll(params[0])
                                // 2nd param must be which key in context to load
                                val ctxtKey = params[1]
                                val ctxtVal = findCtxtVal(ctxtKey)
                                val results = shuffler.pickN(ctxtVal, n)
                                val delim = if (params.size > 2) {
                                    params[2]
                                } else {
                                    ", "
                                }
                                return StringReader(results.joinToString(delim))
                            } else if (cmd_and_params[0] == "pick:") {
                                // {{>pick: <dice> <key>}}
                                val params = cmd_and_params[1].split(" ", limit = 2)

                                if (!(2..3).contains(params.size)) {
                                    throw IllegalArgumentException("pick syntax must be: <dice/#> <key>. input: ${cmd_and_params[1]}")
                                }
                                //  params[0] must dice
                                val ctxtKey = params[1]
                                val ctxtVal = findCtxtVal(ctxtKey)

                                return StringReader(shuffler.pickD(params[0], ctxtVal))
                            } else if (cmd_and_params[0] == "titleCase:") {
                                // {{>titleCase: <val>}}
                                return StringReader(cmd_and_params[1].titleCase())
                            } else if (cmd_and_params[0] == "roll:") {
                                // {{>roll: <dicestr>}}
                                return StringReader(shuffler.roll(cmd_and_params[1]).toString())
                            } else if (cmd_and_params[0] == "rollN:") {
                                // rolls dicestr N times, returns results as string separated by delim
                                // {{>rollN: <n> <dicestr> <delim>}}
                                val params = cmd_and_params[1].split(" ", limit = 3)
                                if (!(2..3).contains(params.size)) {
                                    throw IllegalArgumentException("rollN syntax must be: <#> <dice> [<delim>]. input: ${cmd_and_params[1]}")
                                }
                                val num = params[0].toInt()
                                val diceStr = params[1]
                                val delim = if (params.size > 2) {
                                    params[2]
                                } else {
                                    ", "
                                }
                                return StringReader(shuffler.rollN(diceStr, num).joinToString(delim))
                            } else if (cmd_and_params[0] == "rollKeepHigh:") {
                                // {{>rollKeepHigh: <rollN> <KeepN> <dicestr>}}
                                val params = cmd_and_params[1].split(" ", limit = 3)
                                if (!(2..3).contains(params.size)) {
                                    throw IllegalArgumentException("rollKeepHigh syntax must be: '<# to roll> <# to Keep> <dice>'. input: ${cmd_and_params[1]}")
                                }
                                val numToRoll = params[0].toInt()
                                val numToKeep = params[1].toInt()
                                val diceStr = params[2]
                                val rolls = shuffler.rollN(diceStr, numToRoll).sortedDescending()
                                val kept = rolls.take(numToKeep)
                                logger.debug("keephigh. rolled: $rolls , kept: $kept")
                                return StringReader(kept.sum().toString())
                            } else if (cmd_and_params[0] == "rollKeepLow:") {
                                // {{>rollKeepLow: <rollN> <KeepN> <dicestr>}}
                                val params = cmd_and_params[1].split(" ", limit = 3)
                                if (!(2..3).contains(params.size)) {
                                    throw IllegalArgumentException("rollKeepLow syntax must be: '<# to roll> <# to Keep> <dice>'. input: ${cmd_and_params[1]}")
                                }
                                val numToRoll = params[0].toInt()
                                val numToKeep = params[1].toInt()
                                val diceStr = params[2]
                                val rolls = shuffler.rollN(diceStr, numToRoll).sortedDescending()
                                val kept = rolls.takeLast(numToKeep)
                                logger.debug("keeplow. rolled: $rolls , kept: $kept")
                                return StringReader(kept.sum().toString())
                            } else if (cmd_and_params[0] == "add:") {
                                // {{>add: <variable> <val>}}
                                val params = cmd_and_params[1].split(" ", limit = 2)
                                val key = params[0]
                                val curVal = state[key]
                                if (curVal == null) {
                                    state.put(key, params[1].toInt())
                                } else if (curVal is Int) {
                                    state.put(key, curVal + params[1].toInt())
                                } else {
                                    throw IllegalArgumentException("cannot 'add:'. value of state.$key is not an integer")
                                }
                                return StringReader("")
                            } else if (cmd_and_params[0] == "set:") {
                                // {{>set: <variable> <val>}}
                                val params = cmd_and_params[1].split(" ", limit = 2)
                                val key = params[0]
                                state.put(key, params[1])
                                return StringReader("")
                            } else if (cmd_and_params[0] == "accum:") {
                                // {{>accum: <list-variable> <val>}}
                                val params = cmd_and_params[1].split(" ", limit = 2)
                                val key = params[0]
                                val curVal = state[key]
                                if (curVal == null) {
                                    state.put(key, listOf(params[1]))
                                } else if (curVal is List<*>) {
                                    state.put(key, curVal + listOf(params[1]))
                                }
                                return StringReader("")
                            } else if (cmd_and_params[0] == "accumRoll:") {
                                // {{>accumRoll: <list-variable> <diceStr>}}
                                val params = cmd_and_params[1].split(" ", limit = 2)
                                val key = params[0]
                                val curVal = state[key]
                                if (curVal == null) {
                                    state.put(key, listOf(shuffler.roll(params[1])))
                                } else if (curVal is List<*>) {
                                    state.put(key, curVal + listOf(shuffler.roll(params[1])))
                                }
                                return StringReader("")
                            } else if (cmd_and_params[0] == "remove:") {
                                // remove given item from list-var
                                // {{>remove: <list-variable> <val>}}
                                val params = cmd_and_params[1].split(" ", limit = 2)
                                val key = params[0]
                                val curVal = state[key]
                                if (curVal != null && curVal is List<*>) {
                                    state.put(key, curVal.filter { it != params[1] })
                                    return StringReader("")
                                }
                                throw IllegalArgumentException("unable to process '>$name'. variable '$key' is not a list")
                            } else if (cmd_and_params[0] == "sum:") {
                                // {{>sum: <list-variable>}} // sum list
                                val key = cmd_and_params[1]
                                val curVal = findCtxtVal(key) // allow on all context vars, not just state vars
                                if (curVal != null && curVal is List<*>) {
                                    return StringReader(curVal.map { it -> it.toString().toInt() }.sum().toString())
                                }
                                throw IllegalArgumentException("unable to process '>$name'. variable '$key' is not a list")
                            } else if (cmd_and_params[0] == "copy:") {
                                // {{>copy: <stateVarOrig> <stateVarDest>}} // create a copy of state variable orig as dest
                                val params = cmd_and_params[1].split(" ", limit = 2)
                                val key = params[0]
                                val destKey = params[1]
                                val curVal = state[key]
                                if (curVal != null && curVal is List<*>) {
                                    state.put(destKey, curVal.map { it -> it })
                                } else if (curVal != null) {
                                    state.put(destKey, curVal)
                                }
                                return StringReader("")
                            } else if (cmd_and_params[0] == "keepLTE:") {
                                // {{>keepLTE: <list-variable> <N>}} // keep elements in list that are <= N
                                val params = cmd_and_params[1].split(" ", limit = 2)
                                val key = params[0]
                                val n = params[1].toInt()
                                val curVal = state[key]
                                if (curVal != null && curVal is List<*>) {
                                    state.put(key, curVal.filter { it -> it.toString().toInt() <= n })
                                    return StringReader("")
                                }
                                throw IllegalArgumentException("unable to process '>$name'. variable '$key' is not a list")
                            } else if (cmd_and_params[0] == "keepGTE:") {
                                // {{>keepGTE: <list-variable> <N>}} // keep elements in list that are >= N
                                val params = cmd_and_params[1].split(" ", limit = 2)
                                val key = params[0]
                                val n = params[1].toInt()
                                val curVal = state[key]
                                if (curVal != null && curVal is List<*>) {
                                    state.put(key, curVal.filter { it -> it.toString().toInt() >= n })
                                    return StringReader("")
                                }
                                throw IllegalArgumentException("unable to process '>$name'. variable '$key' is not a list")

                            } else if (cmd_and_params[0] == "size:") {
                                // {{>size: <list-variable>}} // return the size of the list
                                val key = cmd_and_params[1]
                                val curVal = state[key]
                                if (curVal != null && curVal is List<*>) {
                                    return StringReader(curVal.size.toString())
                                } else {
                                    return StringReader("0")
                                }
                            } else if (cmd_and_params[0] == "keepFirst:") {
                                // {{>keepFirst: <list-variable> <N>}} // keep only first N elements in list
                                val params = cmd_and_params[1].split(" ", limit = 2)
                                val key = params[0]
                                val n = params[1].toInt()
                                val curVal = state[key]
                                if (curVal != null && curVal is List<*>) {
                                    state.put(key, curVal.take(n))
                                    return StringReader("")
                                }
                                throw IllegalArgumentException("unable to process '>$name'. variable '$key' is not a list")

                            } else if (cmd_and_params[0] == "keepLast:") {
                                // {{>keepLast: <list-variable> <N>}} // keep only last N elements in list
                                val params = cmd_and_params[1].split(" ", limit = 2)
                                val key = params[0]
                                try {
                                    val n = params[1].toInt()
                                    val curVal = state[key]
                                    if (curVal != null && curVal is List<*>) {
                                        state.put(key, curVal.takeLast(n))
                                        return StringReader("")
                                    }
                                    throw IllegalArgumentException("unable to process '>$name'. variable '$key' is not a list")
                                } catch (e: NumberFormatException) {
                                    throw IllegalArgumentException("not an integer: '${params[1]}' in '>$name'", e)
                                }
                            } else if (cmd_and_params[0] == "sort:") {
                                // {{>sort: <list-variable>}} // sorts list according to natural sort and always ascending.
                                val key = cmd_and_params[1]
                                val curVal = state[key]
                                if (curVal != null && curVal is List<*>) {
                                    state.put(key, curVal.sortMixedList())
                                }
                                return StringReader("")
                            } else if (cmd_and_params[0] == "uniq:") {
                                // {{>uniq: <list-variable>}} // ensures only one element of each value in list
                                val key = cmd_and_params[1]
                                val curVal = state[key]
                                if (curVal != null && curVal is List<*>) {
                                    state.put(key, curVal.toHashSet().toList())
                                }
                                return StringReader("")
                            } else if (cmd_and_params[0] == "get:") {
                                // {{>get: <variable> [<delim>]}}
                                val params = cmd_and_params[1].split(" ", limit = 2)
                                val key = params[0]
                                val curVal = state[key]
                                if (curVal == null) {
                                    throw IllegalStateException("key '$key' from command '>$name' is null")
                                } else if (curVal is Collection<*>) {
                                    val delim = if (params.size > 1) {
                                        params[1]
                                    } else {
                                        ", "
                                    }
                                    return StringReader(curVal.joinToString(delim))
                                } else {
                                    return StringReader(curVal.toString())
                                }
                            } else if (cmd_and_params[0] == "repeat:") {
                                // {{>repeat: <#/dicestr> <val>}}

                                val params = cmd_and_params[1].split(" ", limit = 2)
                                // could be entry in state or context, if neither of those try to roll it
                                val nCtxtVal = findCtxtValNoThrow(params[0])
                                val n = if (nCtxtVal != null) when (nCtxtVal)
                                    {
                                    is String -> nCtxtVal.toInt()
                                    else -> nCtxtVal as Int
                                    }
                                else if (state.containsKey(params[0])) state[params[0]] as Int
                                else shuffler.roll(params[0])
                                val torepeat = params[1]
                                return StringReader(torepeat.repeat(n))
                            } else {
                                throw IllegalArgumentException("unknown instruction: '$name'")
                            }
                        } catch (ex: Exception) {
                            throw IOException("couldn't understand partial '$name' - ${ex.message}", ex)
                        }
                    }
                })

        var result = template

        var count = 0
        do {
            try {
                logger.debug("$count : $result")
                result = compiler
                        .compile(result)
                        .execute(context)
                        .trim()
                if (!result.contains("{{")) {
                    // all normal curlies are consumed, see if there are final-curlies.
                    // if so, replace them and re-process
                    if (result.contains("%[[")) {
                        result = result
                                .replace("%[[", "{{")
                                .replace("]]%", "}}")
                    } else {
                        // result didn't contain curlies, or our final-curlies,
                        // so we are done
                        break
                    }
                }
                // don't let a template force us into infinite loop
                if (count > 50)
                    break

                count++
            } catch (ex: MustacheException) {
                throw MustacheException("problem processing: $result - ${ex.message}", ex)
            }
        } while (true)

        return result
    }
}

