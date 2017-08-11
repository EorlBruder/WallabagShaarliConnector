package de.eorlbruder.bookmarksync.core

import de.eorlbruder.bookmarksync.core.utils.ResponseUtils
import de.eorlbruder.bookmarksync.shaarli.ShaarliConnector

abstract class Connector {

    val entries = ArrayList<Entry>()
    var entriesToSync = ArrayList<Entry>()
    abstract val name: String

    fun write(source: String) {
        ShaarliConnector.logger.info("Writing all retrieved and modified Entries to $name")
        entriesToSync.forEach { writeEntry(it, source) }
    }


    protected fun getAuthHeader(): Map<String, String> = ResponseUtils.getAuthorizationHeaderWithToken(getAccessToken())

    protected abstract fun getAccessToken(): String

    protected abstract fun writeEntry(entry: Entry, source: String)

}