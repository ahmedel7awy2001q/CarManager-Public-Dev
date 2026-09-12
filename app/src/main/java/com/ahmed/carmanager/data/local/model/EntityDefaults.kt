package com.ahmed.carmanager.data.local.model

import java.util.UUID

fun newEntityId(): String = UUID.randomUUID().toString()
fun nowEpochMillis(): Long = System.currentTimeMillis()
