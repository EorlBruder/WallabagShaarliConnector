package de.eorlbruder.wallabag_shaarli_connector.standardnotes

import de.eorlbruder.wallabag_shaarli_connector.core.Connector
import de.eorlbruder.wallabag_shaarli_connector.core.Entry
import de.eorlbruder.wallabag_shaarli_connector.core.Sysconfig
import de.eorlbruder.wallabag_shaarli_connector.core.utils.ResponseUtils
import de.eorlbruder.wallabag_shaarli_connector.wallabag.WallabagConnector
import de.eorlbruder.wallbag_shaarli_connector.standardnotes.EntryDecrypter
import khttp.post
import mu.KLogging
import org.json.JSONArray
import org.json.JSONObject
import java.security.NoSuchAlgorithmException


class StandardnotesConnector(isSource: Boolean) : Connector(isSource) {

    companion object : KLogging()

    val config : Sysconfig = Sysconfig()

    init {
        logger.info("Starting to extract all Entries from Standardnotes")
        val jsonRequestData = JSONObject()
        jsonRequestData.put("limit", 30)
        val headers = HashMap(getAuthHeader())
        headers.put("Content-type", "application/json")
        var response = post(config.STANDARDNOTES_URL + "items/sync",
                headers = headers,
                data = jsonRequestData.toString())
        WallabagConnector.logger.debug("Processing Page with Status Code ${response.statusCode}")
        while (ResponseUtils.isSuccessfulStatusCode(response)) {
            val json = JSONObject(response.text)
            pruneAndExtractEntries(json)
            val cursorToken = json.get("cursor_token")
            if (cursorToken == JSONObject.NULL) {
                break
            }
            jsonRequestData.put("cursor_token", cursorToken as String)
            response = post(config.STANDARDNOTES_URL + "items/sync",
                    headers = headers,
                    data = jsonRequestData.toString())
            WallabagConnector.logger.debug("Processing Page with Status Code ${response.statusCode}")
        }
    }

    fun pruneAndExtractEntries(json: JSONObject) {
        val retrievedItems = json.get("retrieved_items") as JSONArray
        val decryptedNotes = ArrayList<JSONObject>()
        val decryptedTags = HashMap<String, String>()
        retrievedItems.forEach {
            if (it is JSONObject) {
                val contentType = it.get("content_type") as String
                val deleted = it.get("deleted") as Boolean
                if ((contentType == "Note" || contentType == "Tag") && !deleted) {
                    var authHash = it.get("auth_hash")
                    if (authHash !is String) {
                        authHash = ""
                    }
                    val uuid = it.get("uuid") as String
                    val encItemKey = it.get("enc_item_key")
                    var ak = ""
                    var mk = ""
                    if (encItemKey is String) {
                        val decryptedKey = decrypt(encItemKey, uuid = uuid,
                                ak = config.STANDARDNOTES_AUTH_KEY,
                                mk = config.STANDARDNOTES_MASTER_KEY, authHash = authHash)
                        mk = decryptedKey.substring(0, decryptedKey.length / 2)
                        ak = decryptedKey.substring(decryptedKey.length / 2, decryptedKey.length)
                    }
                    val content = it.get("content") as String
                    val decryptedContent = decrypt(content, uuid = uuid, ak = ak, mk = mk,
                            authHash = authHash)
                    if (contentType == "Note") {
                        decryptedNotes.add(JSONObject(decryptedContent))
                    } else {
                        val tagJson = JSONObject(decryptedContent)
                        val tagTitle = tagJson.get("title") as String
                        decryptedTags.put(uuid, tagTitle)
                    }
                }
            }
        }
        decryptedNotes.forEach {
            val title = it.get("title") as String
            val description = it.get("text") as String
            val tags = HashSet<String>()
            tags.add(name)
            val references = it.get("references") as JSONArray
            references.forEach {
                val contentType = (it as JSONObject).get("content_type")
                if (contentType == "Tag") {
                    val tagUuid = it.get("uuid") as String
                    val tag = decryptedTags.get(tagUuid)
                    if (tag != null)
                        tags.add(tag)
                }
            }
            val entry = Entry(title, tags, description = description)
            entries.add(entry)
            logger.debug("Added entry with title ${entry.title}")
        }
    }

    fun decrypt(encryptedEntry: String, authHash: String = "", uuid: String = "", ak: String = "", mk: String = ""): String {
        if (encryptedEntry.substring(0..2) == "002") {
            return EntryDecrypter.decryptV002(encryptedEntry, ak, mk, uuid)
        } else if (encryptedEntry.substring(0..2) == "001") {
            return EntryDecrypter.decryptV001(encryptedEntry, config.STANDARDNOTES_AUTH_KEY,
                    config.STANDARDNOTES_MASTER_KEY, authHash)
        } else if (encryptedEntry.substring(0..2) == "000") {
            return EntryDecrypter.decryptV000(encryptedEntry)
        }
        throw NoSuchAlgorithmException()
    }

    override fun getAccessToken(): String {
        val response = post(config.STANDARDNOTES_URL + "auth/sign_in",
                params = mapOf("email" to config.STANDARDNOTES_EMAIL,
                        "password" to config.STANDARDNOTES_SERVER_KEY))
        val token = JSONObject(response.text).get("token") as String
        return token
    }


    override fun writeEntry(entry: Entry) = throw NotImplementedError("A write isn't implemented for Standardnotes yet")

    override val name: String = "Standardnotes"
}