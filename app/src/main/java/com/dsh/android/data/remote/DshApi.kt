package com.dsh.android.data.remote

/**
 * Legacy REST API interface — no longer used.
 *
 * DSH communicates via RPC over HTTP POST (see [DshRpcClient]):
 * POST /api/{namespace}/{method}
 *
 * This file is kept as documentation of the original approach.
 */
@Deprecated("Use DshRpcClient instead", replaceWith = ReplaceWith("DshRpcClient"))
interface DshApi
