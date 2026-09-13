package com.kairav.nexum.data.models

/**
 * SyncStatus tracks the synchronization state of a record with the backend server.
 */
enum class SyncStatus {
    /**
     * The record has been created locally but not yet synced to the server.
     */
    PENDING,

    /**
     * The record has been successfully synced to the server.
     */
    SYNCED,

    /**
     * The sync attempt failed.
     */
    FAILED
}
