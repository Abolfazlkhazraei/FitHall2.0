package com.fithall.app.data

/**
 * On-device storage, kept separately for each account that signs in on this phone, so one
 * person's logs are never shown to another (the on-device equivalent of row-level security).
 */
interface AccountStorage {
    /** The storage for [accountId]; null means this device's storage in builds without accounts. */
    fun open(accountId: String?): AppRepository
    /** Called on sign-out, e.g. so background reminders stop reading the account's data. */
    fun signedOut() = Unit
}

/** Keeps each account's data in memory; for platforms without persistent storage, and tests. */
class InMemoryAccountStorage : AccountStorage {
    private val repositories = mutableMapOf<String?, AppRepository>()
    override fun open(accountId: String?): AppRepository =
        repositories.getOrPut(accountId) { AppRepository(InMemoryKeyValueStore()) }
}
