/*
 * Copyright (c) 2024 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.internal

import kotlinx.cinterop.*
import platform.posix.*
import kotlin.concurrent.*

@OptIn(ExperimentalForeignApi::class)
internal actual class MutexNode {

    @Volatile
    private var isLocked = false
    private val pMutex = nativeHeap.alloc<pthread_mutex_tVar>().apply { pthread_mutex_init(ptr, null) }
    private val pCond = nativeHeap.alloc<pthread_cond_tVar>().apply { pthread_cond_init(ptr, null) }

    actual var next: MutexNode? = null

    actual fun lock() {
        pthread_mutex_lock(pMutex.ptr)
        while (isLocked) { // wait till locked are available
            pthread_cond_wait(pCond.ptr, pMutex.ptr)
        }
        isLocked = true
        pthread_mutex_unlock(pMutex.ptr)
    }

    actual fun unlock() {
        pthread_mutex_lock(pMutex.ptr)
        isLocked = false
        pthread_cond_broadcast(pCond.ptr)
        pthread_mutex_unlock(pMutex.ptr)
    }
}
